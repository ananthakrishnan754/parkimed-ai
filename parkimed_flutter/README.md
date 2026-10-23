# ParkiMed Flutter — Modern Clinical Frontend

An ultra-modern, user-friendly, light-themed mobile & cross-platform frontend for **ParkiMed** built with **Flutter** and **Cubit State Management (`flutter_bloc`)**.

---

## 🌟 Key Design & Architectural Highlights

1. **Fascinating Light-Themed Clinical Design**:
   - **Palette**: Electric Indigo primary (`#4F46E5`), Soft Sky accent (`#0EA5E9`), Emerald Green (`#10B981`) for `ON State` (optimal control), and Coral Rose (`#F43F5E`) for `OFF State` (fluctuations detected).
   - **Typography**: Clean, readable Google Fonts (`Outfit`) tailored for ease of readability by elderly and Parkinson's patients.
   - **Tactile feedback**: Soft ambient shadows, rounded card surfaces (20–24px border radii), high-contrast touch targets, and animated transitions.

2. **Cubit State Management (`flutter_bloc`)**:
   - **`AssessmentCubit` & `AssessmentState`**:
     - Manages active test lifecycles (Idle, Active, Processing, Completed).
     - Handles real-time countdown timers, interactive touch coordinates, cadence calculation, and on-device assessment evaluation.
     - Provides instant clinical presets (**Healthy ON** vs. **Severe OFF**) for fast demonstration.
   - **`HistoryCubit` & `HistoryState`**:
     - Maintains longitudinal assessment logs over days and weeks.
     - Calculates running severity averages, ON/OFF distribution, and supplies chronological data points for the trend chart.
   - **`MedicationCubit` & `MedicationScheduleState`**:
     - Tracks daily prescription dosages (e.g. Levodopa/Carbidopa morning, noon, evening).
     - Provides one-tap toggle for medication compliance and calculates daily adherence percentages.

3. **Interactive Motor Assessment Screens**:
   - 🎙️ **Voice Task** (`VoiceTaskScreen`): Soundwave visualizer, 5-second sustained phonation timer, pitch perturbation (Jitter/Shimmer) extraction.
   - 🌀 **Spiral Drawing** (`SpiralTaskScreen`): **Live interactive canvas** where the user traces an Archimedean spiral with their finger! Automatically calculates motor velocity smoothness and micro-tremor deviations.
   - 👆 **Finger Tapping** (`TapTaskScreen`): Two large alternating tactile tap targets with 10-second countdown and real-time cadence calculation (`taps/sec`).
   - 🚶 **Gait & Walk** (`WalkTaskScreen`): Mobility and turning hesitation test.
   - 📊 **Results Screen** (`ResultsScreen`): Circular `SeverityGauge` (0.0 to 5.0), ON/OFF status badge, confidence percentage, modality progress bars, and clinical guidance.
   - 📈 **Longitudinal Trends** (`HistoryScreen`): Interactive `fl_chart` severity curve tracking symptom fluctuation over time.
   - 💊 **Medication Schedule** (`MedicationScreen`): Interactive daily pill checklist with adherence ring.

---

## 🚀 How to Run the App

Open your terminal, navigate to the `parkimed_flutter` directory:

```bash
cd d:\parkimed-ai\parkimed_flutter
```

### 1. Run on Chrome (Instant browser preview):
```bash
flutter run -d chrome
```

### 2. Run on Windows Desktop:
```bash
flutter run -d windows
```

### 3. Run on an Android Phone / Emulator:
Ensure your Android device is connected via USB with USB debugging enabled, or start an Android Emulator in Android Studio:
```bash
flutter devices
flutter run -d android
```

---

## 📁 Directory Structure

```
parkimed_flutter/
├── lib/
│   ├── core/
│   │   ├── models/
│   │   │   ├── assessment_result.dart    # AssessmentResult data model
│   │   │   └── medication_dose.dart      # Medication prescription schedule model
│   │   └── theme/
│   │       └── app_theme.dart            # Light clinical theme, colors, typography
│   ├── cubits/
│   │   ├── assessment/
│   │   │   ├── assessment_cubit.dart     # Business logic for motor tests & evaluation
│   │   │   └── assessment_state.dart
│   │   ├── history/
│   │   │   ├── history_cubit.dart        # Longitudinal history & trend management
│   │   │   └── history_state.dart
│   │   └── medication/
│   │       ├── medication_cubit.dart     # Daily dosage schedule & adherence tracker
│   │       └── medication_state.dart
│   ├── screens/
│   │   ├── main_screen.dart              # Bottom navigation (Dashboard, Trends, Schedule)
│   │   ├── home/
│   │   │   └── home_screen.dart          # Hero state card, quick test grid, medication banner
│   │   ├── tasks/
│   │   │   ├── voice_task_screen.dart    # Voice acoustic test with animated waves
│   │   │   ├── spiral_task_screen.dart   # Interactive touch drawing canvas for spiral
│   │   │   ├── tap_task_screen.dart      # Alternating finger tapping test
│   │   │   └── walk_task_screen.dart     # Gait & turning kinematics test
│   │   ├── results/
│   │   │   └── results_screen.dart       # Severity radial gauge, status badge, breakdown
│   │   ├── history/
│   │   │   └── history_screen.dart       # fl_chart severity curve + assessment history
│   │   └── medication/
│   │       └── medication_screen.dart    # Interactive pill checklist & adherence gauge
│   ├── widgets/
│   │   ├── animated_wave.dart            # Soundwave animation
│   │   ├── modality_bar.dart             # Per-modality breakdown bar
│   │   ├── severity_gauge.dart           # Custom circular severity meter (0.0 - 5.0)
│   │   └── status_chip.dart              # Clinical ON/OFF badge
│   └── main.dart                         # MultiBlocProvider entry point
└── test/
    └── widget_test.dart                  # Automated smoke test (verified passing)
```
