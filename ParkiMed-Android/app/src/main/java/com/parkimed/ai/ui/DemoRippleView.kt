package com.parkimed.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.random.Random

/**
 * Visualizes a simulated Parkinson's finger-tapping pattern: dots "tap" the
 * pad at irregular, hesitant intervals (large gaps, occasional double-taps) —
 * the way a patient with bradykinesia/hesitation would tap.
 */
class DemoRippleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private data class Ripple(val start: Long, val x: Float, val y: Float, val r: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private val ripples = mutableListOf<Ripple>()
    private val intervals = mutableListOf<Long>()
    private var startMs = 0L
    private var done = false
    private var onDone: (() -> Unit)? = null
    private val rnd = Random(7)

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(resolveSize(suggestedMinimumWidth, w),
                        resolveSize(suggestedMinimumHeight, h))
    }

    fun start(intervalMs: LongArray, onDone: () -> Unit) {
        this.intervals.clear()
        intervalMs.forEach { this.intervals.add(it) }
        this.onDone = onDone
        done = false
        ripples.clear()
        startMs = System.currentTimeMillis()
        handler.post(animTick)
    }

    fun stop() {
        handler.removeCallbacks(animTick)
        done = true
        ripples.clear()
        invalidate()
    }

    private fun totalMs(): Long = intervals.sum()

    private val animTick = object : Runnable {
        override fun run() {
            if (done) return
            val t = System.currentTimeMillis() - startMs
            var cum = 0L
            for (i in intervals.indices) {
                cum += intervals[i]
                if (t >= cum && i >= ripples.size) {
                    val w = width.toFloat(); val h = height.toFloat()
                    val x = (0.2f + 0.6f * rnd.nextFloat()) * w
                    val y = (0.2f + 0.6f * rnd.nextFloat()) * h
                    ripples.add(Ripple(System.currentTimeMillis(), x, y, 12f + 26f * rnd.nextFloat()))
                }
            }
            invalidate()
            if (t >= totalMs() + 900) {
                done = true
                onDone?.invoke()
                return
            }
            handler.postDelayed(this, 16L)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        paint.style = Paint.Style.FILL
        val iter = ripples.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            val age = now - r.start
            if (age > 900L) { iter.remove(); continue }
            val k = age / 900f
            val prog = 1f - k
            // touch dot
            paint.color = Color.argb((150 * prog).toInt(), 0x00, 0xE0, 0xB0)
            canvas.drawCircle(r.x, r.y, 10f * prog + 2f, paint)
            // expanding ripple ring
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.argb((80 * prog).toInt(), 0x6B, 0xCB, 0xFF)
            canvas.drawCircle(r.x, r.y, min(r.r * (1f + 2.2f * k), (width + height) / 4f), paint)
            paint.style = Paint.Style.FILL
        }
    }
}