import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../cubits/assessment/assessment_cubit.dart';
import '../../cubits/assessment/assessment_state.dart';
import '../../cubits/history/history_cubit.dart';
import '../../core/theme/app_theme.dart';
import '../../widgets/animated_wave.dart';
import '../results/results_screen.dart';

class VoiceTaskScreen extends StatelessWidget {
  const VoiceTaskScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<AssessmentCubit, AssessmentState>(
      listener: (context, state) {
        if (state.isCompleted && state.lastResult != null) {
          // Push result to history
          context.read<HistoryCubit>().addAssessment(state.lastResult!);
          // Navigate to results
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
        final isRecording = state.isActive;
        final isProcessing = state.isProcessing;

        return Scaffold(
          appBar: AppBar(
            title: const Text('Voice Acoustic Test'),
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
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryLight,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: AppTheme.primaryMuted),
                    ),
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppTheme.primary,
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: const Icon(Icons.mic_rounded, color: Colors.white, size: 24),
                        ),
                        const SizedBox(width: 16),
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Sustain "AAAHHH"',
                                style: TextStyle(
                                  fontWeight: FontWeight.w700,
                                  fontSize: 16,
                                  color: AppTheme.primaryDark,
                                ),
                              ),
                              SizedBox(height: 4),
                              Text(
                                'Hold phone 15cm away and take a deep breath. Sustain a clear vowel sound.',
                                style: TextStyle(
                                  fontSize: 13,
                                  color: AppTheme.textSecondary,
                                  height: 1.3,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),

                  const Spacer(),

                  // Animated Waveform & Mic Circle
                  Stack(
                    alignment: Alignment.center,
                    children: [
                      // Pulsing Rings
                      if (isRecording) ...[
                        Container(
                          width: 220,
                          height: 220,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: AppTheme.primary.withValues(alpha: 0.08),
                          ),
                        ),
                        Container(
                          width: 170,
                          height: 170,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: AppTheme.primary.withValues(alpha: 0.15),
                          ),
                        ),
                      ],
                      Container(
                        width: 120,
                        height: 120,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: isRecording
                              ? AppTheme.primaryGradient
                              : const LinearGradient(
                                  colors: [AppTheme.surfaceSubtle, AppTheme.surfaceBorder],
                                ),
                          boxShadow: isRecording ? AppTheme.cardShadow : null,
                        ),
                        child: Center(
                          child: isProcessing
                              ? const SizedBox(
                                  width: 36,
                                  height: 36,
                                  child: CircularProgressIndicator(
                                    color: Colors.white,
                                    strokeWidth: 3,
                                  ),
                                )
                              : Icon(
                                  Icons.mic_rounded,
                                  size: 48,
                                  color: isRecording ? Colors.white : AppTheme.textSecondary,
                                ),
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 32),

                  // Timer / Status
                  if (isRecording) ...[
                    Text(
                      '${state.activeSecondsRemaining}s',
                      style: const TextStyle(
                        fontSize: 42,
                        fontWeight: FontWeight.w800,
                        color: AppTheme.primary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Recording phonation...',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 16),
                    AnimatedWave(isAnimating: true, height: 40, color: AppTheme.primary),
                  ] else if (isProcessing) ...[
                    const Text(
                      'Running MDVP Acoustic Extraction...',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.primary,
                      ),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Analyzing Pitch Jitter & Shimmer via TFLite',
                      style: TextStyle(
                        fontSize: 13,
                        color: AppTheme.textMuted,
                      ),
                    ),
                  ] else ...[
                    const Text(
                      'Ready to begin',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Tap Start when you are in a quiet room',
                      style: TextStyle(
                        fontSize: 14,
                        color: AppTheme.textMuted,
                      ),
                    ),
                  ],

                  const Spacer(),

                  // Action Buttons
                  if (!isRecording && !isProcessing) ...[
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        icon: const Icon(Icons.play_arrow_rounded, size: 24),
                        label: const Text('Start Voice Test (5s)'),
                        onPressed: () => cubit.startVoiceTask(simulateSevere: false),
                      ),
                    ),
                    const SizedBox(height: 12),
                    // Quick Preset for clinical demo
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
                            onPressed: () => cubit.startVoiceTask(simulateSevere: false),
                            child: const Text('Demo: Healthy ON'),
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
                            onPressed: () => cubit.startVoiceTask(simulateSevere: true),
                            child: const Text('Demo: Severe OFF'),
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
