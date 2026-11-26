package com.parkimed.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Visualizes finger-tapping rhythm: one bar per tap interval.
 * Regular tapping = near-equal bars; bradykinesia/hesitation shows as
 * long, irregular bars. Mean line included for quick reading.
 */
class TapRhythmView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var intervals: LongArray = LongArray(0)

    private val barOk = Paint().apply { color = 0xFF10B981.toInt() }
    private val barSlow = Paint().apply { color = 0xFFF43F5E.toInt() }
    private val meanLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4F46E5.toInt()
        strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = 28f
    }

    fun setIntervals(iv: LongArray) { intervals = iv; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (intervals.size < 2) {
            canvas.drawText("Not enough taps", 20f, height / 2f, labelPaint)
            return
        }
        val maxIv = intervals.max().toFloat().coerceAtLeast(1f)
        val n = intervals.size
        val slot = width.toFloat() / n
        val barW = (slot * 0.62f).coerceAtMost(60f)
        val mean = intervals.average().toFloat()

        for (i in 0 until n) {
            val hgt = (height * 0.72f) * (intervals[i] / maxIv)
            val x = i * slot + (slot - barW) / 2
            val paint = if (intervals[i] > mean * 1.6f) barSlow else barOk
            canvas.drawRoundRect(
                x, height - hgt, x + barW, height - 12f, 10f, 10f, paint,
            )
        }
        val my = height - 12f - (height * 0.72f) * (mean / maxIv)
        canvas.drawLine(0f, my, width.toFloat(), my, meanLine)
        canvas.drawText("mean %.0f ms".format(mean), 16f, my - 10f, meanLine)
    }
}
