import 'package:equatable/equatable.dart';
import '../../core/models/assessment_result.dart';

class HistoryState extends Equatable {
  final List<AssessmentResult> assessments;
  final bool isLoading;

  const HistoryState({
    this.assessments = const [],
    this.isLoading = false,
  });

  AssessmentResult? get latestResult =>
      assessments.isNotEmpty ? assessments.first : null;

  int get onCount =>
      assessments.where((a) => a.state == MedicationState.on).length;

  int get offCount =>
      assessments.where((a) => a.state == MedicationState.off).length;

  double get averageSeverity {
    if (assessments.isEmpty) return 0.0;
    final total = assessments.fold<double>(0.0, (acc, a) => acc + a.severity);
    return double.parse((total / assessments.length).toStringAsFixed(1));
  }

  HistoryState copyWith({
    List<AssessmentResult>? assessments,
    bool? isLoading,
  }) {
    return HistoryState(
      assessments: assessments ?? this.assessments,
      isLoading: isLoading ?? this.isLoading,
    );
  }

  @override
  List<Object?> get props => [assessments, isLoading];
}
