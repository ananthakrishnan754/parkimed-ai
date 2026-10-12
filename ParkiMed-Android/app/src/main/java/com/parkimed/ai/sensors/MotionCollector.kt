package com.parkimed.ai.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Collects accelerometer + gyroscope during a 30s walk/task and produces
 * de-gravity windows (window=300 samples @100Hz, overlap 150) for
 * FeatureExtractor.motion().
 *
 * Missing sensors (e.g. no gyroscope on some devices/emulators) degrade
 * gracefully: gyro channels are recorded as zeros so the task still runs.
 */
class MotionCollector(context: Context, private val durationMs: Long) :
    SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val samples = mutableListOf<FloatArray>()   // [t,x,y,z,gx,gy,gz]
    private var accel: FloatArray? = null
    private var gyro: FloatArray? = null
    private val gravity = FloatArray(3)
    private val alpha = 0.8f

    private val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val available: Boolean get() = accelSensor != null

    var onComplete: ((List<FloatArray>) -> Unit)? = null

    fun start() {
        if (accelSensor != null) {
            sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        if (gyroSensor != null) {
            sm.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // low-pass gravity estimate, high-pass = linear acceleration
                for (i in 0..2) {
                    gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i]
                }
                accel = floatArrayOf(
                    event.values[0] - gravity[0],
                    event.values[1] - gravity[1],
                    event.values[2] - gravity[2],
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyro = event.values.clone()
            }
        }
        val a = accel ?: return
        val t = System.currentTimeMillis()
        val g = gyro ?: if (gyroSensor == null) floatArrayOf(0f, 0f, 0f) else return
        samples.add(floatArrayOf(t.toFloat(), a[0], a[1], a[2], g[0], g[1], g[2]))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun buildWindows(): List<FloatArray> {
        // resample to ~100Hz and chunk into windows of 300 with 150 overlap
        val sorted = samples.sortedBy { it[0] }
        if (sorted.size < 30) return emptyList()
        val t0 = sorted.first()[0]
        val t1 = sorted.last()[0]
        val n = ((t1 - t0) * 0.1f).toInt()          // ~100 Hz
        if (n < 10) return emptyList()
        val resampled = MutableList(n) { FloatArray(6) }
        for (k in 0 until n) {
            val tk = t0 + k * 10f
            // linear interp between nearest samples
            var i = 0
            while (i < sorted.size - 1 && sorted[i][0] < tk) i++
            val lo = sorted[maxOf(0, i - 1)]; val hi = sorted[minOf(sorted.size - 1, i)]
            if (hi[0] - lo[0] <= 0f) {
                System.arraycopy(hi, 1, resampled[k], 0, 6)
            } else {
                val w = (tk - lo[0]) / (hi[0] - lo[0])
                for (c in 0 until 6) {
                    resampled[k][c] = lo[c + 1] + w * (hi[c + 1] - lo[c + 1])
                }
            }
        }
        // windows
        val win = 300; val step = 150
        val windows = mutableListOf<FloatArray>()
        var start = 0
        while (start + win <= resampled.size) {
            val w = FloatArray(win * 6)
            for (c in 0 until 6)
                for (j in 0 until win) w[c * win + j] = resampled[start + j][c]
            windows.add(w)
            start += step
        }
        return windows
    }

    companion object { private const val G = 9.81f }
}