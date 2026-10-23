import 'package:flutter/material.dart';
import '../../core/models/assessment_result.dart';
import '../../core/theme/app_theme.dart';

class StatusChip extends StatelessWidget {
  final MedicationState state;
  final bool isLarge;

  const StatusChip({
    super.key,
    required this.state,
    this.isLarge = false,
  });

  @override
  Widget build(BuildContext context) {
    final isOn = state == MedicationState.on;
    final bgColor = isOn ? AppTheme.stateOnLight : AppTheme.stateOffLight;
    final borderColor = isOn ? AppTheme.stateOnBorder : AppTheme.stateOffBorder;
    final textColor = isOn ? AppTheme.stateOn : AppTheme.stateOff;
    final label = isOn ? 'ON STATE' : 'OFF STATE';
    final icon = isOn ? Icons.check_circle_rounded : Icons.warning_amber_rounded;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: isLarge ? 16 : 10,
        vertical: isLarge ? 8 : 4,
      ),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: borderColor, width: 1.5),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            size: isLarge ? 18 : 14,
            color: textColor,
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              color: textColor,
              fontWeight: FontWeight.w700,
              fontSize: isLarge ? 13 : 11,
              letterSpacing: 0.5,
            ),
          ),
        ],
      ),
    );
  }
}
