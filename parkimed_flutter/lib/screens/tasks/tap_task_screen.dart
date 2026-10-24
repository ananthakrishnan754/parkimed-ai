import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../cubits/assessment/assessment_cubit.dart';
import '../../cubits/assessment/assessment_state.dart';
import '../../cubits/history/history_cubit.dart';
import '../../core/models/assessment_result.dart';
import '../../core/theme/app_theme.dart';
import '../results/results_screen.dart';

class TapTaskScreen extends StatelessWidget {
  const TapTaskScreen({super.key});

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
        final isTapping = state.isActive && state.currentTask == AssessmentTaskType.tap;
        final isProcessing = state.isProcessing && state.currentTask == AssessmentTaskType.tap;
        final tapCount = state.liveMetrics['tapCount'] as int? ?? 0;
        final cadence = state.liveMetrics['cadence'] as double? ?? 0.0;

        return Scaffold(
          appBar: AppBar(
            title: const Text('Finger Tapping Test'),
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
                  // Instruction Card
                  Container(
                    padding: const EdgeInsets.all(18),
                    decoration: BoxDecoration(
                      color: AppTheme.accentCyanLight,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: AppTheme.accentCyan.withValues(alpha: 0.3)),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.touch_app_rounded, color: AppTheme.accentCyan, size: 28),
                        SizedBox(width: 14),
                        Expanded(
                          child: Text(
                            'Tap the two target buttons alternately with your index and middle finger as fast and steadily as possible for 10 seconds.',
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

                  const SizedBox(height: 24),

                  // Timer & Stats Counter
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _StatBadge(
                        title: 'TIME REMAINING',
                        value: '${isTapping ? state.activeSecondsRemaining : 10}s',
                        color: AppTheme.primary,
                      ),
                      _StatBadge(
                        title: 'TOTAL TAPS',
                        value: '$tapCount',
                        color: AppTheme.accentCyan,
                      ),
                      _StatBadge(
                        title: 'CADENCE',
                        value: '${cadence.toStringAsFixed(1)} /s',
                        color: AppTheme.stateOn,
                      ),
                    ],
                  ),

                  const Spacer(),

                  // Tapping Targets (Two large circular buttons)
                  if (isTapping) ...[
                    Row(
                      children: [
                        Expanded(
                          child: _TapTargetButton(
                            label: 'LEFT',
                            onTap: cubit.recordTap,
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: _TapTargetButton(
                            label: 'RIGHT',
                            onTap: cubit.recordTap,
                          ),
                        ),
                      ],
                    ),
                  ] else if (isProcessing) ...[
                    const CircularProgressIndicator(color: AppTheme.primary),
                    const SizedBox(height: 16),
                    const Text(
                      'Analyzing Bradykinesia & Rhythm Stability...',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                  ] else ...[
                    Container(
                      width: 130,
                      height: 130,
                      decoration: BoxDecoration(
                        color: AppTheme.primaryLight,
                        shape: BoxShape.circle,
                        border: Border.all(color: AppTheme.primaryMuted, width: 2),
                      ),
                      child: const Icon(
                        Icons.touch_app_rounded,
                        size: 56,
                        color: AppTheme.primary,
                      ),
                    ),
                    const SizedBox(height: 20),
                    const Text(
                      'Place phone flat on a table',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                  ],

                  const Spacer(),

                  // Start button
                  if (!isTapping && !isProcessing) ...[
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        icon: const Icon(Icons.play_arrow_rounded, size: 24),
                        label: const Text('Start 10s Tapping Test'),
                        onPressed: cubit.startTapTask,
                      ),
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

class _StatBadge extends StatelessWidget {
  final String title;
  final String value;
  final Color color;

  const _StatBadge({
    required this.title,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.surfaceBorder),
        boxShadow: AppTheme.softShadow,
      ),
      child: Column(
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w700,
              color: AppTheme.textMuted,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w800,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

class _TapTargetButton extends StatefulWidget {
  final String label;
  final VoidCallback onTap;

  const _TapTargetButton({required this.label, required this.onTap});

  @override
  State<_TapTargetButton> createState() => _TapTargetButtonState();
}

class _TapTargetButtonState extends State<_TapTargetButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) {
        setState(() => _isPressed = true);
        widget.onTap();
      },
      onTapUp: (_) => setState(() => _isPressed = false),
      onTapCancel: () => setState(() => _isPressed = false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 90),
        height: 180,
        decoration: BoxDecoration(
          gradient: _isPressed
              ? LinearGradient(
                  colors: [AppTheme.primaryDark, AppTheme.primary],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                )
              : LinearGradient(
                  colors: [Colors.white, AppTheme.primaryLight],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: _isPressed ? AppTheme.primary : AppTheme.primaryMuted,
            width: 2.5,
          ),
          boxShadow: _isPressed ? [] : AppTheme.cardShadow,
        ),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.fingerprint_rounded,
                size: 48,
                color: _isPressed ? Colors.white : AppTheme.primary,
              ),
              const SizedBox(height: 8),
              Text(
                widget.label,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                  color: _isPressed ? Colors.white : AppTheme.primary,
                  letterSpacing: 1.0,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
