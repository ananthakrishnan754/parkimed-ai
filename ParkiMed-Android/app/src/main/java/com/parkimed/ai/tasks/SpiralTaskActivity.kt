package com.parkimed.ai.tasks

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.parkimed.ai.R
import com.parkimed.ai.features.FeatureExtractor
import com.parkimed.ai.model.ParkiMedModel
import com.parkimed.ai.ui.SpiralDrawView

/**
 * Spiral test: trace the template spiral once. Path coordinates →
 * spiral features → model.
 */
class SpiralTaskActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spiral)

        val drawView = findViewById<SpiralDrawView>(R.id.spiralView)

        findViewById<android.widget.Button>(R.id.btnSpiralClear).setOnClickListener {
            drawView.clear()
        }

        findViewById<android.widget.Button>(R.id.btnSpiralDone).setOnClickListener {
            if (drawView.points.size < 20) {
                Toast.makeText(this, "Draw the spiral completely first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val xs = drawView.points.map { it[0] }.toFloatArray()
            val ys = drawView.points.map { it[1] }.toFloatArray()
            val model = ParkiMedModel(this)
            try {
                val spiral = FeatureExtractor.spiral(xs, ys)
                val pred = model.predict(FloatArray(39), FloatArray(36), FloatArray(20), spiral)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    com.parkimed.ai.data.LocalStore(this@SpiralTaskActivity)
                        .record(pred, "spiral", floatArrayOf(0.5f, 0.5f, 0.5f, pred.pOn))
                    runOnUiThread {
                        Toast.makeText(
                            this@SpiralTaskActivity,
                            "Spiral result: ${pred.onOff} (sev ${"%.1f".format(pred.severity)})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Inference failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                model.close()
                finish()
            }
        }
    }
}