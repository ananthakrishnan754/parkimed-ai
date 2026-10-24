import 'dart:async';
import 'dart:typed_data';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../core/models/assessment_result.dart';
import '../../core/features/feature_extractor.dart';
import '../../services/assessment_service.dart';
import 'assessment_state.dart';
import '../../core/features/feature_extractor.dart' show Offset;

class AssessmentCubit extends Cubit<AssessmentState> {
  Timer? _countdownTimer;

  AssessmentCubit() : super(const AssessmentState());

  @override
  Future<void> close() {
    _countdownTimer?.cancel();
    return super.close();
  }

  void reset() {
    _countdownTimer?.cancel();
    emit(const AssessmentState());
  }

  /// Start a Voice Assessment (Phonation "AAAHHH")
  void startVoiceTask({bool simulateSevere = false}) async {
    _countdownTimer?.cancel();
    emit(state.copyWith(
      status: AssessmentStatus.active,
      currentTask: AssessmentTaskType.voice,
      activeSecondsRemaining: 5,
      liveProgress: 0.0,
      liveMetrics: {
        'rms': 0.12,
        'pitch': 132.5,
        'jitter': simulateSevere ? 0.045 : 0.008,
        'shimmer': simulateSevere ? 0.065 : 0.015,
      },
    ));

    int secondsLeft = 5;
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      secondsLeft--;
      final progress = (5 - secondsLeft) / 5.0;

      if (secondsLeft <= 0) {
        timer.cancel();
        _processVoiceAssessment(simulateSevere: simulateSevere);
      } else {
        emit(state.copyWith(
          activeSecondsRemaining: secondsLeft,
          liveProgress: progress,
          liveMetrics: {
            'rms': 0.18 + (secondsLeft % 2 == 0 ? 0.05 : -0.03),
            'pitch': 130.0 + (simulateSevere ? (secondsLeft * 6.5) : (secondsLeft * 1.2)),
            'jitter': simulateSevere ? 0.052 : 0.009,
            'shimmer': simulateSevere ? 0.078 : 0.018,
          },
        ));
      }
    });
  }

  void _processVoiceAssessment({required bool simulateSevere}) async {
    emit(state.copyWith(
      status: AssessmentStatus.processing,
      liveProgress: 1.0,
      activeSecondsRemaining: 0,
    ));

    try {
      final pred = simulateSevere
          ? await AssessmentService.instance.runVoiceDemoSevere()
          : await AssessmentService.instance.runVoiceDemoHealthy();

      final result = AssessmentResult(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        timestamp: DateTime.now(),
        state: pred.onOff == 'ON' ? MedicationState.on : MedicationState.off,
        severity: pred.severity,
        confidence: pred.confidence,
        taskType: AssessmentTaskType.voice,
        modalityScores: {
          'Voice (MDVP)': pred.onOff == 'ON' ? 0.92 : 0.22,
          'Jitter / Shimmer': pred.onOff == 'ON' ? 0.88 : 0.31,
          'Fundamental Pitch': pred.onOff == 'ON' ? 0.95 : 0.28,
        },
        notes: pred.onOff == 'OFF'
            ? 'Elevated micro-tremor in vocal fold phonation. Acoustic features indicate medication wearing off.'
            : 'Stable fundamental frequency and normal perturbation. Medication is actively functioning.',
      );

      emit(state.copyWith(
        status: AssessmentStatus.completed,
        lastResult: result,
      ));
    } catch (e) {
      // Fallback to original logic if model fails to load
      final result = AssessmentResult(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        timestamp: DateTime.now(),
        state: simulateSevere ? MedicationState.off : MedicationState.on,
        severity: simulateSevere ? 3.6 : 1.1,
        confidence: simulateSevere ? 0.89 : 0.94,
        taskType: AssessmentTaskType.voice,
        modalityScores: {
          'Voice (MDVP)': simulateSevere ? 0.22 : 0.92,
          'Jitter / Shimmer': simulateSevere ? 0.31 : 0.88,
          'Fundamental Pitch': simulateSevere ? 0.28 : 0.95,
        },
        notes: simulateSevere
            ? 'Elevated micro-tremor in vocal fold phonation. Acoustic features indicate medication wearing off.'
            : 'Stable fundamental frequency and normal perturbation. Medication is actively functioning.',
      );
      emit(state.copyWith(
        status: AssessmentStatus.completed,
        lastResult: result,
      ));
    }
  }

  /// Complete a Spiral Drawing Assessment from canvas coordinates
  void completeSpiralAssessment({
    required double smoothnessScore, // 0.0 to 1.0
    required double tremorDeviation,
    List<Offset>? points,
  }) async {
    emit(state.copyWith(
      status: AssessmentStatus.processing,
      currentTask: AssessmentTaskType.spiral,
      liveProgress: 1.0,
    ));

    try {
      if (points != null && points.isNotEmpty) {
        final pred = await AssessmentService.instance.runSpiral(points);
        final isSevere = pred.onOff == 'OFF';
        final result = AssessmentResult(
          id: DateTime.now().millisecondsSinceEpoch.toString(),
          timestamp: DateTime.now(),
          state: isSevere ? MedicationState.off : MedicationState.on,
          severity: pred.severity,
          confidence: pred.confidence,
          taskType: AssessmentTaskType.spiral,
          modalityScores: {
            'Spiral Kinematics': smoothnessScore.clamp(0.1, 1.0),
            'Tremor Amplitude': (1.0 - tremorDeviation).clamp(0.1, 0.99),
            'Trajectory Regularity': (smoothnessScore * 0.95).clamp(0.1, 1.0),
          },
          notes: isSevere
              ? 'Tremulous spiral trace detected with deviation from ideal Archimedean path.'
              : 'Smooth concentric progression with controlled motor velocity.',
        );
        emit(state.copyWith(
          status: AssessmentStatus.completed,
          lastResult: result,
        ));
        return;
      }
    } catch (_) {}

    await Future.delayed(const Duration(milliseconds: 800));

    final isSevere = smoothnessScore < 0.55 || tremorDeviation > 0.45;
    final severity = isSevere
        ? (2.8 + (1.0 - smoothnessScore) * 2.0).clamp(2.0, 4.8)
        : (0.6 + (1.0 - smoothnessScore) * 1.2).clamp(0.4, 1.8);

    final result = AssessmentResult(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      timestamp: DateTime.now(),
      state: isSevere ? MedicationState.off : MedicationState.on,
      severity: double.parse(severity.toStringAsFixed(1)),
      confidence: 0.91,
      taskType: AssessmentTaskType.spiral,
      modalityScores: {
        'Spiral Kinematics': smoothnessScore,
        'Tremor Amplitude': (1.0 - tremorDeviation).clamp(0.1, 0.99),
        'Trajectory Regularity': smoothnessScore * 0.95,
      },
      notes: isSevere
          ? 'Tremulous spiral trace detected with deviation from ideal Archimedean path.'
          : 'Smooth concentric progression with controlled motor velocity.',
    );

    emit(state.copyWith(
      status: AssessmentStatus.completed,
      lastResult: result,
    ));
  }

  /// Start a Finger Tapping Assessment
  void startTapTask() {
    _countdownTimer?.cancel();
    emit(state.copyWith(
      status: AssessmentStatus.active,
      currentTask: AssessmentTaskType.tap,
      activeSecondsRemaining: 10,
      liveProgress: 0.0,
      liveMetrics: {
        'tapCount': 0,
        'cadence': 0.0,
      },
    ));

    int secondsLeft = 10;
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      secondsLeft--;
      final progress = (10 - secondsLeft) / 10.0;

      if (secondsLeft <= 0) {
        timer.cancel();
        _processTapAssessment();
      } else {
        emit(state.copyWith(
          activeSecondsRemaining: secondsLeft,
          liveProgress: progress,
        ));
      }
    });
  }

  void recordTap() {
    if (!state.isActive || state.currentTask != AssessmentTaskType.tap) return;
    final currentTaps = (state.liveMetrics['tapCount'] as int? ?? 0) + 1;
    final elapsedSec = 10 - state.activeSecondsRemaining;
    final cadence = elapsedSec > 0 ? (currentTaps / elapsedSec) : 0.0;

    emit(state.copyWith(
      liveMetrics: {
        'tapCount': currentTaps,
        'cadence': cadence,
      },
    ));
  }

  void _processTapAssessment() async {
    emit(state.copyWith(
      status: AssessmentStatus.processing,
      liveProgress: 1.0,
    ));

    try {
      // Use demo tap (visual head) if model available, matching original behavior
      final pred = await AssessmentService.instance.runTapDemo();
      final totalTaps = state.liveMetrics['tapCount'] as int? ?? 0;
      final result = AssessmentResult(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        timestamp: DateTime.now(),
        state: pred.onOff == 'ON' ? MedicationState.on : MedicationState.off,
        severity: pred.severity,
        confidence: pred.confidence,
        taskType: AssessmentTaskType.tap,
        modalityScores: {
          'Tapping Speed': (totalTaps / 45.0).clamp(0.1, 1.0),
          'Rhythm Stability': pred.onOff == 'OFF' ? 0.42 : 0.91,
        },
        notes: pred.onOff == 'OFF'
            ? 'Bradykinesia and slowing of rhythm noted. Tap cadence decelerated over the 10s window.'
            : 'High amplitude and regular alternating cadence maintained.',
      );
      emit(state.copyWith(
        status: AssessmentStatus.completed,
        lastResult: result,
      ));
      return;
    } catch (_) {}

    await Future.delayed(const Duration(milliseconds: 800));

    final totalTaps = state.liveMetrics['tapCount'] as int? ?? 0;
    // Normal healthy tapping is typically 35-50+ in 10s. Bradykinesia < 22 taps.
    final isSevere = totalTaps < 25;
    final severity = isSevere ? 3.4 : 1.3;

    final result = AssessmentResult(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      timestamp: DateTime.now(),
      state: isSevere ? MedicationState.off : MedicationState.on,
      severity: severity,
      confidence: 0.88,
      taskType: AssessmentTaskType.tap,
      modalityScores: {
        'Tapping Speed': (totalTaps / 45.0).clamp(0.1, 1.0),
        'Rhythm Stability': isSevere ? 0.42 : 0.91,
      },
      notes: isSevere
          ? 'Bradykinesia and slowing of rhythm noted. Tap cadence decelerated over the 10s window.'
          : 'High amplitude and regular alternating cadence maintained.',
    );

    emit(state.copyWith(
      status: AssessmentStatus.completed,
      lastResult: result,
    ));
  }

  /// Run Gait Walk Assessment
  void runGaitAssessment({bool simulateStiffGait = false}) async {
    emit(state.copyWith(
      status: AssessmentStatus.active,
      currentTask: AssessmentTaskType.walk,
      activeSecondsRemaining: 6,
      liveProgress: 0.0,
    ));

    for (int i = 1; i <= 6; i++) {
      await Future.delayed(const Duration(seconds: 1));
      emit(state.copyWith(
        activeSecondsRemaining: 6 - i,
        liveProgress: i / 6.0,
      ));
    }

    emit(state.copyWith(status: AssessmentStatus.processing));
    try {
      final pred = await AssessmentService.instance.runGaitDemo();
      final result = AssessmentResult(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        timestamp: DateTime.now(),
        state: pred.onOff == 'ON' ? MedicationState.on : MedicationState.off,
        severity: pred.severity,
        confidence: pred.confidence,
        taskType: AssessmentTaskType.walk,
        modalityScores: {
          'Stride Cadence': pred.onOff == 'ON' ? 0.94 : 0.35,
          'Turning Hesitation': pred.onOff == 'ON' ? 0.90 : 0.28,
          'Kinematic Balance': pred.onOff == 'ON' ? 0.92 : 0.38,
        },
        notes: pred.onOff == 'OFF'
            ? 'Asymmetry in stride interval with hesitation during turning sequence.'
            : 'Smooth step velocity and fluid directional pivot detected.',
      );
      emit(state.copyWith(
        status: AssessmentStatus.completed,
        lastResult: result,
      ));
      return;
    } catch (_) {}

    await Future.delayed(const Duration(milliseconds: 700));

    final result = AssessmentResult(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      timestamp: DateTime.now(),
      state: simulateStiffGait ? MedicationState.off : MedicationState.on,
      severity: simulateStiffGait ? 4.2 : 1.0,
      confidence: 0.85,
      taskType: AssessmentTaskType.walk,
      modalityScores: {
        'Stride Cadence': simulateStiffGait ? 0.35 : 0.94,
        'Turning Hesitation': simulateStiffGait ? 0.28 : 0.90,
        'Kinematic Balance': simulateStiffGait ? 0.38 : 0.92,
      },
      notes: simulateStiffGait
          ? 'Asymmetry in stride interval with hesitation during turning sequence.'
          : 'Smooth step velocity and fluid directional pivot detected.',
    );

    emit(state.copyWith(
      status: AssessmentStatus.completed,
      lastResult: result,
    ));
  }
}
