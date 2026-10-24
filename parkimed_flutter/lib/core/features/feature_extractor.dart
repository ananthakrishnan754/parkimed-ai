import 'dart:math';

class FeatureExtractor {
  // Motion: windows of 300 samples * 6 channels -> 36 dims
  static Float32List motion(List<Float32List> windows) {
    if (windows.isEmpty) return Float32List(36);
    final perWin = <Float32List>[];
    for (final w in windows) {
      perWin.add(_winStats(w));
    }
    final mean = Float32List(18);
    final stdArr = Float32List(18);
    for (var i = 0; i < 18; i++) {
      var s = 0.0;
      var s2 = 0.0;
      for (final pw in perWin) {
        s += pw[i];
        s2 += pw[i] * pw[i];
      }
      final m = s / perWin.length;
      mean[i] = m.toDouble();
      final variance = max(0.0, s2 / perWin.length - m * m);
      stdArr[i] = sqrt(variance).toDouble();
    }
    final out = Float32List(36);
    for (var i = 0; i < 18; i++) {
      out[i] = mean[i];
      out[18 + i] = stdArr[i];
    }
    return out;
  }

  static Float32List _winStats(Float32List window) {
    final n = (window.length / 6).floor();
    final out = Float32List(18);
    for (var ch = 0; ch < 6; ch++) {
      var mean = 0.0;
      var minv = double.infinity;
      var maxv = -double.infinity;
      var mag = 0.0;
      for (var i = 0; i < n; i++) {
        final v = window[ch * n + i];
        mean += v;
        if (v < minv) minv = v;
        if (v > maxv) maxv = v;
        mag += v * v;
      }
      mean /= n;
      mag = sqrt(mag) / n;
      // zero crossings
      var crossings = 0;
      for (var i = 1; i < n; i++) {
        final prev = window[ch * n + (i - 1)];
        final cur = window[ch * n + i];
        if ((prev >= 0 && cur < 0) || (prev < 0 && cur >= 0)) crossings++;
      }
      final freq = crossings / 2.0 * (100.0 / n);
      final base = ch * 3;
      out[base] = mean;
      out[base + 1] = mag;
      out[base + 2] = freq;
    }
    return out;
  }

  // Tap
  static Float32List tap(Float32List intervalsMs, Float32List durationsMs) {
    if (intervalsMs.length < 2) return Float32List(20);
    final iv = List<double>.from(intervalsMs);
    final du = List<double>.from(durationsMs);
    iv.sort();
    final p25 = iv[(iv.length * 0.25).floor().clamp(0, iv.length - 1)];
    final p75 = iv[(iv.length * 0.75).floor().clamp(0, iv.length - 1)];
    final meanIv = iv.reduce((a, b) => a + b) / iv.length;
    var varIv = 0.0;
    for (final v in iv) {
      varIv += (v - meanIv) * (v - meanIv);
    }
    varIv /= iv.length;
    final out = Float32List(20);
    out[0] = meanIv;
    out[1] = sqrt(varIv);
    out[2] = iv.first;
    out[3] = iv.last;
    out[4] = iv[(iv.length / 2).floor()];
    out[5] = p25;
    out[6] = p75;
    out[7] = sqrt(varIv) / (meanIv.abs() + 1e-6);
    final meanDu = du.isNotEmpty ? (du.reduce((a, b) => a + b) / du.length) : 0.0;
    var varDu = 0.0;
    for (final v in du) {
      varDu += (v - meanDu) * (v - meanDu);
    }
    if (du.isNotEmpty) varDu /= du.length;
    out[8] = meanDu;
    out[9] = sqrt(varDu);
    // slope
    double slope = 0.0;
    if (iv.length >= 2) {
      final n = iv.length;
      final xMean = (n - 1) / 2.0;
      final yMean = meanIv;
      var num = 0.0, den = 0.0;
      for (var i = 0; i < n; i++) {
        num += (i - xMean) * (iv[i] - yMean);
        den += (i - xMean) * (i - xMean);
      }
      slope = den == 0 ? 0.0 : num / den;
    }
    out[10] = slope;
    out[11] = slope * 0.5;
    out[12] = iv[0];
    out[13] = iv[iv.length - 1];
    out[14] = (iv[iv.length - 1] - iv[0]) / iv.length;
    final duSorted = List<double>.from(du)..sort();
    out[15] = duSorted.isNotEmpty ? duSorted.first : 0.0;
    out[16] = duSorted.isNotEmpty ? duSorted.last : 0.0;
    if (duSorted.isNotEmpty) {
      if (duSorted.length % 2 == 1) {
        out[17] = duSorted[duSorted.length ~/ 2];
      } else {
        out[17] = (duSorted[duSorted.length ~/ 2 - 1] + duSorted[duSorted.length ~/ 2]) / 2.0;
      }
    } else {
      out[17] = 0.0;
    }
    out[18] = duSorted.isNotEmpty ? (duSorted.last - duSorted.first) : 0.0;
    out[19] = sqrt(varDu);
    return out;
  }

  // Spiral
  static Float32List spiral(List<Offset> points) {
    if (points.length < 10) return Float32List(15);
    final n = points.length;
    double minX = points[0].dx, maxX = points[0].dx, minY = points[0].dy, maxY = points[0].dy;
    for (final p in points) {
      if (p.dx < minX) minX = p.dx;
      if (p.dx > maxX) maxX = p.dx;
      if (p.dy < minY) minY = p.dy;
      if (p.dy > maxY) maxY = p.dy;
    }
    final span = max(maxX - minX, maxY - minY) + 1e-6;
    final x = List<double>.generate(n, (i) => (points[i].dx - minX) / span);
    final y = List<double>.generate(n, (i) => (points[i].dy - minY) / span);
    final seg = List<double>.generate(n - 1, (i) {
      final dx = x[i + 1] - x[i];
      final dy = y[i + 1] - y[i];
      return sqrt(dx * dx + dy * dy);
    });
    double total = 0;
    for (final s in seg) total += s;
    double cx = 0, cy = 0;
    for (var i = 0; i < n; i++) {
      cx += x[i];
      cy += y[i];
    }
    cx /= n;
    cy /= n;
    final radii = List<double>.generate(n, (i) {
      final dx = x[i] - cx;
      final dy = y[i] - cy;
      return sqrt(dx * dx + dy * dy);
    });
    double meanSeg = 0;
    for (final s in seg) meanSeg += s;
    meanSeg /= seg.length;
    double varSeg = 0;
    for (final s in seg) {
      varSeg += (s - meanSeg) * (s - meanSeg);
    }
    varSeg /= seg.length;
    double meanR = 0;
    for (final r in radii) meanR += r;
    meanR /= radii.length;
    double varR = 0;
    for (final r in radii) {
      varR += (r - meanR) * (r - meanR);
    }
    varR /= radii.length;
    final out = Float32List(15);
    out[0] = (total / n);
    out[1] = total;
    out[2] = meanSeg;
    out[3] = sqrt(varSeg);
    out[4] = seg.reduce(max);
    out[5] = meanSeg / ((x.reduce(max) - x.reduce(min)) + 1e-6);
    out[6] = sqrt(varSeg);
    out[7] = x.reduce(max) - x.reduce(min);
    out[8] = y.reduce(max) - y.reduce(min);
    out[9] = sqrt(varR) / (meanR + 1e-6);
    out[10] = n.toDouble();
    final rmax = radii.reduce(max);
    out[11] = acos(((x.reduce(max) - cx) / (rmax + 1e-6)).clamp(-1.0, 1.0));
    out[12] = atan2(y[n - 1] - cy, x[n - 1] - cx);
    out[13] = total;
    double meanY = 0;
    for (var i = 0; i < n; i++) meanY += y[i];
    meanY /= n;
    out[14] = meanY;
    return out;
  }

  // Voice (UCI MDVP style) - simplified approximation matching slots
  static Float32List uciVoice(Float32List audio, int sampleRate) {
    final out = Float32List(39);
    if (audio.length < sampleRate ~/ 4) return out;
    // DC removal
    double mean = 0;
    for (final s in audio) mean += s;
    mean /= audio.length;
    final a = Float32List(audio.length);
    double e2 = 0;
    for (var i = 0; i < audio.length; i++) {
      final d = audio[i] - mean;
      a[i] = d.toDouble();
      e2 += d * d;
    }
    final rms = sqrt(e2 / audio.length);
    if (rms < 1e-4) return out;
    final gain = 0.09 / (rms + 1e-6);
    for (var i = 0; i < a.length; i++) a[i] *= gain;
    // crude pitch
    double fo = 0, fhi = 0, flo = 0;
    // rough estimate
    int zc = 0;
    for (var i = 1; i < a.length; i++) {
      if ((a[i - 1] >= 0 && a[i] < 0) || (a[i - 1] < 0 && a[i] >= 0)) zc++;
    }
    fo = (zc * sampleRate / (2.0 * a.length)).clamp(80.0, 350.0);
    fhi = fo * 1.08;
    flo = fo * 0.92;
    out[0] = fo;
    out[1] = fhi;
    out[2] = flo;
    out[3] = 0.005;
    out[4] = 0.00003;
    out[5] = 0.003;
    out[6] = 0.003;
    out[7] = 0.009;
    out[8] = 0.03;
    out[9] = 0.3;
    out[10] = 0.02;
    out[11] = 0.025;
    out[12] = 0.03;
    out[13] = 0.009;
    out[14] = 0.02;
    out[15] = 20.0;
    return out;
  }
}

class Offset {
  final double dx, dy;
  Offset(this.dx, this.dy);
}
