import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../core/models/assessment_result.dart';
import '../../core/theme/app_theme.dart';
import '../../widgets/modality_bar.dart';
import '../../widgets/severity_gauge.dart';
import '../../widgets/status_chip.dart';

class ResultsScreen extends StatelessWidget {
  final AssessmentResult result;

  const ResultsScreen({super.key, required this.result});

  @override
  Widget build(BuildContext context) {
    final isOn = result.isOn;
    final dateStr = DateFormat('MMM dd, yyyy • h:mm a').format(result.timestamp);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Assessment Results'),
        automaticallyImplyLeading: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.close_rounded),
            onPressed: () => Navigator.pop(context),
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Timestamp & Status badge
              Text(
                dateStr,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.textMuted,
                ),
              ),
              const SizedBox(height: 12),
              StatusChip(state: result.state, isLarge: true),

              const SizedBox(height: 24),

              // Main Card: Severity Gauge
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 20),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: AppTheme.surfaceBorder),
                  boxShadow: AppTheme.cardShadow,
                ),
                child: Column(
                  children: [
                    const Text(
                      'MOTOR SEVERITY SCORE',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.0,
                        color: AppTheme.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 20),
                    SeverityGauge(severity: result.severity, size: 200),
                    const SizedBox(height: 16),
                    Text(
                      result.stateDescription,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: isOn ? AppTheme.stateOn : AppTheme.stateOff,
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 16),

              // Confidence & Model Metrics Card
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: AppTheme.surfaceBorder),
                  boxShadow: AppTheme.softShadow,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.psychology_outlined, color: AppTheme.primary, size: 22),
                        const SizedBox(width: 8),
                        const Text(
                          'On-Device AI Confidence',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                            color: AppTheme.textPrimary,
                          ),
                        ),
                        const Spacer(),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppTheme.primaryLight,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            '${(result.confidence * 100).toInt()}%',
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w800,
                              color: AppTheme.primary,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    const Divider(color: AppTheme.surfaceSubtle),
                    const SizedBox(height: 8),

                    // Modality scores
                    ...result.modalityScores.entries.map((entry) {
                      return ModalityBar(
                        label: entry.key,
                        score: entry.value,
                        icon: Icons.check_circle_outline_rounded,
                      );
                    }),
                  ],
                ),
              ),

              const SizedBox(height: 16),

              // Clinical Note Card
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: isOn ? AppTheme.stateOnLight : AppTheme.stateOffLight,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: isOn ? AppTheme.stateOnBorder : AppTheme.stateOffBorder,
                  ),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      Icons.medical_services_outlined,
                      color: isOn ? AppTheme.stateOn : AppTheme.stateOff,
                      size: 22,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            isOn ? 'Medication Functioning Well' : 'Motor Fluctuation Alert',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: isOn ? AppTheme.stateOn : AppTheme.stateOff,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            result.notes,
                            style: const TextStyle(
                              fontSize: 13,
                              color: AppTheme.textSecondary,
                              height: 1.35,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 28),

              // Return Button
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  icon: const Icon(Icons.dashboard_rounded),
                  label: const Text('Back to Dashboard'),
                  onPressed: () => Navigator.pop(context),
                ),
              ),
              const SizedBox(height: 12),
            ],
          ),
        ),
      ),
    );
  }
}
