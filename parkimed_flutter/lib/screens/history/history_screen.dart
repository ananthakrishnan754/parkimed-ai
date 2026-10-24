import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';
import '../../cubits/history/history_cubit.dart';
import '../../cubits/history/history_state.dart';
import '../../core/models/assessment_result.dart';
import '../../core/theme/app_theme.dart';
import '../../widgets/status_chip.dart';
import '../results/results_screen.dart';

class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Longitudinal Trends'),
        automaticallyImplyLeading: false,
      ),
      body: SafeArea(
        child: BlocBuilder<HistoryCubit, HistoryState>(
          builder: (context, state) {
            final assessments = state.assessments;

            return SingleChildScrollView(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Summary Badges
                  Row(
                    children: [
                      Expanded(
                        child: _StatCard(
                          title: 'AVG SEVERITY',
                          value: '${state.averageSeverity}',
                          subtitle: '0.0 - 5.0 scale',
                          color: AppTheme.primary,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _StatCard(
                          title: 'ON STATE',
                          value: '${state.onCount}',
                          subtitle: 'Controlled tests',
                          color: AppTheme.stateOn,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _StatCard(
                          title: 'OFF STATE',
                          value: '${state.offCount}',
                          subtitle: 'Fluctuations',
                          color: AppTheme.stateOff,
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 20),

                  // Trend Line Chart Card
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(color: AppTheme.surfaceBorder),
                      boxShadow: AppTheme.softShadow,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'Severity Fluctuation Curve',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: AppTheme.textPrimary,
                              ),
                            ),
                            Text(
                              'Target: < 2.0',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                color: AppTheme.stateOn,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),
                        SizedBox(
                          height: 200,
                          child: assessments.isEmpty
                              ? const Center(child: Text('No data recorded yet'))
                              : _buildChart(assessments),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 24),

                  // Recent Records Header
                  const Text(
                    'Assessment History',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Assessment Cards List
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: assessments.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (context, index) {
                      final item = assessments[index];
                      return _AssessmentItemCard(
                        result: item,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => ResultsScreen(result: item),
                            ),
                          );
                        },
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

  Widget _buildChart(List<AssessmentResult> list) {
    // Reverse to show chronologically from left to right
    final chronological = list.reversed.toList();
    final spots = <FlSpot>[];
    for (int i = 0; i < chronological.length; i++) {
      spots.add(FlSpot(i.toDouble(), chronological[i].severity));
    }

    return LineChart(
      LineChartData(
        gridData: FlGridData(
          show: true,
          drawVerticalLine: false,
          horizontalInterval: 1.0,
          getDrawingHorizontalLine: (value) => const FlLine(
            color: AppTheme.surfaceSubtle,
            strokeWidth: 1,
          ),
        ),
        titlesData: FlTitlesData(
          topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          leftTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              interval: 1.0,
              reservedSize: 28,
              getTitlesWidget: (val, meta) => Text(
                val.toInt().toString(),
                style: const TextStyle(fontSize: 11, color: AppTheme.textMuted),
              ),
            ),
          ),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              interval: 1.0,
              reservedSize: 22,
              getTitlesWidget: (val, meta) {
                final idx = val.toInt();
                if (idx >= 0 && idx < chronological.length) {
                  return Text(
                    DateFormat('d/M').format(chronological[idx].timestamp),
                    style: const TextStyle(fontSize: 10, color: AppTheme.textMuted),
                  );
                }
                return const Text('');
              },
            ),
          ),
        ),
        borderData: FlBorderData(show: false),
        minX: 0,
        maxX: (chronological.length - 1).toDouble().clamp(1.0, 20.0),
        minY: 0,
        maxY: 5.0,
        lineBarsData: [
          LineChartBarData(
            spots: spots,
            isCurved: true,
            color: AppTheme.primary,
            barWidth: 3.5,
            isStrokeCapRound: true,
            dotData: FlDotData(
              show: true,
              getDotPainter: (spot, percent, barData, index) {
                final isOff = spot.y >= 2.5;
                return FlDotCirclePainter(
                  radius: 5,
                  color: isOff ? AppTheme.stateOff : AppTheme.stateOn,
                  strokeWidth: 2,
                  strokeColor: Colors.white,
                );
              },
            ),
            belowBarData: BarAreaData(
              show: true,
              gradient: LinearGradient(
                colors: [
                  AppTheme.primary.withValues(alpha: 0.2),
                  AppTheme.primary.withValues(alpha: 0.0),
                ],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  final String title;
  final String value;
  final String subtitle;
  final Color color;

  const _StatCard({
    required this.title,
    required this.value,
    required this.subtitle,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppTheme.surfaceBorder),
        boxShadow: AppTheme.softShadow,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.5,
              color: AppTheme.textMuted,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w800,
              color: color,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            subtitle,
            style: const TextStyle(
              fontSize: 10,
              color: AppTheme.textSecondary,
            ),
          ),
        ],
      ),
    );
  }
}

class _AssessmentItemCard extends StatelessWidget {
  final AssessmentResult result;
  final VoidCallback onTap;

  const _AssessmentItemCard({required this.result, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final timeStr = DateFormat('MMM d • h:mm a').format(result.timestamp);
    IconData taskIcon;
    switch (result.taskType) {
      case AssessmentTaskType.voice:
        taskIcon = Icons.mic_rounded;
        break;
      case AssessmentTaskType.spiral:
        taskIcon = Icons.gesture_rounded;
        break;
      case AssessmentTaskType.tap:
        taskIcon = Icons.touch_app_rounded;
        break;
      case AssessmentTaskType.walk:
        taskIcon = Icons.directions_walk_rounded;
        break;
      case AssessmentTaskType.composite:
        taskIcon = Icons.auto_awesome_rounded;
        break;
    }

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppTheme.surfaceBorder),
          boxShadow: AppTheme.softShadow,
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppTheme.surfaceSubtle,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(taskIcon, color: AppTheme.primary, size: 20),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${result.taskType.name.toUpperCase()} TEST',
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    timeStr,
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppTheme.textMuted,
                    ),
                  ),
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                StatusChip(state: result.state),
                const SizedBox(height: 4),
                Text(
                  'Sev: ${result.severity.toStringAsFixed(1)}',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: AppTheme.textSecondary,
                  ),
                ),
              ],
            ),
            const SizedBox(width: 8),
            const Icon(Icons.arrow_forward_ios_rounded, size: 14, color: AppTheme.textMuted),
          ],
        ),
      ),
    );
  }
}
