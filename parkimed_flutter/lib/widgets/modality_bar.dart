import 'package:flutter/material.dart';
import '../../core/theme/app_theme.dart';

class ModalityBar extends StatelessWidget {
  final String label;
  final double score; // 0.0 to 1.0
  final IconData icon;

  const ModalityBar({
    super.key,
    required this.label,
    required this.score,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final percentage = (score * 100).toInt();
    final isGood = score >= 0.70;
    final color = isGood ? AppTheme.stateOn : AppTheme.stateOff;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 16, color: AppTheme.textSecondary),
              const SizedBox(width: 8),
              Text(
                label,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.textPrimary,
                ),
              ),
              const Spacer(),
              Text(
                '$percentage%',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: color,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: LinearProgressIndicator(
              value: score.clamp(0.0, 1.0),
              minHeight: 8,
              backgroundColor: AppTheme.surfaceSubtle,
              valueColor: AlwaysStoppedAnimation<Color>(color),
            ),
          ),
        ],
      ),
    );
  }
}
