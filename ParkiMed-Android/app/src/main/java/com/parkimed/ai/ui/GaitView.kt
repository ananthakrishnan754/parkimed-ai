package com.parkimed.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simulates a Parkinsonian "severe episode" visibly:
 *  - a stick figure walking in place with slow, shuffling, asymmetric steps
 *    and markedly reduced arm swing (stiff gait / bradykinesia), and
 *  - a wobbly (tremor-injected) spiral being traced beneath it — both poor
 *    motor signs the model reads as symptom ON* (OFF of medication).
 */
class GaitView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var startMs = 0L
    private var durationMs = 0L
    private var done = false
    private var onDone: (() -> Unit)? = null
    private var spiralPts = Array(0) { FloatArray(2) }
    private var spiralPtsDrawn = 0

    fun start(durationMs: Long, onDone: () -> Unit) {
        this.durationMs = durationMs
        this.onDone = onDone
        done = false
        spiralPtsDrawn = 0
        // wobbly Archimedean spiral (tremor-injected) for the "drawn" trace
        val n = 260
        spiralPts = Array(n) { i ->
            val a = i / 260f * 3.0f * Math.PI.toFloat() * 1.2f
            val r = (i / 260f) * 0.42f + 0.03f
            val wob = 0.012f * sin(38f * i) + 0.008f * sin(9f * i)
            floatArrayOf(0.5f + (r + wob) * cos(a.toDouble()).toFloat(),
                         0.5f + (r + wob) * sin(a.toDouble()).toFloat())
        }
        startMs = System.currentTimeMillis()
        handler.post(animTick)
    }

    fun stop() {
        handler.removeCallbacks(animTick)
        done = true
        invalidate()
    }

    private val animTick = object : Runnable {
        override fun run() {
            if (done) return
            invalidate()
            val t = System.currentTimeMillis() - startMs
            spiralPtsDrawn = minOf(spiralPts.size, (t / durationMs * spiralPts.size).toInt())
            if (t >= durationMs + 600) {
                done = true
                onDone?.invoke()
                return
            }
            handler.postDelayed(this, 16L)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val t = (System.currentTimeMillis() - startMs) / 600.0               // walk cycle
        val phase = (t % 1.0).toDouble()

        // --- walking figure (top ~55% of the view) ---
        val hipY = h * 0.34f
        val torsoX = w * 0.28f
        val stride = w * 0.085f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.rgb(0xF2, 0xF7, 0xFB)

        // head
        val headY = hipY - h * 0.11f
        paint.style = Paint.Style.FILL
        canvas.drawCircle(torsoX, headY, w * 0.026f, paint)
        paint.style = Paint.Style.STROKE

        // spine
        canvas.drawLine(torsoX, headY + w * 0.026f, torsoX, hipY, paint)

        // legs: shuffling feet (PD gait = short, stiff, less lift)
        val lift = 6f + 10f * sin(phase * Math.PI).toFloat() * 0.4f
        val fx1 = torsoX + cos(phase * 2.0 * Math.PI).toFloat() * stride
        val fx2 = torsoX - cos(phase * 2.0 * Math.PI).toFloat() * stride
        val fy1 = hipY + h * 0.14f - lift
        val fy2 = hipY + h * 0.14f - lift * 0.6f
        canvas.drawLine(torsoX, hipY, fx1, fy1, paint)
        canvas.drawLine(torsoX, hipY, fx2, fy2, paint)
        // stiff feet lines
        canvas.drawLine(fx1, fy1, fx1 + stride * 0.3f, fy1, paint)
        canvas.drawLine(fx2, fy2, fx2 - stride * 0.3f, fy2, paint)

        // arms: markedly reduced, asymmetric swing
        val armX = torsoX
        val armSwing = w * 0.02f * Math.sin(phase * Math.PI * 2).toFloat()
        val a1 = armX + armSwing
        val a2 = armX - armSwing * 1.3f
        canvas.drawLine(torsoX, hipY - h * 0.04f, a1, hipY + h * 0.07f, paint)
        canvas.drawLine(torsoX, hipY - h * 0.04f, a2, hipY + h * 0.06f, paint)

        // --- spiral trace (bottom ~40%) ---
        val sx0 = w * 0.10f; val sy0 = h * 0.66f
        val sw = w * 0.80f; val sh = h * 0.26f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(0x00, 0xE0, 0xB0)
        var prevX = -1f; var prevY = -1f
        for (i in 0 until spiralPtsDrawn) {
            val px = sx0 + spiralPts[i][0] * sw
            val py = sy0 + spiralPts[i][1] * sh
            if (prevX >= 0f) canvas.drawLine(prevX, prevY, px, py, paint)
            prevX = px; prevY = py
        }
    }
}