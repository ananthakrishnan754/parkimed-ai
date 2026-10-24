import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../core/theme/app_theme.dart';

class AnimatedWave extends StatefulWidget {
  final bool isAnimating;
  final double height;
  final Color? color;

  const AnimatedWave({
    super.key,
    required this.isAnimating,
    this.height = 70,
    this.color,
  });

  @override
  State<AnimatedWave> createState() => _AnimatedWaveState();
}

class _AnimatedWaveState extends State<AnimatedWave>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    if (widget.isAnimating) {
      _controller.repeat();
    }
  }

  @override
  void didUpdateWidget(covariant AnimatedWave oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isAnimating != oldWidget.isAnimating) {
      if (widget.isAnimating) {
        _controller.repeat();
      } else {
        _controller.stop();
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const barCount = 18;
    final waveColor = widget.color ?? AppTheme.primary;

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return SizedBox(
          height: widget.height,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: List.generate(barCount, (index) {
              double scale = 0.2;
              if (widget.isAnimating) {
                final angle =
                    (_controller.value * 2 * math.pi) + (index * 0.35);
                scale = 0.25 + 0.75 * ((math.sin(angle) + 1) / 2);
              }

              return Container(
                margin: const EdgeInsets.symmetric(horizontal: 3),
                width: 4,
                height: widget.height * scale,
                decoration: BoxDecoration(
                  color: waveColor.withValues(alpha: 0.8),
                  borderRadius: BorderRadius.circular(4),
                ),
              );
            }),
          ),
        );
      },
    );
  }
}
