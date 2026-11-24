package com.parkimed.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Draws the per-modality model outputs as labelled horizontal bars.
 * 0.5 = modality absent (grey, exactly as the model gates it);
 * values above lean ON (green), below lean OFF (red).
 */
class ModalityGaugesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class Bar(val label: String, val value: Float)

    private var bars: List<Bar> = emptyList()

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        textSize = 34f
    }
    private val trackPaint = Paint().apply {
        color = 0xFFE2E8F0.toInt()
        style = Paint.Style.FILL
    }
    private val onPaint = Paint().apply { color = 0xFF10B981.toInt() }
    private val offPaint = Paint().apply { color = 0xFFF43F5E.toInt() }
    private val absentPaint = Paint().apply { color = 0xFF94A3B8.toInt() }

    fun setValues(voice: Float, motion: Float, tap: Float, spiral: Float) {
        bars = listOf(
            Bar("Voice", voice), Bar("Motion", motion),
            Bar("Tap", tap), Bar("Spiral", spiral),
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return
        val rowH = height / bars.size
        val labelW = 190f
        val trackLeft = labelW
        val trackRight = width - 120f
        val barH = rowH * 0.4f

        for ((i, b) in bars.withIndex()) {
            val cy = i * rowH + rowH / 2f
            canvas.drawText(b.label, 8f, cy + 12f, labelPaint)

            val rect = RectF(trackLeft, cy - barH / 2, trackRight, cy + barH / 2)
            canvas.drawRoundRect(rect, barH / 2, barH / 2, trackPaint)

            val paint = when {
                b.value == 0.5f -> absentPaint
                b.value > 0.5f -> onPaint
                else -> offPaint
            }
            val fill = RectF(trackLeft, cy - barH / 2,
                trackLeft + (trackRight - trackLeft) * b.value.coerceIn(0f, 1f), cy + barH / 2)
            canvas.drawRoundRect(fill, barH / 2, barH / 2, paint)

            val txt = if (b.value == 0.5f) "n/a" else "%.0f%%".format(b.value * 100)
            canvas.drawText(txt, trackRight + 20f, cy + 12f, labelPaint)
        }
    }
}
