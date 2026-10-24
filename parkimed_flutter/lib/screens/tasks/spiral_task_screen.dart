import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../cubits/assessment/assessment_cubit.dart';
import '../../cubits/assessment/assessment_state.dart';
import '../../cubits/history/history_cubit.dart';
import '../../core/theme/app_theme.dart';
import '../../core/features/feature_extractor.dart' show Offset;
import '../results/results_screen.dart';

class SpiralTaskScreen extends StatefulWidget {
  const SpiralTaskScreen({super.key});

  @override
  State<SpiralTaskScreen> createState() => _SpiralTaskScreenState();
}

class _SpiralTaskScreenState extends State<SpiralTaskScreen> {
  final List<Offset?> _points = [];
  final ValueNotifier<int> _repaintNotifier = ValueNotifier<int>(0);
  bool _isEvaluating = false;

  @override
  void dispose() {
    _repaintNotifier.dispose();
    super.dispose();
  }

  void _clearCanvas() {
    setState(() {
      _points.clear();
    });
    _repaintNotifier.value++;
  }

  void _addPoint(Offset? point) {
    if (point == null && (_points.isEmpty || _points.last == null)) {
      return;
    }
    final wasEmpty = _points.isEmpty;
    _points.add(point);
    if (wasEmpty && point != null) {
      setState(() {});
    }
    _repaintNotifier.value++;
  }

  void _evaluateDrawing(BuildContext context) {
    final validPoints = _points.whereType<Offset>().toList();
    if (validPoints.length < 30) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please draw more of the spiral from center outwards')),
      );
      return;
    }

    setState(() {
      _isEvaluating = true;
    });

    // Calculate smoothness and tremor from the touch points
    double totalJitter = 0.0;
    int jitterCount = 0;
    for (int i = 2; i < _points.length; i++) {
      final p0 = _points[i - 2];
      final p1 = _points[i - 1];
      final p2 = _points[i];
      if (p0 == null || p1 == null || p2 == null) continue;

      // Second derivative (acceleration/jitter)
      final dx1 = p1.dx - p0.dx;
      final dy1 = p1.dy - p0.dy;
      final dx2 = p2.dx - p1.dx;
      final dy2 = p2.dy - p1.dy;

      final jerk = math.sqrt(math.pow(dx2 - dx1, 2) + math.pow(dy2 - dy1, 2));
      totalJitter += jerk;
      jitterCount++;
    }

    final avgJitter = jitterCount > 0 ? (totalJitter / jitterCount) : 0.0;
    // Smooth drawing has low avgJitter (< 3.0), tremulous drawing has high jitter (> 7.0)
    final smoothnessScore = (1.0 - (avgJitter / 12.0)).clamp(0.2, 0.96);
    final tremorDeviation = (avgJitter / 10.0).clamp(0.1, 0.95);

    final pts = validPoints.map((e) => Offset(e.dx, e.dy)).toList();
    context.read<AssessmentCubit>().completeSpiralAssessment(
          smoothnessScore: smoothnessScore,
          tremorDeviation: tremorDeviation,
          points: pts,
        );
  }

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
        final hasPoints = _points.whereType<Offset>().isNotEmpty;

        return Scaffold(
          appBar: AppBar(
            title: const Text('Spiral Drawing Test'),
            leading: IconButton(
              icon: const Icon(Icons.arrow_back_ios_new_rounded),
              onPressed: () {
                context.read<AssessmentCubit>().reset();
                Navigator.pop(context);
              },
            ),
            actions: [
              IconButton(
                icon: const Icon(Icons.refresh_rounded),
                tooltip: 'Clear Canvas',
                onPressed: _clearCanvas,
              ),
            ],
          ),
          body: SafeArea(
            child: Column(
              children: [
                // Instruction Banner
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                  child: Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryLight,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppTheme.primaryMuted),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.gesture_rounded, color: AppTheme.primary, size: 24),
                        SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            'Trace the spiral starting from the center red dot outward without resting your wrist.',
                            style: TextStyle(
                              fontSize: 13,
                              color: AppTheme.textSecondary,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                // Interactive Drawing Canvas
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(24),
                        border: Border.all(color: AppTheme.surfaceBorder, width: 2),
                        boxShadow: AppTheme.cardShadow,
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(22),
                        child: GestureDetector(
                          behavior: HitTestBehavior.opaque,
                          onPanStart: (details) {
                            _addPoint(details.localPosition);
                          },
                          onPanUpdate: (details) {
                            _addPoint(details.localPosition);
                          },
                          onPanEnd: (_) {
                            _addPoint(null);
                          },
                          onPanCancel: () {
                            _addPoint(null);
                          },
                          child: RepaintBoundary(
                            child: CustomPaint(
                              size: Size.infinite,
                              painter: _SpiralCanvasPainter(
                                points: _points,
                                repaint: _repaintNotifier,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),

                // Bottom Evaluation Bar
                Padding(
                  padding: const EdgeInsets.all(20.0),
                  child: Row(
                    children: [
                      OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        ),
                        icon: const Icon(Icons.delete_outline_rounded),
                        label: const Text('Reset'),
                        onPressed: hasPoints ? _clearCanvas : null,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton.icon(
                          icon: _isEvaluating
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                                )
                              : const Icon(Icons.analytics_outlined),
                          label: Text(_isEvaluating ? 'Analyzing...' : 'Analyze Spiral'),
                          onPressed: !hasPoints || _isEvaluating
                              ? null
                              : () => _evaluateDrawing(context),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _SpiralCanvasPainter extends CustomPainter {
  final List<Offset?> points;

  _SpiralCanvasPainter({
    required this.points,
    super.repaint,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);

    // Draw Guide Archimedean Spiral in faint dash
    final guidePaint = Paint()
      ..color = AppTheme.surfaceBorder
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0;

    final path = Path();
    const loops = 4.0;
    const maxTheta = loops * 2 * math.pi;
    final b = (size.width * 0.42) / maxTheta;

    for (double theta = 0; theta <= maxTheta; theta += 0.05) {
      final r = b * theta;
      final x = center.dx + r * math.cos(theta);
      final y = center.dy + r * math.sin(theta);
      if (theta == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    canvas.drawPath(path, guidePaint);

    // Center starting dot
    final centerDotPaint = Paint()..color = AppTheme.stateOff;
    canvas.drawCircle(center, 7, centerDotPaint);

    // User drawn stroke
    if (points.isNotEmpty) {
      final userPaint = Paint()
        ..color = AppTheme.primary
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..strokeWidth = 4.0
        ..style = PaintingStyle.stroke;

      final dotPaint = Paint()
        ..color = AppTheme.primary
        ..style = PaintingStyle.fill;

      final userPath = Path();
      bool inStroke = false;

      for (int i = 0; i < points.length; i++) {
        final pt = points[i];
        if (pt != null) {
          if (!inStroke) {
            userPath.moveTo(pt.dx, pt.dy);
            canvas.drawCircle(pt, 2.0, dotPaint);
            inStroke = true;
          } else {
            userPath.lineTo(pt.dx, pt.dy);
          }
        } else {
          inStroke = false;
        }
      }
      canvas.drawPath(userPath, userPaint);
    }
  }

  @override
  bool shouldRepaint(covariant _SpiralCanvasPainter oldDelegate) => true;
}
