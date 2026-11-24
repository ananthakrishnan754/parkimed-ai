# ParkiMed Android App

Offline-first Android client for the adaptive multimodal Parkinson's
medication-state (ON/OFF) + severity framework.

## Requirements
- JDK 17
- Android SDK 35 (minSdk 29 / targetSdk 35)
- Gradle 8.9 (no wrapper committed — use a system Gradle with `ANDROID_HOME` set)
- Kotlin 2.0.21, AGP 8.7.3

## Build

```bash
export ANDROID_HOME=/path/to/sdk
gradle -p ParkiMed-Android :app:assembleDebug
adb install -r ParkiMed-Android/app/build/outputs/apk/debug/app-debug.apk
```

## Model contract (v3)

The shipped asset `app/src/main/assets/parkimed_model.tflite` is the
authoritative v3 fusion model (~28 KB). Inputs are resolved **by tensor
name** at runtime, so ordering never matters:

| Tensor     | Shape      | Presence gate              |
|------------|------------|----------------------------|
| `voice`    | `[1, 39]`  | `\|\|x\|\|2 > 1e-4`          |
| `motion`   | `[1, 36]`  | gate (head hard-zeroed v3) |
| `tap`      | `[1, 20]`  | gate (head hard-zeroed v3) |
| `spiral`   | `[1, 15]`  | `\|\|x\|\|2 > 1e-4`          |
| `visual`   | `[1, 20]`  | `\|\|x\|\|2 > 1e-4`          |
| `gait`     | `[1, 24]`  | `\|\|x\|\|2 > 1e-4`          |

Outputs: `on_off[1,2]` (index 1 = P(ON)) and `severity[1,1]` =
P(PD) x mean(modality severity), displayed x5 on a 0..5 scale.
`preprocessing_config.json` ships alongside for reference; runtime code
hard-codes the contract above.

## Architecture (mirrors the Python backend)

```
Tasks (Voice / Walk / Tap / Spiral)
   |  sensors: AudioRecord, SensorManager, MotionEvent, Canvas view
   v
FeatureExtractor.kt   (39 / 36 / 20 / 15 / 20 / 24 dim vectors — mirrors src/features)
   v
ParkiMedModel.kt      (TFLite interpreter, name-resolved inputs, presence gating)
   v
Prediction(ON/OFF, severity 0..5, confidence)
   v
Room database (offline-first)  ->  SyncWorker  ->  Firebase (optional)  ->  dashboard
```

## Key files

| File | Purpose |
|---|---|
| `model/ParkiMedModel.kt` | TFLite loading + name-based input binding + inference |
| `features/FeatureExtractor.kt` | Feature math mirroring the Python side |
| `sensors/MotionCollector.kt` | Accel + gyro capture, gravity removal, windowing |
| `sensors/AudioCollector.kt` | 16 kHz mono mic recording |
| `sensors/TapCollector.kt` | Tap interval/duration capture |
| `ui/SpiralDrawView.kt` | Spiral/handwriting capture canvas |
| `data/` (Room) + `SyncWorker` | Offline local DB + optional cloud sync |
| `camera/PillCaptureActivity.kt` | Pill-intake camera stub (implementation task) |

Demos: every task screen exposes real sample paths (Voice Healthy/Severe,
Tap, Walk) that run the trained modality heads end-to-end — useful when
live capture is impractical.

## Explicit follow-ups (implementation TODOs)
1. **Pill detector:** wire CameraX `ImageAnalysis` + MediaPipe/TFLite detector
   in `PillCaptureActivity`; log confirmed intake with timestamp to Room.
2. **Firebase (optional):** add `google-services.json`; until then SyncWorker
   no-ops safely and everything stays offline-first.
3. **Motion/tap heads:** v3 hard-zeroes these heads pending labeled mPower
   data (Synapse `syn4993293` token required). Live motion/tap assessments
   therefore sit near 50/50 by design; use demos for trained-head paths.
4. **Voice proxy features:** MFCC is computed on-device via proxy features;
   keep Python-side `FeatureExtractor.voice()` and Kotlin in sync if retraining.

## Permissions
RECORD_AUDIO, CAMERA, INTERNET, POST_NOTIFICATIONS. Granted at runtime in
MainActivity on first run.
