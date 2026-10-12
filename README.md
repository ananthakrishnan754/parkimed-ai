# ParkiMed — Smartphone-Based Parkinson's Medication Monitoring

**Adaptive Multimodal Fusion Framework for Smartphone-Based Medication
Monitoring for Parkinson's Disease** — B.Tech AI & ML final-year project.

An **offline Android app** that records **voice, spiral drawing, finger tapping
and gait** with the phone's sensors, runs a real-data-trained TensorFlow Lite
fusion model on-device, and reports the patient's medication **state (ON/OFF)**
and **severity** in real time — with a local History + severity trend and an
optional Firebase doctor dashboard.

> **Model training uses REAL clinical datasets only** (see [Datasets](#datasets)).
> The only generated artifacts are the demo playback WAVs in `reports/` (used
> solely for live presentations, never for training).

---

## Repository layout

```
parkimed-ai/
├── config.py                     # Global constants/paths for the Python side
├── requirements.txt
├── src/
│   ├── models/
│   │   ├── train_real_uci.py     # v2: real voice classifier + UPDRS severity (UCI)
│   │   ├── train_real_v3.py      # v3: REAL heads — voice, spiral, visual-tap, gait + presence gating
│   │   ├── build_spiral_data.py  # UCI spiral tablets -> normalized 15-dim features
│   │   ├── build_gait_data.py    # REMAP Kinect turns -> 24-dim gait features
│   │   ├── fusion_model.py       # (v1) adaptive-attention reference
│   │   └── baseline_models.py
│   ├── export/export_tflite.py   # Keras/SavedModel -> float16 TFLite
│   ├── data/download_uci.py
│   ├── features/feature_extraction.py
│   └── utils/
├── scripts/run_pipeline.sh
├── data/raw/                     # committed CSVs (real UCI + derived features)
├── models/                       # parkimed_model.tflite (shipped) + GeneratedArtifacts
├── ParkiMed-Android/             # Kotlin app (single module, viewBinding)
│   └── app/src/main/
│       ├── java/com/parkimed/ai/{features,sensors,model,data,ui,tasks}
│       └── assets/parkimed_model.tflite + preprocessing_config.json
├── doctor/app.py                 # Flask dashboard (reads Firestore)
└── reports/                      # Project report (PDF/md), demo WAVs, screenshots
```

---

## Model contract (what the app consumes)

Inputs (float32, `[1, dim]`), resolved **by tensor name** on Android:

| Input   | Dim | Meaning / source |
|---------|-----|------------------|
| `voice` | 39  | slots 0–15 = real MDVP (Fo, Fhi, Flo, Jitter %, RAP/PPQ/DDP, Shimmer, APQ*, NHR, HNR); 16–38 = 0 |
| `motion`| 36  | reserved (no labelled on-device dataset yet → gated off) |
| `tap`   | 20  | reserved (same) |
| `spiral`| 15  | **real** — UCI spiral tablets, unit-normalized |
| `visual`| 20  | **real** — ROC-HCI webcam finger-tap features |
| `gait`  | 24  | **real** — REMAP ON/OFF-medication turning skeletons |

Outputs: `on_off[1,2] = [P(PD), P(ON)]` (index 1 = ON) and `severity[1,1]`
(= `P(PD) × avg(modality severity)`; app scales ×5).

Each modality has a raw-input **presence gate** (L2 norm) so tasks the patient
didn't run are ignored by the fusion.

Real head accuracies (holdout; small datasets so treat as indicative):
voice **0.92–0.95**, spiral **0.90** (subject-level LOGO 0.87), visual-tap
**0.57–0.65**, gait ON/OFF **0.64 holdout / 0.772 leave-subject-out**.

---

## Datasets (all real, free or open)

| Dataset | Source | Used for |
|---------|--------|----------|
| UCI Parkinsons Speech (`uci_speech.csv`, 195: 147 PD/48 healthy) | https://archive.ics.uci.edu/dataset/174 | voice classifier |
| UCI Telemonitoring (`uci_telemonitoring.csv`, 5875, 42 pts) | https://archive.ics.uci.edu/dataset/189 | UPDRS severity regressor |
| UCI Spiral Drawings id 395 | https://archive.ics.uci.edu/dataset/395 | spiral head |
| ROC-HCI finger-tapping (`data/raw/roc_hci_tap/`) | https://github.com/ROC-HCI/finger-tapping-severity | visual-tap head |
| REMAP open gait (Kinect turns, ON/OFF med labels) | https://data.bris.ac.uk/datasets/21h9f9e30v9cl2fapjggz4q1x7/ | gait ON/OFF head |

Committed in-repo: `uci_speech.csv`, `uci_telemonitoring.csv`, `spiral_features.csv`,
`gait_features.csv`, `gait_onoff_features.csv`, ROC-HCI files, and the raw
ARTIFACTS needed by build scripts.

Large raw archives (the 351 MB REMAP zip/extract, UCI spiral `.zip`) are
**gitignored** — re-download with:

```bash
# UCI spiral (needed only if you rebuild spiral_features.csv)
wget -O data/raw/uci_spiral/spiral.zip \
  "https://archive.ics.uci.edu/static/public/395/parkinson+disease+spiral+drawings+using+digitized+graphics+tablet.zip"

# REMAP (needed only if you rebuild gait_features.csv / gait_onoff_features.csv)
cd data/raw/remap && \
wget "https://data.bris.ac.uk/datasets/21h9f9e30v9cl2fapjggz4q1x7/21h9f9e30v9cl2fapjggz4q1x7.zip" \
  && unzip -o -q remap_open.zip -d extracted
```

---

## Train + export the model

```bash
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt

# Optional: rebuild the real-feature datasets (see paths above)
CUDA_VISIBLE_DEVICES="" PYTHONPATH=src python -m models.build_spiral_data
CUDA_VISIBLE_DEVICES="" PYTHONPATH=src python -m models.build_gait_data

# v2 (voice classifier + severity)
CUDA_VISIBLE_DEVICES="" PYTHONPATH=src python -m models.train_real_uci

# v3 (full real multimodal fusion + presence gating) -> writes models/parkimed_model.tflite
CUDA_VISIBLE_DEVICES="" PYTHONPATH=src python -m models.train_real_v3
```

Copy the produced TFLite into the app asset:
```bash
cp models/parkimed_model.tflite ParkiMed-Android/app/src/main/assets/parkimed_model.tflite
```

---

## Build the Android app

Requirements: JDK 17, Android SDK (compileSdk 35), Android Studio or Gradle.

```bash
cd ParkiMed-Android
export ANDROID_HOME=/path/to/Android/Sdk
gradle --no-daemon :app:assembleDebug -x lint
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Notes:
- `androidResources.noCompress += "tflite"` and `buildFeatures.buildConfig = true` are set.
- The TFLite asset ships in-repo so the app builds as-is.
- **Demo buttons** (Voice Healthy/Severe chips, Tap patient-tapping animation,
  Walk PD-episode gait+spiral animation) are `BuildConfig.DEBUG`-gated and replay
  **real patient-derived feature vectors** — built for the project demo.
- Live spiral → real `FeatureExtractor.spiral` → the real spiral head on-device.

### Verification checklist (device)

1. Voice Test — real `AHHH` → healthy = **ON**, low severity.
2. Spiral — tremulous trace → **OFF** (real head).
3. Tap demo (debug) → visible ripples + **OFF · ~3.6/5**.
4. Walk demo (debug) → stiff-gait animation + **OFF · ~4.7/5**.
5. Voice demo chips → Severe **OFF / 2.5**, Healthy **ON**.

---

## Doctor dashboard

```bash
python doctor/app.py   # needs a Firebase service-account (GOOGLE_APPLICATION_CREDENTIALS)
```
Reads Firestore documents written by the app's `SyncWorker` (add
`app/google-services.json` to enable).

---

## Known limitations

- Touch-screen `tap` and accelerometer `motion` heads need a labelled on-device
  dataset (mPower project, Sage Synapse `syn4993293`); until then those inputs
  are gated off. Camera-based visual-tap and gait are already real.
- Voice severity is an estimate from phonation only (UPDRS RMSE ≈ 9.6) — a
  **trend signal**, not a diagnosis.
- Small voice/spiral datasets: cross-validation is the honest estimate.

## References

- UCI Machine Learning Repository: Parkinsons (≈2015 version), Telemonitoring, Spiral Drawings (id 395).
- ROC-HCI: *Using AI to measure Parkinson's disease severity at home* (finger-tapping features).
- Prudente, et al. *REMAP: Real-time EM-based assessment of gait in Parkinson's* (open Kinect turning/sit-to-stand dataset, Bristol).

---

*ParkiMed — Adaptive Multimodal Fusion Framework for Smartphone-Based
Medication Monitoring for Parkinson's Disease.*