package com.parkimed.ai.features

import kotlin.math.*

/**
 * Feature extraction on-device. Dimensions MUST match the Python side
 * (config.py): voice=39, motion=36, tap=20, spiral=15.
 *
 * Voice note: full MFCC extraction would require librosa (not available on
 * Android). The skeleton computes pitch/jitter/shimmer proxy features and
 * pads into 39 dims. For the final build, either:
 *   a) train voice expert on the same proxy features, or
 *   b) run MFCC via a small C/C++ lib ported with NDK.
 */
object FeatureExtractor {

    // ------------------------------------------------------------------
    // Motion : (window, 6ch) -> 36 dims  (mean/std of per-window stats)
    // ------------------------------------------------------------------
    fun motion(windows: List<FloatArray>): FloatArray {
        // windows: already de-gravity windows of size 300 with 6 channels
        // (accel xyz + gyro xyz). Per-window stats -> 18 dims.
        val perWin = windows.map { winStats(it) }       // List<FloatArray>(18)
        if (perWin.isEmpty()) return FloatArray(36)
        val mean = FloatArray(18)
        val std = FloatArray(18)
        for (i in 0 until 18) {
            var s = 0.0; var s2 = 0.0
            for (w in perWin) { s += w[i]; s2 += w[i] * w[i] }
            val m = s / perWin.size
            mean[i] = m.toFloat()
            std[i] = sqrt(max(0.0, s2 / perWin.size - m * m)).toFloat()
        }
        return mean + std
    }

    private fun winStats(window: FloatArray): FloatArray {
        // window length 300, values interleaved x,y,z,gx,gy,gz → channel-major
        val n = window.size / 6
        val out = FloatArray(18)
        for (ch in 0 until 6) {
            var mean = 0.0; var minv = Double.MAX_VALUE; var maxv = -Double.MAX_VALUE
            var mag = 0.0
            for (i in 0 until n) {
                val v = window[ch * n + i].toDouble()
                mean += v; minv = min(minv, v); maxv = max(maxv, v); mag += v * v
            }
            mean /= n
            mag = sqrt(mag) / n
            // dominant frequency via zero-crossing proxy
            var crossings = 0
            for (i in 1 until n) {
                if (sign(window[ch * n + i].toDouble()) != sign(window[ch * n + i - 1].toDouble())) crossings++
            }
            val freq = crossings / 2.0 * (100.0 / n)   // 100Hz sampling
            val base = ch * 3
            out[base] = mean.toFloat()
            out[base + 1] = mag.toFloat()
            out[base + 2] = freq.toFloat()
        }
        return out
    }

    // ------------------------------------------------------------------
    // Tap : intervals/duractions ms -> 20 dims
    // ------------------------------------------------------------------
    fun tap(intervalsMs: FloatArray, durationsMs: FloatArray): FloatArray {
        if (intervalsMs.size < 2) return FloatArray(20)
        val iv = intervalsMs
        val du = durationsMs
        val sortedIv = iv.sortedArray()
        val p25 = sortedIv[(sortedIv.size * 0.25).toInt().coerceAtMost(sortedIv.size - 1)]
        val p75 = sortedIv[(sortedIv.size * 0.75).toInt().coerceAtMost(sortedIv.size - 1)]
        val meanIv = iv.average().toFloat()
        val varIv = iv.map { (it - meanIv) * (it - meanIv) }.average().toFloat()
        val out = FloatArray(20)
        out[0] = meanIv
        out[1] = sqrt(varIv)
        out[2] = iv.min(); out[3] = iv.max()
        out[4] = sortedIv[sortedIv.size / 2]
        out[5] = p25; out[6] = p75
        out[7] = sqrt(varIv) / (abs(meanIv) + 1e-6f)
        out[8] = du.average().toFloat()
        out[9] = du.map { (it - du.average()) * (it - du.average()) }.average().toFloat() * 0.0f +
                (du.stddev())
        out[10] = linearSlope(iv)
        out[11] = linearSlope(iv) * 0.5f
        out[12] = iv[0]; out[13] = iv[iv.size - 1]
        out[14] = (iv[iv.size - 1] - iv[0]) / iv.size
        out[15] = du.min(); out[16] = du.max(); out[17] = dulMedian(du)
        out[18] = abs(du.max() - du.min()); out[19] = du.stddev()
        return out
    }

    private fun linearSlope(y: FloatArray): Float {
        if (y.size < 2) return 0f
        val n = y.size
        val xMean = (n - 1) / 2f
        val yMean = y.average().toFloat()
        var num = 0f; var den = 0f
        for (i in 0 until n) {
            num += (i - xMean) * (y[i] - yMean)
            den += (i - xMean) * (i - xMean)
        }
        return if (den == 0f) 0f else num / den
    }

    private fun dulMedian(a: FloatArray): Float {
        val s = a.sortedArray()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    private fun FloatArray.stddev(): Float {
        if (isEmpty()) return 0f
        val m = average().toFloat()
        return sqrt(map { (it - m) * (it - m) }.average()).toFloat()
    }

    // ------------------------------------------------------------------
    // Spiral : unit-normalized path points -> 15 dims (mirrors the UCI-tablet
    //          training features in src/models/build_spiral_data.py)
    // ------------------------------------------------------------------
    fun spiral(pathX: FloatArray, pathY: FloatArray): FloatArray {
        if (pathX.size < 10) return FloatArray(15)
        val n = pathX.size
        val minX = pathX.min(); val minY = pathY.min()
        val span = max(pathX.max() - minX, pathY.max() - minY) + 1e-6f
        val x = FloatArray(n) { (pathX[it] - minX) / span }
        val y = FloatArray(n) { (pathY[it] - minY) / span }
        val seg = FloatArray(n - 1) { i -> hypot(x[i + 1] - x[i], y[i + 1] - y[i]) }
        val total = seg.sum()
        val cx = x.average().toFloat(); val cy = y.average().toFloat()
        val radii = FloatArray(n) { i -> hypot(x[i] - cx, y[i] - cy) }
        val out = FloatArray(15)
        out[0] = total / n
        out[1] = total
        out[2] = seg.average().toFloat(); out[3] = seg.stddev(); out[4] = seg.max()
        out[5] = seg.average().toFloat() / (x.max() - x.min() + 1e-6f)
        out[6] = seg.stddev()
        out[7] = x.max() - x.min(); out[8] = y.max() - y.min()
        out[9] = radii.stddev() / (radii.average().toFloat() + 1e-6f)
        out[10] = n.toFloat()
        out[11] = acos(maxOf(-1.0, minOf(1.0, (x.max() - cx).toDouble() / (radii.max() + 1e-6f).toDouble()))).toFloat()
        out[12] = atan2(y[n - 1] - cy, x[n - 1] - cx).toFloat()
        out[13] = total
        out[14] = y.average().toFloat()
        return out
    }

    // ------------------------------------------------------------------
    // Voice : pitch/jitter/shimmer proxy -> 39 dims
    // ------------------------------------------------------------------
    fun voice(audio: FloatArray, sampleRate: Int): FloatArray {
        if (audio.size < sampleRate / 10) return FloatArray(39)
        val (pitch, jitter, shimmer) = voiceProxy(audio, sampleRate)
        val out = FloatArray(39)
        out[0] = pitch / 300f          // normalized
        out[1] = jitter
        out[2] = shimmer
        // energy envelope as a rough MFCC substitute (bands of RMS)
        val band = audio.size / 32
        for (i in 0 until 31) {
            val seg = audio.copyOfRange(i * band, (i + 1) * band)
            out[3 + i] = sqrt(seg.map { it.toDouble() * it }.average().toFloat() + 1e-9f)
        }
        out[34] = audio.average().toFloat()
        return out
    }

    // ------------------------------------------------------------------
    // Voice (Model v2 / UCI): real MDVP features on slots 0..15, zeros after.
    // Units MUST match data/raw/uci_speech.csv (Praat sustained-phonation):
    //   Fo/Fhi/Flo Hz | Jitter% fraction*100 | Jitter(Abs) seconds |
    //   RAP/PPQ/DDP relative | Shimmer fraction | Shimmer(dB) dB |
    //   APQ3/APQ5/APQ/DDA relative | NHR fraction | HNR dB
    // ------------------------------------------------------------------
    fun uciVoice(audio: FloatArray, sampleRate: Int): FloatArray {
        val out = FloatArray(39)
        if (audio.size < sampleRate / 4) return out          // need ~0.25s
        // DC removal + loudness normalization (robust to loud/clipped captures:
        // features are amplitude ratios, so this only steadies the autocorrelation)
        var mean = 0.0
        for (s in audio) mean += s
        mean /= audio.size
        var e = 0.0
        val a = FloatArray(audio.size)
        for (i in audio.indices) {
            val d = audio[i] - mean
            a[i] = d.toFloat()
            e += d * d
        }
        val rms = sqrt((e / audio.size).toFloat())
        if (rms < 1e-4f) return out                           // silent
        val gain = 0.09f / (rms + 1e-6f)
        for (i in a.indices) a[i] *= gain
        val vfe = VoiceFeatures.compute(a, sampleRate)
        if (!vfe.valid) return out
        out[0] = vfe.fo; out[1] = vfe.fhi; out[2] = vfe.flo
        out[3] = vfe.jitterFrac; out[4] = vfe.jitterAbs; out[5] = vfe.rap
        out[6] = vfe.ppq; out[7] = vfe.ddp
        out[8] = vfe.shimmer; out[9] = vfe.shimmerDb; out[10] = vfe.apq3
        out[11] = vfe.apq5; out[12] = vfe.apq; out[13] = vfe.dda
        out[14] = vfe.nhr; out[15] = vfe.hnr
        return out
    }

    /** Period-domain MDVP extraction approximating Praat on sustained /a/. */
    private object VoiceFeatures {
        const val MIN_LAG = 32      // 16000/500Hz
        const val MAX_LAG = 200     // 16000/80Hz
        const val FRAME = 512
        const val HOP = 160

        data class Frame(val period: Int, val amp: Float, val rmax: Float)

        fun compute(audio: FloatArray, sr: Int): Result {
            if (sr != 16000) return Result()                 // designed for 16 kHz
            val frames = mutableListOf<Frame>()
            var i = 0
            while (i + FRAME <= audio.size) {
                val rms = frameRms(audio, i)
                if (rms > 1e-4f) {
                    val (lag, rmax) = bestLag(audio, i)
                    if (lag > 0 && rmax >= 0.30f) {
                        val a = frameAmp(audio, i)
                        frames.add(Frame(lag, a, rmax))
                    }
                }
                i += HOP
            }
            if (frames.size < 8) return Result()
            val periods = frames.map { it.period.toFloat() }
            val amps = frames.map { it.amp.toDouble() }

            val fo = periods.map { sr / it }.average().toFloat()
            val fhi = periods.map { sr / it }.max().toFloat()
            val flo = periods.map { sr / it }.min().toFloat()
            val tMean = periods.average().toFloat()

            val local = periods.zipWithNext { a, b -> abs(b - a) }.average().toFloat()
            val jitterFrac = local / (tMean + 1e-6f)       // fraction, matches UCI "Jitter(%)" column
            val jitterAbs = local / sr
            val rap = periodsMeanPerturbation(periods, 3) / (tMean + 1e-6f)
            val ppq = periodsMeanPerturbation(periods, 5) / (tMean + 1e-6f)
            val ddp = 3f * rap

            val aMean = (amps.sum() / amps.size).toFloat()
            if (aMean <= 0f) return Result()
            val shimmer = (amps.zipWithNext { x, y -> abs(x - y) }.sum() / (amps.size - 1) / aMean).toFloat()
            val shimmerDb = (amps.zipWithNext { x, y -> abs(20.0 * log10((y + 1e-9) / (x + 1e-9))) }.sum() / (amps.size - 1)).toFloat()
            val apq3 = amplitudePerturbation(amps, 3) / aMean
            val apq5 = amplitudePerturbation(amps, 5) / aMean
            val apq = amplitudePerturbation(amps, 11) / aMean
            val dda = 3f * apq3

            val hnrs = frames.map { f ->
                val r = f.rmax.toDouble().coerceIn(0.0, 0.999999)
                10.0 * log10(r / (1.0 - r))
            }
            val hnr = hnrs.average().toFloat()
            val nhr = hnrs.map { 1.0 / (1.0 + 10.0.pow(it / 10.0)) }.average().toFloat()

            return Result(
                valid = true,
                fo = fo, fhi = fhi, flo = flo,
                jitterFrac = jitterFrac, jitterAbs = jitterAbs,
                rap = rap, ppq = ppq, ddp = ddp,
                shimmer = shimmer, shimmerDb = shimmerDb,
                apq3 = apq3, apq5 = apq5, apq = apq, dda = dda,
                nhr = nhr.coerceIn(0f, 1f), hnr = hnr.coerceIn(-5f, 40f),
            )
        }

        private fun frameRms(audio: FloatArray, off: Int): Float {
            var s = 0.0
            for (k in off until off + FRAME) s += audio[k].toDouble() * audio[k]
            return sqrt(s / FRAME).toFloat()
        }

        private fun frameAmp(audio: FloatArray, off: Int): Float {
            var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
            for (k in off until off + FRAME) { mn = min(mn, audio[k]); mx = max(mx, audio[k]) }
            return mx - mn
        }

        /** Overlap-normalized autocorrelation peak over [MIN_LAG, MAX_LAG]. */
        private fun bestLag(audio: FloatArray, off: Int): Pair<Int, Float> {
            val n = FRAME
            var best = 0; var bestR = 0f
            for (lag in MIN_LAG..MAX_LAG) {
                var c = 0.0; var e1 = 0.0; var e2 = 0.0
                for (k in off until off + n - lag) {
                    c += audio[k].toDouble() * audio[k + lag]
                    e1 += audio[k].toDouble() * audio[k]
                    e2 += audio[k + lag].toDouble() * audio[k + lag]
                }
                val r = c / sqrt(e1 * e2 + 1e-12)
                if (r > bestR) { bestR = r.toFloat(); best = lag }
            }
            // reject frames whose best peak is too weak (unvoiced/noise)
            return if (bestR >= 0.30f) best to bestR else 0 to 0f
        }

        private fun periodsMeanPerturbation(t: List<Float>, k: Int): Float {
            var total = 0.0
            var cnt = 0
            for (i in t.indices) {
                var sum = 0.0; var m = 0
                for (j in max(0, i - k / 2)..min(t.size - 1, i + k / 2)) { sum += t[j]; m++ }
                total += abs(t[i] - sum / m); cnt++
            }
            return (if (cnt > 0) total / cnt else 0.0).toFloat()
        }

        private fun amplitudePerturbation(a: List<Double>, k: Int): Float {
            var total = 0.0; var cnt = 0
            for (i in a.indices) {
                var sum = 0.0; var m = 0
                for (j in max(0, i - k / 2)..min(a.size - 1, i + k / 2)) { sum += a[j]; m++ }
                total += abs(a[i] - sum / m); cnt++
            }
            return (if (cnt > 0) total / cnt else 0.0).toFloat()
        }

        private fun FloatArray.stddev(): Float {
            if (isEmpty()) return 0f
            val m = average().toFloat()
            return sqrt(map { (it - m) * (it - m) }.average()).toFloat()
        }

        data class Result(
            val valid: Boolean = false,
            val fo: Float = 0f, val fhi: Float = 0f, val flo: Float = 0f,
            val jitterFrac: Float = 0f, val jitterAbs: Float = 0f,
            val rap: Float = 0f, val ppq: Float = 0f, val ddp: Float = 0f,
            val shimmer: Float = 0f, val shimmerDb: Float = 0f,
            val apq3: Float = 0f, val apq5: Float = 0f,
            val apq: Float = 0f, val dda: Float = 0f,
            val nhr: Float = 0f, val hnr: Float = 0f,
        )
    }

    private fun voiceProxy(audio: FloatArray, sr: Int): Triple<Float, Float, Float> {
        // crude pitch via zero crossing rate, jitter = ZCR variance proxy
        var zc = 0
        for (i in 1 until audio.size) {
            if (sign(audio[i].toDouble()) != sign(audio[i - 1].toDouble())) zc++
        }
        val pitch = zc.toFloat() * sr / (2f * audio.size)
        val rms = audio.map { it * it }.average().toFloat()
        val shimmer = if (rms > 0f) sqrt(rms).toFloat() else 0f
        return Triple(pitch.coerceIn(50f, 400f), abs(pitch - 150f) / 150f, shimmer)
    }
}

private operator fun FloatArray.plus(other: FloatArray): FloatArray {
    val out = FloatArray(size + other.size)
    System.arraycopy(this, 0, out, 0, size)
    System.arraycopy(other, 0, out, size, other.size)
    return out
}