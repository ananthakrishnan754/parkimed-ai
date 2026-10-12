package com.parkimed.ai.sensors

import android.view.MotionEvent
import kotlin.math.abs

/**
 * Logs tap DOWN/UP events during the 10s tap test and derives
 * interval_ms / duration_ms arrays for FeatureExtractor.tap().
 */
class TapCollector {

    private val intervals = mutableListOf<Float>()
    private val durations = mutableListOf<Float>()
    private var lastDownTime = 0L
    private var downCount = 0

    fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (downCount > 0) {
                    intervals.add((event.eventTime - lastDownTime).toFloat())
                }
                lastDownTime = event.eventTime
                downCount++
            }
            MotionEvent.ACTION_UP -> {
                durations.add((event.eventTime - lastDownTime).toFloat())
            }
        }
    }

    val intervalMs: FloatArray get() = intervals.toFloatArray()
    val durationMs: FloatArray get() = durations.toFloatArray()
    val tapCount: Int get() = downCount
    val rhythmIrregularity: Float get() =
        if (intervals.size < 2) 0f else (abs(intervals.stddev() / (intervals.average() + 1e-6))).toFloat()

    fun reset() {
        intervals.clear(); durations.clear(); downCount = 0; lastDownTime = 0L
    }

    private fun List<Float>.stddev(): Double {
        val m = average()
        return kotlin.math.sqrt(map { (it - m) * (it - m) }.average())
    }
}