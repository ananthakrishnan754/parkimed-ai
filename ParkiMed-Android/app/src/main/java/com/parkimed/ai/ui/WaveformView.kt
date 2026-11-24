package com.parkimed.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws a captured audio buffer as a centered min/max envelope waveform.
 * Feed with [setAudio] once a recording finishes; empty buffer clears.
 */
class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var envelope: FloatArray = FloatArray(0)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4F46E5.toInt()
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val midPaint = Paint().apply {
        color = 0xFFE2E8F0.toInt()
        strokeWidth = 1.5f
    }

    fun setAudio(audio: FloatArray) {
        // Downsample to ~150 columns of max-abs envelope.
        val cols = 150
        envelope = FloatArray(cols)
        if (audio.isNotEmpty()) {
            val chunk = audio.size / cols
            for (c in 0 until cols) {
                var m = 0f
                val start = c * chunk
                val end = minOf(audio.size, start + chunk)
                var i = start
                while (i < end) {
                    val a = kotlin.math.abs(audio[i])
                    if (a > m) m = a
                    i++
                }
                envelope[c] = m
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midY = height / 2f
        canvas.drawLine(0f, midY, width.toFloat(), midY, midPaint)
        if (envelope.isEmpty()) return
        val colW = width.toFloat() / envelope.size
        for (c in envelope.indices) {
            val amp = envelope[c] * (height / 2f - 6f)
            if (amp < 0.5f) continue
            val x = c * colW + colW / 2
            canvas.drawLine(x, midY - amp, x, midY + amp, barPaint)
        }
    }
}
