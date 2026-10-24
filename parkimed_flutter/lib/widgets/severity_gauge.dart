import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../core/theme/app_theme.dart';

class SeverityGauge extends StatelessWidget {
  final double severity; // 0.0 to 5.0
  final double size;

  const SeverityGauge({
    super.key,
    required this.severity,
    this.size = 180,
  });

  @override
  Widget build(BuildContext context) {
    final clamped = severity.clamp(0.0, 5.0);
    final ratio = clamped / 5.0;

    Color gaugeColor;
    if (clamped < 1.5) {
      gaugeColor = AppTheme.stateOn;
    } else if (clamped < 3.0) {
      gaugeColor = AppTheme.stateModerate;
    } else {
      gaugeColor = AppTheme.stateOff;
    }

    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(
            size: Size(size, size),
            painter: _GaugePainter(
              progress: ratio,
              activeColor: gaugeColor,
              backgroundColor: AppTheme.surfaceSubtle,
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                severity.toStringAsFixed(1),
                style: TextStyle(
                  fontSize: size * 0.26,
                  fontWeight: FontWeight.w800,
                  color: AppTheme.textPrimary,
                  height: 1.0,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'OUT OF 5.0',
                style: TextStyle(
                  fontSize: size * 0.07,
                  fontWeight: FontWeight.w700,
                  color: AppTheme.textMuted,
                  letterSpacing: 1.0,
                ),
              ),
              const SizedBox(height: 2),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: gaugeColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  clamped < 1.5
                      ? 'Mild'
                      : clamped < 3.0
                          ? 'Moderate'
                          : 'Severe',
                  style: TextStyle(
                    fontSize: size * 0.07,
                    fontWeight: FontWeight.w700,
                    color: gaugeColor,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _GaugePainter extends CustomPainter {
  final double progress; // 0.0 to 1.0
  final Color activeColor;
  final Color backgroundColor;

  _GaugePainter({
    required this.progress,
    required this.activeColor,
    required this.backgroundColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (size.width - 24) / 2;
    const startAngle = 135 * (math.pi / 180);
    const sweepAngle = 270 * (math.pi / 180);

    final bgPaint = Paint()
      ..color = backgroundColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = 14
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      startAngle,
      sweepAngle,
      false,
      bgPaint,
    );

    final activePaint = Paint()
      ..color = activeColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = 14
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      startAngle,
      sweepAngle * progress,
      false,
      activePaint,
    );
  }

  @override
  bool shouldRepaint(covariant _GaugePainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.activeColor != activeColor;
  }
}
