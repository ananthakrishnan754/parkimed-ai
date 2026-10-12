package com.parkimed.ai.tasks

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.parkimed.ai.R
import com.parkimed.ai.databinding.ActivityTaskBinding
import com.parkimed.ai.features.FeatureExtractor
import com.parkimed.ai.model.ParkiMedModel
import com.parkimed.ai.sensors.MotionCollector

/**
 * Walk/Gait task: 30 seconds of accelerometer + gyroscope while the patient
 * walks in place / static hold. Windows → motion features → model.
 */
class WalkTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskBinding
    private var collector: MotionCollector? = null
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.taskTitle.text = "Movement Test"
        binding.taskHint.text = "Hold the phone in your palm.\n" +
                "Walk in place for 10 seconds (production: 30s)."
        binding.btnStart.setOnClickListener { startCapture() }
        if (com.parkimed.ai.BuildConfig.DEBUG) wireDemo()
    }

    // Debug demo: animates a slow, stiff, asymmetric walk + a tremor spiral,
    // then feeds REAL REMAP OFF-medication gait features + a REAL UCI PD spiral
    // through the model — a simulated severe episode (ratiophase OFF).
    private fun wireDemo() {
        binding.gaitDemoView.visibility = android.view.View.VISIBLE
        binding.btnDemoWalk.visibility = android.view.View.VISIBLE
        binding.btnDemoWalk.setOnClickListener {
            binding.taskHint.text = "Simulating a PD episode: stiff gait + tremor spiral…"
            binding.btnStart.isEnabled = false
            binding.btnDemoWalk.isEnabled = false
            binding.gaitDemoView.start(6_000L) {
                runOnUiThread { runDemoPrediction() }
            }
        }
    }

    private fun runDemoPrediction() {
        val model = ParkiMedModel(this)
        try {
            val gauge = DEMO_GAIT_OFF  // real REMAP OFF-med turn
            val spiral = DEMO_SPIRAL_PD // real UCI PD drawing
            val pred = model.predict(
                FloatArray(39), FloatArray(36), FloatArray(20), spiral,
                FloatArray(20), gauge,
            )
            if (pred == null) { Toast.makeText(this, "Model not loaded", Toast.LENGTH_SHORT).show(); return }
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.parkimed.ai.data.LocalStore(this@WalkTaskActivity)
                    .record(pred, "gait+spiral(demo: real PD)", floatArrayOf(0.5f, pred.pOn, 0.5f, pred.pOn))
                runOnUiThread {
                    Toast.makeText(
                        this@WalkTaskActivity,
                        "Demo: ${pred.onOff} · sev ${"%.1f".format(pred.severity)} · " +
                                "${"%.0f".format(pred.pOn * 100)}% ON (real OFF-med gait + PD spiral)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Demo failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            model.close()
            binding.taskHint.text = "Hold the phone in your palm.\nWalk in place for 10 seconds (production: 30s)."
            binding.btnStart.isEnabled = true
            binding.btnDemoWalk.isEnabled = true
        }
    }

    companion object {
        // Real REMAP PD turning clip Pt412_PD_n_1109, OFF medication.
        private val DEMO_GAIT_OFF = floatArrayOf(
            0.0283965f, 0.0525614f, 0.0285152f, 0.0300641f, 0.0301685f, 0.031669f,
            0.0413217f, 0.033721f, 0.0355937f, 0.0237773f, 0.0188175f, 0.0325495f,
            0.109989f, 0.4f, 0.4f, 0.0828415f, 0.0262172f, 0.0411985f, 0.0149813f,
            2.40416f, 0.0153778f, 0.54152f, 268f, 0.223607f,
        )
        // Real UCI PD spiral drawing features, row (unit-normalized tablet).
        private val DEMO_SPIRAL_PD = floatArrayOf(
            0.0032606f, 5.556f, 0.0032625f, 0.0018424f, 0.022784f, 0.0032625f,
            0.0018424f, 1f, 0.91733f, 0.39104f, 1704f, 0.44279f, -0.087637f,
            5.556f, 0.44591f,
        )
    }

    private fun startCapture() {
        binding.btnStart.isEnabled = false
        binding.taskHint.text = "Keep moving... 10s"
        val model = ParkiMedModel(this)

        collector = MotionCollector(this, 10_000L).also {
            if (!it.available) {
                binding.taskHint.text = "No motion sensor — cannot run Movement test"
                binding.btnStart.isEnabled = true
                return
            }
            it.start()
            it.onComplete = { windows ->
                try {
                    if (windows.isEmpty()) {
                        Toast.makeText(
                            this,
                            "No motion data collected — please retry",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val motion = FeatureExtractor.motion(windows)
                        val pred = model.predict(FloatArray(39), motion, FloatArray(20), FloatArray(15))
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            com.parkimed.ai.data.LocalStore(this@WalkTaskActivity)
                                .record(pred, "motion", floatArrayOf(0.5f, pred.pOn, 0.5f, 0.5f))
                            runOnUiThread {
                                Toast.makeText(
                                    this@WalkTaskActivity,
                                    "Motion result: ${pred.onOff} (sev ${"%.1f".format(pred.severity)})",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
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

        timer = object : CountDownTimer(10_000, 1_000) {
            override fun onTick(ms: Long) {
                binding.taskHint.text = "Keep moving... ${ms / 1000}s left"
            }
            override fun onFinish() {
                collector?.stop()
                val windows = collector?.buildWindows() ?: emptyList()
                collector?.onComplete?.invoke(windows)
            }
        }.start()
    }

    override fun onDestroy() {
        timer?.cancel()
        collector?.stop()
        super.onDestroy()
    }
}