package com.parkimed.ai.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.parkimed.ai.R
import com.parkimed.ai.data.AssessmentRecord
import com.parkimed.ai.data.LocalStore
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
        title.text = "Medication state: ${r.onOff}"
        detail.text = buildString {
            appendLine("Severity: %.1f / 5".format(r.severity))
            appendLine("Confidence: %.0f%%".format(r.confidence * 100))
            appendLine("Modalities used: ${r.sourcesUsed}")
            appendLine("→ Why? voice P(ON)=%.2f, motion=%.2f, tap=%.2f, spiral=%.2f"
                .format(r.voiceP, r.motionP, r.tapP, r.spiralP))
        }
    }
}