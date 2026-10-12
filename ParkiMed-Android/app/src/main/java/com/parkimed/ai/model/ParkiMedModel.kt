package com.parkimed.ai.model

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wraps parkimed_model.tflite for on-device inference.
 *
 * Inputs  (4 tensors, float32, shape [1, dim]):
 *   voice 39, motion 36, tap 20, spiral 15  (in signature order)
 *          slots 0..15 = real MDVP features, rest zero
 * Outputs (2 tensors):
 *   on_off   [1,2]   [P(PD), P(ON)]   index 1 = P(ON) => healthy = ON
 *   severity [1,1]   P(PD) * P(UPDRS high) 0..1  (caller scales by 5)
 */
class ParkiMedModel(context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            interpreter = Interpreter(
                loadModelFile(context, "parkimed_model.tflite"),
                Interpreter.Options().apply { setNumThreads(4) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model", e)
        }
    }

    /** Run inference on all feature vectors (v3: voice/motion/tap/spiral/visual). */
    fun predict(
        voice: FloatArray,
        motion: FloatArray,
        tap: FloatArray,
        spiral: FloatArray,
        visual: FloatArray = FloatArray(20),
        gait: FloatArray = FloatArray(24),
    ): Prediction {
        val interp = checkNotNull(interpreter) { "Model not loaded" }

        val clean = { src: FloatArray ->
            FloatArray(src.size) { i -> if (src[i].isFinite()) src[i] else 0f }
        }
        val srcs = mapOf(
            "voice" to clean(voice), "motion" to clean(motion), "tap" to clean(tap),
            "spiral" to clean(spiral), "visual" to clean(visual), "gait" to clean(gait),
        )
        // Resolve inputs by NAME (visual[20] and tap[20] have equal width); fall
        // back to dimension matching for older models.
        val ordered: Array<Any> = (0 until interp.inputTensorCount).map { i ->
            val t = interp.getInputTensor(i)
            val name = t.name().lowercase()
            val key = srcs.keys.firstOrNull { name.contains(it) }
            if (key != null) arrayOf(srcs[key])
            else {
                val dim = t.shape()[1]
                arrayOf(srcs.entries.first { it.value.size == dim }.value)
            }
        }.toTypedArray()

        // Allocate outputs by shape and locate on_off by its width of 2.
        val outputs: MutableMap<Int, Any> = mutableMapOf()
        var onOffIndex = -1
        var sevIndex = -1
        for (o in 0 until interp.outputTensorCount) {
            val w = interp.getOutputTensor(o).shape()[1]
            when (w) {
                2 -> {
                    outputs[o] = arrayOf(floatArrayOf(0f, 0f))
                    onOffIndex = o
                }
                1 -> {
                    outputs[o] = arrayOf(floatArrayOf(0f))
                    sevIndex = o
                }
                else -> error("Unexpected output width $w")
            }
        }
        check(onOffIndex >= 0 && sevIndex >= 0) { "Missing on_off/severity outputs" }

        interp.runForMultipleInputsOutputs(ordered, outputs)

        @Suppress("UNCHECKED_CAST")
        val onOff = outputs[onOffIndex] as Array<FloatArray>
        @Suppress("UNCHECKED_CAST")
        val sev = outputs[sevIndex] as Array<FloatArray>

        val pOn = onOff[0][1]
        val pOff = onOff[0][0]
        val severity = sev[0][0].coerceIn(0f, 1f) * 5f
        return Prediction(
            onOff = if (pOn >= 0.5f) "ON" else "OFF",
            severity = severity,
            confidence = maxOf(pOn, pOff),
            pOn = pOn,
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(context: Context, name: String): MappedByteBuffer {
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { ch ->
                return ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    data class Prediction(
        val onOff: String,
        val severity: Float,
        val confidence: Float,
        val pOn: Float,
    )

    companion object {
        private const val TAG = "ParkiMedModel"

        /** Load the model + run one dummy inference; logs the outcome (used at app start). */
        fun probe(owner: Context) {
            try {
                val m = ParkiMedModel(owner)
                val p = m.predict(FloatArray(39), FloatArray(36), FloatArray(20), FloatArray(15))
                Log.i(TAG, "probe OK -> ${p.onOff} rawPOn=${p.pOn}")
                m.close()
            } catch (t: Throwable) {
                Log.e(TAG, "probe FAILED", t)
            }
        }
    }
}