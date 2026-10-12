package com.parkimed.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Canvas for the spiral / handwriting test. Captures stroke points, which the
 * view-owner passes to FeatureExtractor.spiral(). A faint template spiral is
 * rendered as guidance.
 */
class SpiralDrawView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    val points = mutableListOf<FloatArray>()   // [x, y]
    private val strokeWidth = 6f
    private val pen = Paint().apply {
        color = Color.CYAN
        strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val guide = Paint().apply {
        color = 0x33FFFFFF
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // template spiral centered on the view
        val cx = width / 2f; val cy = height / 2f
        val radius = minOf(width, height) * 0.30f
        val guidePath = android.graphics.Path()
        val turns = 2.5f
        val pointsGuide = 200
        for (i in 0..pointsGuide) {
            val t = turns * 2f * Math.PI * i / pointsGuide
            val r = radius * (i.toFloat() / pointsGuide)
            val x = cx + r * kotlin.math.cos(t).toFloat()
            val y = cy + r * kotlin.math.sin(t).toFloat()
            if (i == 0) guidePath.moveTo(x, y) else guidePath.lineTo(x, y)
        }
        canvas.drawPath(guidePath, guide)

        // user's trace
        if (points.size > 1) {
            val path = android.graphics.Path().apply {
                moveTo(points[0][0], points[0][1])
                for (i in 1 until points.size) lineTo(points[i][0], points[i][1])
            }
            canvas.drawPath(path, pen)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { points.clear(); points.add(floatArrayOf(event.x, event.y)) }
            MotionEvent.ACTION_MOVE -> points.add(floatArrayOf(event.x, event.y))
            MotionEvent.ACTION_UP -> points.add(floatArrayOf(event.x, event.y))
        }
        invalidate()
        return true
    }

    fun clear() { points.clear(); invalidate() }
}