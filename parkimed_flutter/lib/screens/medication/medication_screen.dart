import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../cubits/medication/medication_cubit.dart';
import '../../cubits/medication/medication_state.dart';
import '../../core/models/medication_dose.dart';
import '../../core/theme/app_theme.dart';

class MedicationScreen extends StatelessWidget {
  const MedicationScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Medication Schedule'),
        automaticallyImplyLeading: false,
      ),
      body: SafeArea(
        child: BlocBuilder<MedicationCubit, MedicationScheduleState>(
          builder: (context, state) {
            final cubit = context.read<MedicationCubit>();
            final doses = state.todayDoses;
            final percent = (state.adherenceRate * 100).toInt();

            return SingleChildScrollView(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Adherence Progress Card
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      gradient: AppTheme.primaryGradient,
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: AppTheme.cardShadow,
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'TODAY\'S ADHERENCE',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                  color: Colors.white70,
                                  letterSpacing: 0.8,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                '${state.completedCount} of ${state.totalCount} Doses Taken',
                                style: const TextStyle(
                                  fontSize: 20,
                                  fontWeight: FontWeight.w800,
                                  color: Colors.white,
                                ),
                              ),
                              const SizedBox(height: 4),
                              const Text(
                                'Regular timing prevents OFF motor fluctuations',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Colors.white70,
                                ),
                              ),
                            ],
                          ),
                        ),
                        Container(
                          width: 64,
                          height: 64,
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.2),
                            shape: BoxShape.circle,
                          ),
                          child: Center(
                            child: Text(
                              '$percent%',
                              style: const TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.w800,
                                color: Colors.white,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 24),

                  const Text(
                    'Daily Prescriptions',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Doses Timeline List
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: doses.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 12),
                    itemBuilder: (context, index) {
                      final dose = doses[index];
                      return _DoseCard(
                        dose: dose,
                        onToggle: () => cubit.toggleDoseTaken(dose.id),
                      );
                    },
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _DoseCard extends StatelessWidget {
  final MedicationDose dose;
  final VoidCallback onToggle;

  const _DoseCard({required this.dose, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: dose.isTaken ? AppTheme.stateOnBorder : AppTheme.surfaceBorder,
          width: dose.isTaken ? 1.5 : 1.0,
        ),
        boxShadow: AppTheme.softShadow,
      ),
      child: Row(
        children: [
          // Time badge
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: dose.isTaken ? AppTheme.stateOnLight : AppTheme.surfaceSubtle,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Text(
              dose.timeFormatted,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w800,
                color: dose.isTaken ? AppTheme.stateOn : AppTheme.textSecondary,
              ),
            ),
          ),
          const SizedBox(width: 14),

          // Medicine info
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  dose.name,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: AppTheme.textPrimary,
                    decoration: dose.isTaken ? TextDecoration.lineThrough : null,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  dose.dosage,
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppTheme.textMuted,
                  ),
                ),
              ],
            ),
          ),

          // Checkbox toggle button
          IconButton(
            icon: Icon(
              dose.isTaken ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
              color: dose.isTaken ? AppTheme.stateOn : AppTheme.textMuted,
              size: 28,
            ),
            onPressed: onToggle,
          ),
        ],
      ),
    );
  }
}
