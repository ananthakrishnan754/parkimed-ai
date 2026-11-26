package com.parkimed.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Semicircular gauge for motor severity (0..5). Arc color shifts
 * green -> amber -> red as severity rises; needle + big value centered.
 */
class SeverityGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var severity = 0f
    private var animateFrom = 0f

    private val arcBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFE2E8F0.toInt()
    }
    private val arcFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF0F172A.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textAlign = Paint.Align.CENTER
    }

    fun setSeverity(v: Float) {
        severity = v.coerceIn(0f, 5f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val stroke = h * 0.13f
        val radius = minOf(w / 2f, h) - stroke
        val cx = w / 2f
        val cy = h * 0.92f
        arcBg.strokeWidth = stroke
        arcFg.strokeWidth = stroke

        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val start = 180f; val sweep = 180f

        // background track
        canvas.drawArc(rect, start, sweep, false, arcBg)

        // colored value arc (single color chosen by zone)
        val frac = severity / 5f
        arcFg.color = when {
            severity < 1.5f -> 0xFF10B981.toInt()
            severity < 3f -> 0xFFF59E0B.toInt()
            else -> 0xFFF43F5E.toInt()
        }
        if (frac > 0.005f) canvas.drawArc(rect, start, sweep * frac, false, arcFg)

        // ticks 0..5
        for (i in 0..5) {
            val ang = Math.toRadians((180 + sweep * i / 5f).toDouble())
            val x1 = cx + (radius - stroke * 0.9f) * kotlin.math.cos(ang).toFloat()
            val y1 = cy + (radius - stroke * 0.9f) * kotlin.math.sin(ang).toFloat()
            val x2 = cx + (radius + stroke * 0.55f) * kotlin.math.cos(ang).toFloat()
            val y2 = cy + (radius + stroke * 0.55f) * kotlin.math.sin(ang).toFloat()
            canvas.drawLine(x1, y1, x2, y2, arcBg)
        }

        // needle
        val ang = Math.toRadians((180 + sweep * frac).toDouble())
        val nx = cx + (radius - stroke * 0.2f) * kotlin.math.cos(ang).toFloat()
        val ny = cy + (radius - stroke * 0.2f) * kotlin.math.sin(ang).toFloat()
        canvas.drawLine(cx, cy, nx, ny, needle)
        canvas.drawCircle(cx, cy, stroke * 0.28f, needle)

        // value text
        textPaint.textSize = h * 0.22f
        canvas.drawText("%.1f".format(severity), cx, cy - h * 0.16f, textPaint)
        labelPaint.textSize = h * 0.09f
        canvas.drawText("SEVERITY / 5", cx, cy - h * 0.02f, labelPaint)

        // zone labels
        labelPaint.textSize = h * 0.075f
        canvas.drawText("0", cx - radius, cy + h * 0.07f, labelPaint)
        canvas.drawText("5", cx + radius, cy + h * 0.07f, labelPaint)
    }
}
