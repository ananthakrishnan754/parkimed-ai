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
        detail.text = buildString {
            appendLine("Severity: %.1f / 5".format(r.severity))
            appendLine("Confidence: %.0f%%".format(r.confidence * 100))
            appendLine("Modalities used: ${r.sourcesUsed}")
            appendLine("→ Why? voice P(ON)=%.2f, motion=%.2f, tap=%.2f, spiral=%.2f"
                .format(r.voiceP, r.motionP, r.tapP, r.spiralP))
        }

        findViewById<com.parkimed.ai.ui.ModalityGaugesView>(R.id.gauges)
            .setValues(r.voiceP, r.motionP, r.tapP, r.spiralP)

        // --- Explanation + suggestions (offline rules; optional AI narrative) ---
        val perModality = floatArrayOf(r.voiceP, r.motionP, r.tapP, r.spiralP)
        val pred = com.parkimed.ai.model.ParkiMedModel.Prediction(
            onOff = r.onOff, severity = r.severity,
            confidence = r.confidence, pOn = r.voiceP.coerceIn(0f, 1f),
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
}