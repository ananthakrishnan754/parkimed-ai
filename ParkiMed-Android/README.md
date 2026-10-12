# ParkiMed Android App

Offline-first Android client for the adaptive multimodal fusion framework.

## Requirements
- Android Studio (Ladybug+)
- JDK 17
- minSdk 29 (Android 10+) per the SRS

## Deploying the model

1. Train + export on your machine:
   ```bash
   cd ../parkimed-ai
   ./venv/bin/python -m src.models.train --model fusion
   ./venv/bin/python -m src.export.export_tflite --model models/fusion_best.keras --float16
   ```
2. Copy artifacts into the app:
   ```bash
   cp models/parkimed_model.tflite  ParkiMed-Android/app/src/main/assets/
   # preprocessing_config.json already ships alongside — keep it in sync with config.py
   ```
3. Open `ParkiMed-Android/` in Android Studio and run on a device.

## Architecture (mirrors the Python backend)

```
Tasks (Voice / Walk / Tap / Spiral)
   │  sensors: AudioRecord, SensorManager, MotionEvent, Canvas view
   ▼
FeatureExtractor.kt   (39 / 36 / 20 / 15 dim vectors — identical to src/features)
   ▼
ParkiMedModel.kt      (TFLite interpreter, offline, ~70 KB model)
   ▼
Prediction(ON/OFF, severity, confidence)
   ▼
Room database (offline-first)  →  SyncWorker  →  Firebase  →  Doctor dashboard
```

## Key files

| File | Purpose |
|---|---|
| `model/ParkiMedModel.kt` | TFLite loading + inference |
| `features/FeatureExtractor.kt` | Feature math mirroring the Python side |
| `sensors/MotionCollector.kt` | Accel + gyro capture, gravity removal, windowing |
| `sensors/AudioCollector.kt` | 16 kHz mono mic recording |
| `sensors/TapCollector.kt` | Tap interval/duration capture |
| `ui/SpiralDrawView.kt` | Spiral/ handwriting capture canvas |
| `data/Room` + `SyncWorker` | Offline local DB + online cloud sync |
| `camera/PillCaptureActivity.kt` | Pill-intake camera stub (implementation task) |

## Explicit follow-ups (implementation TODOs)
1. **Voice features:** full MFCC needs an NDK port (e.g. `librosa`/`aubio` C port)
   or retrain the voice expert on the on-device proxy features. Decide before
   final build so Python-side `FeatureExtractor.voice()` and Kotlin match.
2. **Pill detector:** wire CameraX `ImageAnalysis` + MediaPipe/TFLite detector
   in `PillCaptureActivity`; log confirmed intake with timestamp to Room.
3. **Firebase:** replace `SyncWorker.await()` stub with `Tasks.await()` from
   play-services-tasks; add `google-services.json`.
4. **Result screen chart:** MPAndroidChart line chart already added to deps.
5. **Array plumbing:** `WalkTaskActivity`/`VoiceTaskActivity` currently show a
   toast instead of persisting predictions — route results into `LocalStore`
   + `ResultsActivity` like HistoryActivity does.

## Permissions
RECORD_AUDIO, CAMERA, INTERNET, POST_NOTIFICATIONS. All granted at runtime in
MainActivity on first run.