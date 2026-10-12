package com.parkimed.ai.tasks

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.parkimed.ai.R
import com.parkimed.ai.databinding.ActivityTapBinding
import com.parkimed.ai.features.FeatureExtractor
import com.parkimed.ai.model.ParkiMedModel
import com.parkimed.ai.sensors.TapCollector

/**
 * Tap test: tap anywhere on the screen for 10 seconds with two fingers
 * alternately. Tap intervals/durations → features → model.
 */
class TapTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTapBinding
    private val collector = TapCollector()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tapArea.setOnTouchListener { _, event ->
            collector.onTouch(event)
            binding.tapCount.text = "Taps: ${collector.tapCount}"
            true
        }

        binding.btnDone.setOnClickListener {
            if (collector.tapCount < 8) {
                Toast.makeText(this, "Too few taps — tap more (min 8)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            finishAnalyze()
        }

        if (com.parkimed.ai.BuildConfig.DEBUG) wireDemo()
    }

    private fun finishAnalyze() {
        val model = ParkiMedModel(this)
        try {
            val tap = FeatureExtractor.tap(collector.intervalMs, collector.durationMs)
            val pred = model.predict(FloatArray(39), FloatArray(36), tap, FloatArray(15))
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.parkimed.ai.data.LocalStore(this@TapTaskActivity)
                    .record(pred, "tap", floatArrayOf(0.5f, 0.5f, pred.pOn, 0.5f))
                runOnUiThread {
                    Toast.makeText(
                        this@TapTaskActivity,
                        "Tap result: ${pred.onOff} (sev ${"%.1f".format(pred.severity)}, irregularity " +
                                "${"%.2f".format(collector.rhythmIrregularity)})",
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

    // Debug demo: shows a patient-style tapping animation, then runs the REAL
    // ROC-HCI PD recording features (video finger-tapping) through the model.
    private fun wireDemo() {
        binding.demoRow.visibility = android.view.View.VISIBLE
        binding.btnDemoTap.setOnClickListener {
            binding.demoRipples.visibility = android.view.View.VISIBLE
            binding.tapCount.text = "Demo: patient tapping…"
            val intervals = DEMO_INTERVALS
            binding.demoRipples.start(intervals) {
                runOnUiThread {
                    binding.tapCount.text = "Taps: ${intervals.size} (simulated)"
                    runDemoPrediction(intervals)
                }
            }
        }
    }

    private fun runDemoPrediction(intervals: LongArray) {
        val model = ParkiMedModel(this)
        try {
            val tap = FeatureExtractor.tap(
                FloatArray(intervals.size) { i -> intervals[i].toFloat() },
                FloatArray(intervals.size) { 300f },
            )
            val pred = model.predict(
                FloatArray(39), FloatArray(36), tap, FloatArray(15), DEMO_PD_VIS,
            )
            if (pred == null) { Toast.makeText(this, "Model not loaded", Toast.LENGTH_SHORT).show(); return }
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.parkimed.ai.data.LocalStore(this@TapTaskActivity)
                    .record(pred, "tap(demo: real PD video)", floatArrayOf(0.5f, 0.5f, pred.pOn, 0.5f))
                runOnUiThread {
                    Toast.makeText(
                        this@TapTaskActivity,
                        "Tap demo: ${pred.onOff} · sev ${"%.1f".format(pred.severity)} · " +
                                "${"%.0f".format(pred.pOn * 100)}% ON (real PD tap pattern)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Demo failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            model.close()
        }
    }

    companion object {
        // Real ROC-HCI PD finger-tapping video features (Rating-4 patient),
        // same 20 columns as the v3 visual head.
        private val DEMO_PD_VIS = floatArrayOf(
            52.9757f, 2.65834f, 9.79285f, 0.133751f, 0.0299685f, 0.702191f,
            36f, 38f, 0.546946f, 0.546946f, 87.9081f, 22.7598f, 1.05619f,
            1.87051f, 0.145064f, 3.0792f, 5.04498f, 1.42411f, 84.7993f, -1.07393f,
        )
        // Irregular, hesitant patient-like cadence (ms) for the animation.
        private val DEMO_INTERVALS = longArrayOf(
            620, 470, 900, 390, 1350, 540, 730, 1080, 460, 820,
        )
    }
}