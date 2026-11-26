package com.parkimed.ai.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.parkimed.ai.R
import com.parkimed.ai.ai.Explainer
import com.parkimed.ai.ai.GeminiClient
import com.parkimed.ai.data.AssessmentRecord
import com.parkimed.ai.data.LocalStore
import com.parkimed.ai.data.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Latest assessment result + daily trend chart.
 */
class ResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        val store = LocalStore(this)
        CoroutineScope(Dispatchers.Main).launch {
            val history = withContext(Dispatchers.IO) { store.recent(30) }
            val latest = history.firstOrNull()
            val title = findViewById<android.widget.TextView>(R.id.tvResult)
            val detail = findViewById<android.widget.TextView>(R.id.tvResultDetail)
            val chart = findViewById<SeverityTrendView>(R.id.trendChart)

            chart.series = history
                .asReversed()
                .map { it.timestamp to it.severity }

            if (latest == null) {
                title.text = "No assessments yet"
                detail.text = "Run a task (voice / movement / tap / spiral) from home."
            } else {
                render(latest, title, detail)
            }
        }
    }

    private fun render(r: AssessmentRecord, title: android.widget.TextView,
                       detail: android.widget.TextView) {
        val prefs = UserPrefs(this)
        val isOn = r.onOff == "ON"
        val onColor = androidx.core.content.ContextCompat.getColor(this, R.color.state_on)
        val offColor = androidx.core.content.ContextCompat.getColor(this, R.color.state_off)

        val chip = findViewById<android.widget.TextView>(R.id.tvStateChip)
        chip.text = r.onOff
        chip.setTextColor(if (isOn) onColor else offColor)

        findViewById<android.widget.TextView>(R.id.tvSeverityBig).text =
            "Severity: %.1f / 5.0".format(r.severity)

        findViewById<android.widget.LinearLayout>(R.id.cardResult)
            .setBackgroundResource(if (isOn) R.drawable.bg_on_state else R.drawable.bg_off_state)

        title.text = "Medication state: ${r.onOff}"
        title.setTextColor(if (isOn) onColor else offColor)

        // confidence visualization: thin progress bar under the severity
        val confBar = findViewById<android.widget.ProgressBar>(R.id.confBar)
        confBar.progress = (r.confidence * 100).toInt()

        detail.text = buildString {
            appendLine("Severity: %.1f / 5".format(r.severity))
            appendLine("Confidence: %.0f%%".format(r.confidence * 100))
            appendLine("Modalities used: ${r.sourcesUsed}")
        }

        findViewById<com.parkimed.ai.ui.ModalityGaugesView>(R.id.gauges)
            .setValues(r.voiceP, r.motionP, r.tapP, r.spiralP)
        findViewById<com.parkimed.ai.ui.SeverityGaugeView>(R.id.gaugeResult)
            .setSeverity(r.severity)
        findViewById<android.widget.TextView>(R.id.tvMeasured).text = measuredSummary(r)

        // --- Explanation + suggestions (offline rules; optional AI narrative) ---
        // per-modality slots hold 0.5 for "not used"; the used modality's slot
        // carries the model's P(ON) for that run.
        val perModality = floatArrayOf(r.voiceP, r.motionP, r.tapP, r.spiralP)
        val truePOn = perModality.firstOrNull { it != 0.5f } ?: 0.5f
        val pred = com.parkimed.ai.model.ParkiMedModel.Prediction(
            onOff = r.onOff, severity = r.severity,
            confidence = r.confidence, pOn = truePOn.coerceIn(0f, 1f),
        )
        val ex = Explainer.explain(pred, perModality, r.sourcesUsed, prefs)
        findViewById<android.widget.TextView>(R.id.txtWhy).text = ex.why
        findViewById<android.widget.TextView>(R.id.txtSuggestions).text =
            ex.suggestions.joinToString(separator = "\n") { "•  $it" }

        val aiNarr = findViewById<android.widget.TextView>(R.id.txtAiNarrative)
        if (prefs.aiEnabled && prefs.hasApiKey) {
            CoroutineScope(Dispatchers.Main).launch {
                val text = GeminiClient.explain(pred, perModality, r.sourcesUsed, prefs)
                if (text != null) {
                    aiNarr.visibility = View.VISIBLE
                    aiNarr.text = text
                }
            }
        } else {
            aiNarr.visibility = View.GONE
        }
    }

    /** Plain-language description of what each used modality measures. */
    private fun measuredSummary(r: AssessmentRecord): String = buildString {
        val used = r.sourcesUsed.lowercase()
        if (used.contains("voice")) appendLine("• Voice: pitch stability, jitter and shimmer of a sustained /ah/ sound")
        if (used.contains("motion")) appendLine("• Motion: hand tremor from accelerometer/gyroscope")
        if (used.contains("tap")) appendLine("• Tap: finger-tapping speed and rhythm irregularity")
        if (used.contains("spiral")) appendLine("• Spiral: drawing size, stroke consistency and closure")
        if (used.contains("gait")) appendLine("• Gait: stride cadence and balance while walking")
        if (used.contains("demo")) appendLine("• Note: built-in demo data was used (not a live recording)")
        if (isEmpty()) append("• ${r.sourcesUsed}")
        append("\nAbsent modalities are gated out — they never affect this result.")
    }
}