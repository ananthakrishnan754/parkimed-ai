import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';
import '../../cubits/history/history_cubit.dart';
import '../../cubits/history/history_state.dart';
import '../../cubits/medication/medication_cubit.dart';
import '../../cubits/medication/medication_state.dart';
import '../../core/theme/app_theme.dart';
import '../../widgets/status_chip.dart';
import '../tasks/voice_task_screen.dart';
import '../tasks/spiral_task_screen.dart';
import '../tasks/tap_task_screen.dart';
import '../tasks/walk_task_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final todayStr = DateFormat('EEEE, MMMM d').format(DateTime.now());

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: AppTheme.surfaceBorder),
                          boxShadow: [
                            BoxShadow(
                              color: AppTheme.primary.withValues(alpha: 0.08),
                              blurRadius: 10,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        clipBehavior: Clip.antiAlias,
                        child: Image.asset(
                          'assets/App_logo.png',
                          fit: BoxFit.cover,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            todayStr.toUpperCase(),
                            style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 1.0,
                              color: AppTheme.textMuted,
                            ),
                          ),
                          const SizedBox(height: 2),
                          const Text(
                            'parkiMed',
                            style: TextStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.w800,
                              color: AppTheme.textPrimary,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: AppTheme.primaryLight,
                      shape: BoxShape.circle,
                      border: Border.all(color: AppTheme.primaryMuted),
                    ),
                    child: const Icon(
                      Icons.person_rounded,
                      color: AppTheme.primary,
                      size: 24,
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 20),

              // Hero Status Card (Listening to HistoryCubit)
              BlocBuilder<HistoryCubit, HistoryState>(
                builder: (context, state) {
                  final latest = state.latestResult;
                  final isOn = latest?.isOn ?? true;
                  final severity = latest?.severity ?? 1.1;

                  return Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(22),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: isOn
                            ? [const Color(0xFFF0FDF4), Colors.white]
                            : [const Color(0xFFFFF1F2), Colors.white],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(
                        color: isOn ? AppTheme.stateOnBorder : AppTheme.stateOffBorder,
                        width: 1.5,
                      ),
                      boxShadow: AppTheme.cardShadow,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'CURRENT MEDICATION STATE',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                                letterSpacing: 0.8,
                                color: isOn ? AppTheme.stateOn : AppTheme.stateOff,
                              ),
                            ),
                            if (latest != null)
                              StatusChip(state: latest.state),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(
                              isOn ? 'ON State' : 'OFF State',
                              style: TextStyle(
                                fontSize: 32,
                                fontWeight: FontWeight.w800,
                                color: isOn ? const Color(0xFF065F46) : const Color(0xFF9F1239),
                                height: 1.0,
                              ),
                            ),
                            const Spacer(),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                const Text(
                                  'SEVERITY',
                                  style: TextStyle(
                                    fontSize: 10,
                                    fontWeight: FontWeight.w700,
                                    color: AppTheme.textMuted,
                                    letterSpacing: 0.5,
                                  ),
                                ),
                                Text(
                                  '${severity.toStringAsFixed(1)} / 5.0',
                                  style: const TextStyle(
                                    fontSize: 20,
                                    fontWeight: FontWeight.w800,
                                    color: AppTheme.textPrimary,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Text(
                          isOn
                              ? 'Motor symptoms controlled. Voice & kinetic tests within healthy thresholds.'
                              : 'Motor tremor or bradykinesia elevated. Check medication schedule.',
                          style: TextStyle(
                            fontSize: 13,
                            color: isOn ? const Color(0xFF047857) : const Color(0xFFBE123C),
                            height: 1.3,
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),

              const SizedBox(height: 16),

              // Medication Next Dose Card
              BlocBuilder<MedicationCubit, MedicationScheduleState>(
                builder: (context, medState) {
                  final nextDose = medState.nextDueDose;
                  if (nextDose == null) return const SizedBox.shrink();

                  return Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryLight,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: AppTheme.primaryMuted),
                    ),
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: AppTheme.primary,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: const Icon(Icons.medication_liquid_rounded, color: Colors.white, size: 22),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'NEXT SCHEDULED DOSE',
                                style: TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w700,
                                  color: AppTheme.primaryDark,
                                  letterSpacing: 0.5,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${nextDose.name} (${nextDose.dosage})',
                                style: const TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w700,
                                  color: AppTheme.textPrimary,
                                ),
                              ),
                            ],
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: AppTheme.primaryMuted),
                          ),
                          child: Text(
                            nextDose.timeFormatted,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w800,
                              color: AppTheme.primary,
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),

              const SizedBox(height: 24),

              // Section Header: Quick Motor Tasks
              const Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Motor Assessment Tasks',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.textPrimary,
                    ),
                  ),
                  Text(
                    'On-Device AI',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: AppTheme.primary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // 2x2 Grid of Task Cards
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 14,
                crossAxisSpacing: 14,
                childAspectRatio: 1.05,
                children: [
                  _TaskCard(
                    title: 'Voice Test',
                    subtitle: 'Vocal tremor & pitch stability',
                    duration: '5 sec',
                    icon: Icons.mic_rounded,
                    gradientColors: const [Color(0xFF6366F1), Color(0xFF4F46E5)],
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const VoiceTaskScreen()),
                      );
                    },
                  ),
                  _TaskCard(
                    title: 'Spiral Drawing',
                    subtitle: 'Coordination & kinetic tremor',
                    duration: 'Interactive',
                    icon: Icons.gesture_rounded,
                    gradientColors: const [Color(0xFF0EA5E9), Color(0xFF0284C7)],
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const SpiralTaskScreen()),
                      );
                    },
                  ),
                  _TaskCard(
                    title: 'Finger Tapping',
                    subtitle: 'Speed & rhythm bradykinesia',
                    duration: '10 sec',
                    icon: Icons.touch_app_rounded,
                    gradientColors: const [Color(0xFF10B981), Color(0xFF059669)],
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const TapTaskScreen()),
                      );
                    },
                  ),
                  _TaskCard(
                    title: 'Gait & Walk',
                    subtitle: 'Turning kinematics & hesitation',
                    duration: '15 sec',
                    icon: Icons.directions_walk_rounded,
                    gradientColors: const [Color(0xFFF59E0B), Color(0xFFD97706)],
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const WalkTaskScreen()),
                      );
                    },
                  ),
                ],
              ),

              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }
}

class _TaskCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final String duration;
  final IconData icon;
  final List<Color> gradientColors;
  final VoidCallback onTap;

  const _TaskCard({
    required this.title,
    required this.subtitle,
    required this.duration,
    required this.icon,
    required this.gradientColors,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(22),
          border: Border.all(color: AppTheme.surfaceBorder),
          boxShadow: AppTheme.softShadow,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(colors: gradientColors),
                    borderRadius: BorderRadius.circular(14),
                    boxShadow: [
                      BoxShadow(
                        color: gradientColors.first.withValues(alpha: 0.3),
                        blurRadius: 8,
                        offset: const Offset(0, 3),
                      ),
                    ],
                  ),
                  child: Icon(icon, color: Colors.white, size: 22),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppTheme.surfaceSubtle,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    duration,
                    style: const TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.textMuted,
                    ),
                  ),
                ),
              ],
            ),
            const Spacer(),
            Text(
              title,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: AppTheme.textPrimary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              subtitle,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 11,
                color: AppTheme.textMuted,
                height: 1.25,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
