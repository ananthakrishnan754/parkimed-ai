package com.parkimed.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.parkimed.ai.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dependency-free severity-over-time line chart.
 * Series = (timestamp, severity 0..5), oldest first.
 */
class SeverityTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    var series: List<Pair<Long, Float>> = emptyList()
        set(value) {
            field = value.sortedBy { it.first }
            invalidate()
        }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(40, 255, 255, 255)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 34f
    }
    private val gridPath = Path()
    private val timeFmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bg = ContextCompat.getColor(context, R.color.card)
        canvas.drawColor(bg)

        if (series.size < 2) {
            val msg = if (series.size == 1) "One assessment — run more tests" else "No trend data yet"
            labelPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
            canvas.drawText(msg, width / 2f, height / 2f, labelPaint)
            return
        }

        val padL = 74f
        val padR = 34f
        val padT = 40f
        val padB = 60f
        val w = width - padL - padR
        val h = height - padT - padB

        // horizontal grid at y for severity 0, 2.5, 5
        gridPath.rewind()
        for (sev in listOf(0f, 2.5f, 5f)) {
            val y = padT + h * (1f - sev / 5f)
            gridPath.moveTo(padL, y)
            gridPath.lineTo(padL + w, y)
            labelPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
            canvas.drawText(sev.toInt().toString(), 10f, y + 12f, labelPaint)
        }
        canvas.drawPath(gridPath, gridPaint)

        val minT = series.first().first
        val maxT = series.last().first
        val fx = { t: Long -> padL + w * ((t - minT).toFloat() / maxOf(1L, maxT - minT)) }
        val fy = { s: Float -> padT + h * (1f - s.coerceIn(0f, 5f) / 5f) }

        // x-axis time labels: first, midpoint, last
        val midT = minT + (maxT - minT) / 2
        canvas.drawText(timeFmt.format(Date(minT)), padL, padT + h + 36f, labelPaint)
        canvas.drawText(timeFmt.format(Date(midT)), padL + w / 2f - 60f, padT + h + 36f, labelPaint)
        canvas.drawText(timeFmt.format(Date(maxT)), padL + w - 150f, padT + h + 36f, labelPaint)

        val x = FloatArray(series.size) { i -> fx(series[i].first) }
        val y = FloatArray(series.size) { i -> fy(series[i].second) }

        val line = Path().apply {
            moveTo(x[0], y[0])
            for (i in 1 until series.size) {
                lineTo(x[i], y[i])
            }
        }

        // gradient fill under the line
        fillPaint.shader = LinearGradient(
            0f, padT, 0f, padT + h,
            Color.argb(120, 0, 224, 176),
            Color.argb(0, 0, 224, 176),
            Shader.TileMode.CLAMP,
        )
        val fill = Path(line).apply {
            lineTo(x.last(), padT + h)
            lineTo(x[0], padT + h)
            close()
        }
        canvas.drawPath(fill, fillPaint)

        linePaint.color = ContextCompat.getColor(context, R.color.primary)
        canvas.drawPath(line, linePaint)

        dotPaint.color = ContextCompat.getColor(context, R.color.accent)
        for (i in series.indices) canvas.drawCircle(x[i], y[i], 8f, dotPaint)
    }
}