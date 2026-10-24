import 'package:flutter/foundation.dart';
import '../core/model/parkimed_model.dart';
import '../core/features/feature_extractor.dart';
import 'dart:typed_data';

class ModelService {
  static final ModelService instance = ModelService._();
  ModelService._();
  final ParkiMedModel model = ParkiMedModel();
  bool ready = false;

  Future<void> init() async {
    if (ready) return;
    await model.load();
    ready = true;
  }

  Prediction predictVoice(Float32List audio, int sampleRate) {
    final voice = FeatureExtractor.uciVoice(audio, sampleRate);
    return model.predict(voice: voice);
  }

  Prediction predictTap(List<int> intervalsMs, List<int> durationsMs) {
    final iv = Float32List.fromList(intervalsMs.map((e) => e.toDouble()).toList());
    final du = Float32List.fromList(durationsMs.map((e) => e.toDouble()).toList());
    final tapFeat = FeatureExtractor.tap(iv, du);
    // For live tap, motion/tap heads gated to 0 in model; still run with tap features
    // but per original behavior result will be neutral unless other modalities present
    return model.predict(tap: tapFeat);
  }

  Prediction predictSpiral(List<Offset> points) {
    final spiralFeat = FeatureExtractor.spiral(points);
    return model.predict(spiral: spiralFeat);
  }

  Prediction predictMotion(List<Float32List> windows) {
    final motionFeat = FeatureExtractor.motion(windows);
    return model.predict(motion: motionFeat);
  }

  // Demo helpers matching original demos
  Prediction predictVoiceDemoHealthy() {
    final v = Float32List(39);
    // Fill first 16 like healthy demo
    v[0] = 119.992;
    v[1] = 157.302;
    v[2] = 74.997;
    v[3] = 0.00784;
    v[4] = 0.00007;
    v[5] = 0.0037;
    v[6] = 0.00554;
    v[7] = 0.01109;
    v[8] = 0.04374;
    v[9] = 0.426;
    v[10] = 0.02182;
    v[11] = 0.0313;
    v[12] = 0.02971;
    v[13] = 0.06545;
    v[14] = 0.02211;
    v[15] = 21.033;
    return model.predict(voice: v);
  }

  Prediction predictVoiceDemoSevere() {
    final v = Float32List(39);
    v[0] = 197.076;
    v[1] = 206.896;
    v[2] = 192.055;
    v[3] = 0.00896;
    v[4] = 0.00008;
    v[5] = 0.00468;
    v[6] = 0.00621;
    v[7] = 0.01403;
    v[8] = 0.07613;
    v[9] = 0.613;
    v[10] = 0.0412;
    v[11] = 0.04611;
    v[12] = 0.06062;
    v[13] = 0.1236;
    v[14] = 0.03271;
    v[15] = 12.695;
    return model.predict(voice: v);
  }

  Prediction predictTapDemo() {
    final visual = Float32List(20);
    // approximate ROC-HCI demo vector values (first few)
    visual[0] = 0.159;
    visual[1] = 0.0662;
    visual[2] = 0.0735;
    visual[3] = 0.1008;
    visual[4] = 0.1761;
    visual[5] = 0.1367;
    visual[6] = -0.0614;
    visual[7] = 0.0591;
    visual[8] = 0.2164;
    visual[9] = -0.0821;
    visual[10] = -0.1119;
    visual[11] = 0.0213;
    visual[12] = 0.0906;
    visual[13] = -0.1628;
    visual[14] = 0.2134;
    visual[15] = 0.0581;
    visual[16] = -0.0127;
    visual[17] = 0.1417;
    visual[18] = -0.065;
    visual[19] = 0.1422;
    return model.predict(visual: visual);
  }

  Prediction predictWalkDemo() {
    final spiralFeat = Float32List(15);
    // PD spiral demo approx
    spiralFeat[0] = 0.0235;
    spiralFeat[1] = 2.82;
    spiralFeat[2] = 0.0142;
    spiralFeat[3] = 0.0068;
    spiralFeat[4] = 0.038;
    spiralFeat[5] = 0.051;
    spiralFeat[6] = 0.0068;
    spiralFeat[7] = 0.82;
    spiralFeat[8] = 0.78;
    spiralFeat[9] = 0.42;
    spiralFeat[10] = 185;
    spiralFeat[11] = 1.2;
    spiralFeat[12] = -1.8;
    spiralFeat[13] = 2.82;
    spiralFeat[14] = 0.48;
    final gaitFeat = Float32List(24);
    // fill with some values from REMAP pattern
    for (var i = 0; i < 24; i++) gaitFeat[i] = (i % 5 == 0) ? -0.5 : 0.1;
    return model.predict(spiral: spiralFeat, gait: gaitFeat);
  }
}
