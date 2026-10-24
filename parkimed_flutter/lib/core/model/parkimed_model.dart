import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:tflite_flutter/tflite_flutter.dart';

class Prediction {
  final String onOff;
  final double severity; // 0..5
  final double confidence; // 0..1
  final double pOn; // 0..1
  Prediction({
    required this.onOff,
    required this.severity,
    required this.confidence,
    required this.pOn,
  });
}

class ParkiMedModel {
  Interpreter? _interpreter;
  bool get loaded => _interpreter != null;

  Future<void> load() async {
    if (_interpreter != null) return;
    try {
      final modelData = await rootBundle.load('assets/parkimed_model.tflite');
      final bytes = modelData.buffer.asUint8List(
        modelData.offsetInBytes,
        modelData.lengthInBytes,
      );
      _interpreter = Interpreter.fromBuffer(bytes);
      _interpreter!.allocateTensors();
    } catch (e) {
      rethrow;
    }
  }

  void close() {
    _interpreter?.close();
    _interpreter = null;
  }

  int _findInputIndexByName(String key) {
    final interp = _interpreter!;
    final inputs = interp.getInputTensors();
    for (var i = 0; i < inputs.length; i++) {
      final name = inputs[i].name.toLowerCase();
      if (name.contains(key.toLowerCase())) return i;
    }
    // fallback by width
    if (key == 'voice') {
      for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].shape.contains(39)) return i;
      }
    } else if (key == 'motion') {
      for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].shape.contains(36)) return i;
      }
    } else if (key == 'tap') {
      for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].shape.contains(20)) return i;
      }
    } else if (key == 'spiral') {
      for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].shape.contains(15)) return i;
      }
    } else if (key == 'visual') {
      for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].shape.contains(20)) return i;
      }
    } else if (key == 'gait') {
      for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].shape.contains(24)) return i;
      }
    }
    return 0;
  }

  Prediction predict({
    Float32List? voice,
    Float32List? motion,
    Float32List? tap,
    Float32List? spiral,
    Float32List? visual,
    Float32List? gait,
  }) {
    final interp = _interpreter;
    if (interp == null) throw Exception('Model not loaded');
    voice ??= Float32List(39);
    motion ??= Float32List(36);
    tap ??= Float32List(20);
    spiral ??= Float32List(15);
    visual ??= Float32List(20);
    gait ??= Float32List(24);

    // Sanitize
    Float32List _san(Float32List a) {
      final b = Float32List(a.length);
      for (var i = 0; i < a.length; i++) {
        final v = a[i];
        if (v.isNaN || v.isInfinite) {
          b[i] = 0.0;
        } else {
          b[i] = v;
        }
      }
      return b;
    }

    voice = _san(voice);
    motion = _san(motion);
    tap = _san(tap);
    spiral = _san(spiral);
    visual = _san(visual);
    gait = _san(gait);

    final inputsMap = <int, Object>{};
    inputsMap[_findInputIndexByName('voice')] = [voice];
    inputsMap[_findInputIndexByName('motion')] = [motion];
    inputsMap[_findInputIndexByName('tap')] = [tap];
    inputsMap[_findInputIndexByName('spiral')] = [spiral];
    inputsMap[_findInputIndexByName('visual')] = [visual];
    inputsMap[_findInputIndexByName('gait')] = [gait];

    // Prepare outputs
    final outputs = <int, Object>{};
    final outputTensors = interp.getOutputTensors();
    for (var i = 0; i < outputTensors.length; i++) {
      final shape = outputTensors[i].shape;
      if (shape.length == 2 && shape[1] == 2) {
        outputs[i] = List.filled(2, 0.0).reshape([1, 2]);
      } else if (shape.length == 2 && shape[1] == 1) {
        outputs[i] = List.filled(1, 0.0).reshape([1, 1]);
      } else {
        outputs[i] = List.filled(shape.reduce((a, b) => a * b), 0.0).reshape(shape);
      }
    }

    interp.runForMultipleInputs(inputsMap, outputs);

    double pOff = 0.5;
    double severity01 = 0.0;
    // Find outputs
    for (var i = 0; i < outputTensors.length; i++) {
      final shape = outputTensors[i].shape;
      final out = outputs[i];
      if (shape.length == 2 && shape[1] == 2) {
        final arr = out as List;
        final row = arr[0] as List;
        pOff = (row[0] as num).toDouble();
      } else if (shape.length == 2 && shape[1] == 1) {
        final arr = out as List;
        final row = arr[0] as List;
        severity01 = (row[0] as num).toDouble();
      }
    }
    final pOn = 1.0 - pOff;
    final onOff = pOn >= 0.5 ? 'ON' : 'OFF';
    final confidence = pOn >= 0.5 ? pOn : pOff;
    final severity = (severity01 * 5.0).clamp(0.0, 5.0);
    return Prediction(onOff: onOff, severity: severity, confidence: confidence, pOn: pOn);
  }
}
