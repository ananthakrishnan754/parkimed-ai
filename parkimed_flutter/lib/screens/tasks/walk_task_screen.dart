import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../cubits/assessment/assessment_cubit.dart';
import '../../cubits/assessment/assessment_state.dart';
import '../../cubits/history/history_cubit.dart';
import '../../core/models/assessment_result.dart';
import '../../core/theme/app_theme.dart';
import '../results/results_screen.dart';

class WalkTaskScreen extends StatelessWidget {
  const WalkTaskScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<AssessmentCubit, AssessmentState>(
      listener: (context, state) {
        if (state.isCompleted && state.lastResult != null) {
          context.read<HistoryCubit>().addAssessment(state.lastResult!);
          Navigator.pushReplacement(
            context,
            MaterialPageRoute(
              builder: (_) => ResultsScreen(result: state.lastResult!),
            ),
          );
        }
      },
      builder: (context, state) {
        final cubit = context.read<AssessmentCubit>();
        final isWalking = state.isActive && state.currentTask == AssessmentTaskType.walk;
        final isProcessing = state.isProcessing && state.currentTask == AssessmentTaskType.walk;

        return Scaffold(
          appBar: AppBar(
            title: const Text('Gait & Mobility Test'),
            leading: IconButton(
              icon: const Icon(Icons.arrow_back_ios_new_rounded),
              onPressed: () {
                cubit.reset();
                Navigator.pop(context);
              },
            ),
          ),
          body: SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(24.0),
              child: Column(
                children: [
                  Container(
                    padding: const EdgeInsets.all(18),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryLight,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: AppTheme.primaryMuted),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.directions_walk_rounded, color: AppTheme.primary, size: 28),
                        SizedBox(width: 14),
                        Expanded(
                          child: Text(
                            'Hold phone in pocket or hand, walk 10 paces in a straight line, turn around, and return to your starting position.',
                            style: TextStyle(
                              fontSize: 13,
                              color: AppTheme.textSecondary,
                              height: 1.3,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const Spacer(),

                  // Animated gait representation
                  Stack(
                    alignment: Alignment.center,
                    children: [
                      Container(
                        width: 160,
                        height: 160,
                        decoration: BoxDecoration(
                          color: isWalking ? AppTheme.stateOnLight : AppTheme.surfaceSubtle,
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: isWalking ? AppTheme.stateOnBorder : AppTheme.surfaceBorder,
                            width: 2,
                          ),
                          boxShadow: isWalking ? AppTheme.cardShadow : null,
                        ),
                        child: Center(
                          child: isProcessing
                              ? const CircularProgressIndicator(color: AppTheme.primary)
                              : Icon(
                                  Icons.directions_walk_rounded,
                                  size: 72,
                                  color: isWalking ? AppTheme.stateOn : AppTheme.textSecondary,
                                ),
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 24),

                  if (isWalking) ...[
                    Text(
                      '${state.activeSecondsRemaining}s',
                      style: const TextStyle(
                        fontSize: 40,
                        fontWeight: FontWeight.w800,
                        color: AppTheme.stateOn,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Recording motion sensors (accelerometer & gyro)...',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.textSecondary,
                      ),
                    ),
                  ] else if (isProcessing) ...[
                    const Text(
                      'Calculating Kinematics & Turning Hesitation...',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.primary,
                      ),
                    ),
                  ] else ...[
                    const Text(
                      'Ready to Walk',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Ensure you have a clear walkway',
                      style: TextStyle(
                        fontSize: 14,
                        color: AppTheme.textMuted,
                      ),
                    ),
                  ],

                  const Spacer(),

                  if (!isWalking && !isProcessing) ...[
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        icon: const Icon(Icons.play_arrow_rounded, size: 24),
                        label: const Text('Start Walking Test'),
                        onPressed: () => cubit.runGaitAssessment(simulateStiffGait: false),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppTheme.stateOn,
                              side: const BorderSide(color: AppTheme.stateOnBorder),
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            ),
                            onPressed: () => cubit.runGaitAssessment(simulateStiffGait: false),
                            child: const Text('Demo: Fluid Gait'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: OutlinedButton(
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppTheme.stateOff,
                              side: const BorderSide(color: AppTheme.stateOffBorder),
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                            ),
                            onPressed: () => cubit.runGaitAssessment(simulateStiffGait: true),
                            child: const Text('Demo: Stiff / Hesitation'),
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
