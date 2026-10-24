import 'package:flutter/foundation.dart';
import '../core/model/parkimed_model.dart';
import 'model_service.dart';

class AssessmentService {
  static final AssessmentService instance = AssessmentService._();
  AssessmentService._();

  Future<Prediction> runVoiceLive(Float32List audio, int sampleRate) async {
    await ModelService.instance.init();
    return ModelService.instance.predictVoice(audio, sampleRate);
  }

  Future<Prediction> runVoiceDemoSevere() async {
    await ModelService.instance.init();
    return ModelService.instance.predictVoiceDemoSevere();
  }

  Future<Prediction> runVoiceDemoHealthy() async {
    await ModelService.instance.init();
    return ModelService.instance.predictVoiceDemoHealthy();
  }

  Future<Prediction> runTapLive(List<int> intervalsMs, List<int> durationsMs) async {
    await ModelService.instance.init();
    return ModelService.instance.predictTap(intervalsMs, durationsMs);
  }

  Future<Prediction> runTapDemo() async {
    await ModelService.instance.init();
    return ModelService.instance.predictTapDemo();
  }

  Future<Prediction> runSpiral(List<Offset> points) async {
    await ModelService.instance.init();
    return ModelService.instance.predictSpiral(points);
  }

  Future<Prediction> runGaitDemo() async {
    await ModelService.instance.init();
    return ModelService.instance.predictWalkDemo();
  }

  Future<Prediction> runMotion(List<Float32List> windows) async {
    await ModelService.instance.init();
    return ModelService.instance.predictMotion(windows);
  }
}
