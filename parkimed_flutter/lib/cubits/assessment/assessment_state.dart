import 'package:equatable/equatable.dart';
import '../../core/models/assessment_result.dart';

enum AssessmentStatus {
  idle,
  active, // Recording or interacting
  processing, // Computing features & running TFLite inference
  completed,
  error,
}

class AssessmentState extends Equatable {
  final AssessmentStatus status;
  final AssessmentTaskType currentTask;
  final int activeSecondsRemaining;
  final double liveProgress; // 0.0 to 1.0
  final AssessmentResult? lastResult;
  final String? errorMessage;
  final Map<String, dynamic> liveMetrics; // e.g., current pitch, tap count, spiral smoothness

  const AssessmentState({
    this.status = AssessmentStatus.idle,
    this.currentTask = AssessmentTaskType.voice,
    this.activeSecondsRemaining = 0,
    this.liveProgress = 0.0,
    this.lastResult,
    this.errorMessage,
    this.liveMetrics = const {},
  });

  bool get isIdle => status == AssessmentStatus.idle;
  bool get isActive => status == AssessmentStatus.active;
  bool get isProcessing => status == AssessmentStatus.processing;
  bool get isCompleted => status == AssessmentStatus.completed;

  AssessmentState copyWith({
    AssessmentStatus? status,
    AssessmentTaskType? currentTask,
    int? activeSecondsRemaining,
    double? liveProgress,
    AssessmentResult? lastResult,
    String? errorMessage,
    Map<String, dynamic>? liveMetrics,
  }) {
    return AssessmentState(
      status: status ?? this.status,
      currentTask: currentTask ?? this.currentTask,
      activeSecondsRemaining: activeSecondsRemaining ?? this.activeSecondsRemaining,
      liveProgress: liveProgress ?? this.liveProgress,
      lastResult: lastResult ?? this.lastResult,
      errorMessage: errorMessage ?? this.errorMessage,
      liveMetrics: liveMetrics ?? this.liveMetrics,
    );
  }

  @override
  List<Object?> get props => [
        status,
        currentTask,
        activeSecondsRemaining,
        liveProgress,
        lastResult,
        errorMessage,
        liveMetrics,
      ];
}
