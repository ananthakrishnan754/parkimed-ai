import 'package:equatable/equatable.dart';

enum MedicationState { on, off }

enum AssessmentTaskType {
  voice,
  spiral,
  tap,
  walk,
  composite,
}

class AssessmentResult extends Equatable {
  final String id;
  final DateTime timestamp;
  final MedicationState state;
  final double severity; // 0.0 to 5.0 scale
  final double confidence; // 0.0 to 1.0 (e.g. 0.92 = 92%)
  final AssessmentTaskType taskType;
  final Map<String, double> modalityScores; // voice, spiral, tap, motion
  final String notes;

  const AssessmentResult({
    required this.id,
    required this.timestamp,
    required this.state,
    required this.severity,
    required this.confidence,
    required this.taskType,
    this.modalityScores = const {},
    this.notes = '',
  });

  bool get isOn => state == MedicationState.on;

  String get stateDisplay => isOn ? 'ON State' : 'OFF State';
  String get stateDescription => isOn
      ? 'Medication actively controlling motor symptoms'
      : 'Medication wearing off; elevated motor fluctuations detected';

  String get severityLabel {
    if (severity < 1.0) return 'Optimal Control';
    if (severity < 2.0) return 'Mild Symptoms';
    if (severity < 3.5) return 'Moderate Symptoms';
    return 'Significant Severity';
  }

  AssessmentResult copyWith({
    String? id,
    DateTime? timestamp,
    MedicationState? state,
    double? severity,
    double? confidence,
    AssessmentTaskType? taskType,
    Map<String, double>? modalityScores,
    String? notes,
  }) {
    return AssessmentResult(
      id: id ?? this.id,
      timestamp: timestamp ?? this.timestamp,
      state: state ?? this.state,
      severity: severity ?? this.severity,
      confidence: confidence ?? this.confidence,
      taskType: taskType ?? this.taskType,
      modalityScores: modalityScores ?? this.modalityScores,
      notes: notes ?? this.notes,
    );
  }

  @override
  List<Object?> get props => [
        id,
        timestamp,
        state,
        severity,
        confidence,
        taskType,
        modalityScores,
        notes,
      ];
}
