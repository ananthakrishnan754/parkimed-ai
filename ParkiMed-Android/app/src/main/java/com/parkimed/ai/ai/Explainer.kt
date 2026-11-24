package com.parkimed.ai.ai

import com.parkimed.ai.data.UserPrefs
import com.parkimed.ai.model.ParkiMedModel
import kotlin.math.abs

/**
 * Rule-based, fully offline explainer. Produces a plain-language
 * explanation of a prediction from the model's own numbers
 * (P(ON), severity, per-modality contributions) plus actionable
 * suggestions. This never invents data — every sentence is derived
 * from the actual feature values or the user's saved profile.
 */
object Explainer {

    data class Explanation(val why: String, val suggestions: List<String>)

    fun explain(
        pred: ParkiMedModel.Prediction,
        perModality: FloatArray,
        sources: String,
        prefs: UserPrefs,
    ): Explanation {
        val pOn = pred.pOn
        val sev = pred.severity
        val labels = listOf("Voice", "Motion", "Tap", "Spiral")

        // --- why ---
        val why = StringBuilder()
        why.append(
            if (pred.onOff == "ON")
                "The model estimates a ${"%.0f".format(pOn * 100)}% probability that medication is active (ON)"
            else
                "The model estimates a ${"%.0f".format((1f - pOn) * 100)}% probability that medication has worn off (OFF)"
        )
        if (sev >= 0.05f) {
            why.append(", with motor severity ${"%.1f".format(sev)} on a 0–5 scale")
        }
        why.append(".")

        // Strongest contributing modality present in this run.
        var bestIdx = -1
        var bestVal = -1f
        perModality.forEachIndexed { i, v ->
            if (v != 0.5f && (bestIdx == -1 || abs(v - 0.5f) > abs(bestVal - 0.5f))) {
                bestIdx = i; bestVal = v
            }
        }
        if (bestIdx >= 0) {
            val direction = if (bestVal > 0.5f) "ON-like" else "OFF-like"
            why.append(" The strongest signal came from the ${labels[bestIdx]} test (leaning $direction).")
        }
        why.append(" Modalities not used in this run are gated out and do not affect the result.")

        // --- suggestions ---
        val s = mutableListOf<String>()
        if (pred.onOff == "OFF") {
            if (sev >= 3.5f) {
                s.add("Severity is high (${sev.let { "%.1f".format(it) }}/5). Consider taking your next scheduled dose or contacting your clinician if symptoms feel unusual.")
            } else {
                s.add("Mild-to-moderate OFF symptoms detected. If this persists across a few assessments, discuss dose timing with your doctor.")
            }
            if (prefs.doseTime.isNotBlank()) {
                s.add("Your scheduled dose is at ${prefs.doseTime}. Tracking symptom changes around dose time helps your doctor tune timing.")
            }
            s.add("Re-test in 30–60 minutes to see whether the state changes after medication.")
        } else {
            s.add("Medication appears active. This is a good baseline window — periodic ON-state tests help the model learn your personal baseline.")
            if (sev >= 2f) {
                s.add("Some residual symptoms detected even in ON state — worth mentioning at your next check-up.")
            }
        }
        if (sources.contains("demo")) {
            s.add("Note: this run used built-in demo data, not a live recording.")
        }
        return Explanation(why.toString().trim(), s)
    }
}
