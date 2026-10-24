import 'package:flutter_bloc/flutter_bloc.dart';
import '../../core/models/assessment_result.dart';
import 'history_state.dart';

class HistoryCubit extends Cubit<HistoryState> {
  HistoryCubit() : super(const HistoryState()) {
    loadInitialHistory();
  }

  void loadInitialHistory() {
    final now = DateTime.now();

    // Pre-populate with realistic 7-day longitudinal data points for Parkinson's monitoring
    final initialRecords = [
      AssessmentResult(
        id: '1',
        timestamp: now.subtract(const Duration(hours: 1, minutes: 20)),
        state: MedicationState.on,
        severity: 1.1,
        confidence: 0.94,
        taskType: AssessmentTaskType.voice,
        modalityScores: const {'Voice (MDVP)': 0.92, 'Pitch Stability': 0.95},
        notes: 'Phonation stable after 8:00 AM Levodopa intake.',
      ),
      AssessmentResult(
        id: '2',
        timestamp: now.subtract(const Duration(hours: 4, minutes: 10)),
        state: MedicationState.off,
        severity: 3.4,
        confidence: 0.89,
        taskType: AssessmentTaskType.spiral,
        modalityScores: const {'Spiral Kinematics': 0.42, 'Tremor Amplitude': 0.38},
        notes: 'Hand tremor and deviation from concentric spiral before noon dose.',
      ),
      AssessmentResult(
        id: '3',
        timestamp: now.subtract(const Duration(days: 1, hours: 2)),
        state: MedicationState.on,
        severity: 1.3,
        confidence: 0.91,
        taskType: AssessmentTaskType.tap,
        modalityScores: const {'Tapping Speed': 0.88, 'Rhythm Stability': 0.90},
        notes: 'Good finger tapping cadence and high amplitude.',
      ),
      AssessmentResult(
        id: '4',
        timestamp: now.subtract(const Duration(days: 1, hours: 6)),
        state: MedicationState.off,
        severity: 3.8,
        confidence: 0.93,
        taskType: AssessmentTaskType.walk,
        modalityScores: const {'Stride Cadence': 0.32, 'Turning Hesitation': 0.28},
        notes: 'Hesitation during 180-degree turn in corridor.',
      ),
      AssessmentResult(
        id: '5',
        timestamp: now.subtract(const Duration(days: 2, hours: 3)),
        state: MedicationState.on,
        severity: 0.9,
        confidence: 0.96,
        taskType: AssessmentTaskType.voice,
        modalityScores: const {'Voice (MDVP)': 0.96, 'Pitch Stability': 0.97},
        notes: 'Normal phonation. Patient reports feeling well.',
      ),
      AssessmentResult(
        id: '6',
        timestamp: now.subtract(const Duration(days: 3, hours: 5)),
        state: MedicationState.off,
        severity: 3.1,
        confidence: 0.87,
        taskType: AssessmentTaskType.spiral,
        modalityScores: const {'Spiral Kinematics': 0.48, 'Tremor Amplitude': 0.45},
        notes: 'Moderate motor rigidity in drawing task.',
      ),
      AssessmentResult(
        id: '7',
        timestamp: now.subtract(const Duration(days: 4, hours: 1)),
        state: MedicationState.on,
        severity: 1.2,
        confidence: 0.92,
        taskType: AssessmentTaskType.tap,
        modalityScores: const {'Tapping Speed': 0.89, 'Rhythm Stability': 0.93},
        notes: 'Consistent alternating finger taps.',
      ),
    ];

    emit(state.copyWith(assessments: initialRecords));
  }

  void addAssessment(AssessmentResult result) {
    final updatedList = [result, ...state.assessments];
    emit(state.copyWith(assessments: updatedList));
  }
}
