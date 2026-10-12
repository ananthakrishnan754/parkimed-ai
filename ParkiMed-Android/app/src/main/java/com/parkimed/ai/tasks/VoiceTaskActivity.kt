package com.parkimed.ai.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.parkimed.ai.BuildConfig
import com.parkimed.ai.R
import com.parkimed.ai.databinding.ActivityTaskBinding
import com.parkimed.ai.features.FeatureExtractor
import com.parkimed.ai.model.ParkiMedModel
import com.parkimed.ai.sensors.AudioCollector
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Voice task: record a short /ah/ vowel, extract voice features and run the
 * on-device model (other modalities passed as zero-length → are down-weighted
 * by the attention gates).
 */
class VoiceTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskBinding
    private var model: ParkiMedModel? = null

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) startRecording() else toast("Microphone permission required")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.taskTitle.text = "Voice Test"
        binding.taskHint.text = "Hold the phone 15 cm from your mouth.\n" +
                "Say a sustained 'AHHH...' for 3 seconds."
        binding.btnStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) startRecording()
            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (BuildConfig.DEBUG) wireDemoButtons()
    }

    private fun startRecording() {
        binding.btnStart.isEnabled = false
        binding.taskHint.text = "Recording... say AHHH"
        model = ParkiMedModel(this)
        thread {
            val audio = try {
                AudioCollector().record(3000L)
            } catch (t: Throwable) {
                Handler(Looper.getMainLooper()).post {
                    toast("Voice capture failed: ${t.message}")
                    binding.btnStart.isEnabled = true
                }
                FloatArray(0)
            }
            Handler(Looper.getMainLooper()).post {
                if (audio.isEmpty()) {
                    toast("Silent recording — please retry")
                    binding.btnStart.isEnabled = true
                    return@post
                }
                // Other modalities deliberately absent (zeros) →
                // attention gates will down-weight them.
                try {
                    val voice = FeatureExtractor.uciVoice(audio, 16000)
                    try {
                        val rms = kotlin.math.sqrt(audio.map { it.toDouble() * it }.average().toFloat() + 1e-9f)
                        val line = "n=${audio.size} rms=$rms peak=${audio.maxOrNull()}\n" +
                                voice.take(16).joinToString(separator = ",") { "%.5g".format(it) }
                        openFileOutput("voice_debug.txt", android.content.Context.MODE_APPEND)
                            .use { it.write((line + "\n").toByteArray()) }
                    } catch (e: Exception) { }
                    val pred = model?.predict(voice, FloatArray(36), FloatArray(20), FloatArray(15))
                    try {
                        openFileOutput("voice_debug.txt", android.content.Context.MODE_APPEND)
                            .use { it.write(("=> ${pred?.onOff} pOn=${pred?.pOn} sev=${pred?.severity}\n").toByteArray()) }
                    } catch (e: Exception) { }
                    if (pred == null) {
                        toast("Model not loaded")
                        binding.btnStart.isEnabled = true
                    } else {
                        persistAndFinish(pred, "voice", floatArrayOf(pred.pOn, 0.5f, 0.5f, 0.5f))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        toast("Inference failed: ${e.message}")
                        binding.btnStart.isEnabled = true
                    }
                }
            }
        }
    }

    private fun persistAndFinish(
        pred: ParkiMedModel.Prediction,
        sources: String,
        perModality: FloatArray,
    ) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.parkimed.ai.data.LocalStore(this@VoiceTaskActivity)
                .record(pred, sources, perModality)
            runOnUiThread {
                toast("Voice result: ${pred.onOff} (sev ${"%.1f".format(pred.severity)})")
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // Debug-only demo: feeds REAL UCI-recorded feature vectors (a true healthy
    // subject and a true PD subject) through the same model — deterministic.
    private fun wireDemoButtons() {
        binding.demoRow.visibility = android.view.View.VISIBLE
        binding.btnDemoHealthy.setOnClickListener {
            runDemo(DEMO_HEALTHY, "voice(demo healthy)")
        }
        binding.btnDemoSevere.setOnClickListener {
            runDemo(DEMO_SEVERE, "voice(demo severe)")
        }
    }

    private fun runDemo(mdvp16: FloatArray, source: String) {
        try {
            model = ParkiMedModel(this)
            val voice = FloatArray(39)
            System.arraycopy(mdvp16, 0, voice, 0, minOf(16, mdvp16.size))
            val pred = model?.predict(voice, FloatArray(36), FloatArray(20), FloatArray(15))
            if (pred == null) {
                toast("Model not loaded")
                return
            }
            persistAndFinish(pred, source, floatArrayOf(pred.pOn, 0.5f, 0.5f, 0.5f))
        } catch (e: Exception) {
            toast("Demo failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        model?.close()
        super.onDestroy()
    }

    companion object {
        // Real UCI Parkinsons speech rows (uci_speech.csv): 16 MDVP slots in
        // the same units FeatureExtractor.uciVoice produces.
        private val DEMO_HEALTHY = floatArrayOf(
            197.076004f, 206.895996f, 192.054993f, 0.00289f, 1e-5f, 0.00166f, 0.00168f,
            0.00498f, 0.01098f, 0.097f, 0.00563f, 0.0068f, 0.00802f, 0.01689f, 0.00339f, 26.775f,
        )
        private val DEMO_SEVERE = floatArrayOf(
            119.991997f, 157.302002f, 74.997002f, 0.00784f, 7e-5f, 0.0037f, 0.00554f,
            0.01109f, 0.04374f, 0.426f, 0.02182f, 0.0313f, 0.02971f, 0.06545f, 0.02211f, 21.033001f,
        )
    }
}