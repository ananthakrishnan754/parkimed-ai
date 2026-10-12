package com.parkimed.ai.sensors

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs

/**
 * Records N seconds of 16kHz mono audio from the microphone for the voice
 * task. Produces raw float samples for FeatureExtractor.voice().
 *
 * Never throws: on any failure (no mic, permission revoked mid-record,
 * audio hardware busy) it returns an empty array, which callers treat as
 * "recording unavailable".
 */
class AudioCollector(private val sampleRate: Int = 16000) {

    private var recorder: AudioRecord? = null

    /** Blocks for durationMs and returns raw normalized (-1..1) audio. */
    fun record(durationMs: Long): FloatArray {
        return try {
            capture(durationMs)
        } catch (t: Throwable) {
            Log.w("AudioCollector", "recording failed", t)
            release()
            FloatArray(0)
        }
    }

    private fun capture(durationMs: Long): FloatArray {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0 || minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) {
            return FloatArray(0)
        }
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, sampleRate / 2),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return FloatArray(0)
        }
        recorder = rec
        rec.startRecording()

        val n = (sampleRate * durationMs / 1000L).toInt()
        val shortBuf = ShortArray(maxOf(n / 2, sampleRate / 4))
        val out = FloatArray(n)
        var written = 0
        while (written < n) {
            val read = rec.read(shortBuf, 0, shortBuf.size)
            if (read < 0) break
            val need = minOf(read, n - written)
            for (i in 0 until need) {
                out[written + i] = shortBuf[i].toFloat() / 32768f
            }
            written += need
        }
        rec.stop()
        rec.release()
        recorder = null

        // detect silence / low quality -> signal quality hint
        val rms = kotlin.math.sqrt(out.map { it.toDouble() * it }.average() + 1e-9)
        if (rms < 0.01) return FloatArray(0)   // caller treats empty as "absent"
        val peak = out.maxOfOrNull { abs(it) } ?: 1f
        if (peak > 0f) for (i in out.indices) out[i] /= peak
        return out
    }

    fun release() {
        try {
            recorder?.release()
        } catch (_: Throwable) {
        } finally {
            recorder = null
        }
    }
}