package com.parkimed.ai.data

import android.content.Context

/**
 * Central preferences store (SharedPreferences). Holds the AI API key,
 * patient profile collected during onboarding, and user settings.
 * Nothing here is ever synced or logged.
 */
class UserPrefs(context: Context) {

    private val sp = context.getSharedPreferences("parkimed_prefs", Context.MODE_PRIVATE)

    // --- AI ---
    var apiKey: String
        get() = sp.getString(KEY_API, "").orEmpty()
        set(v) = sp.edit().putString(KEY_API, v.trim()).apply()

    var aiEnabled: Boolean
        get() = sp.getBoolean(KEY_AI_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_AI_ENABLED, v).apply()

    // --- Patient profile (onboarding) ---
    var patientName: String
        get() = sp.getString(KEY_NAME, "").orEmpty()
        set(v) = sp.edit().putString(KEY_NAME, v.trim()).apply()

    var patientAge: Int
        get() = sp.getInt(KEY_AGE, 0)
        set(v) = sp.edit().putInt(KEY_AGE, v).apply()

    var diagnosisYear: Int
        get() = sp.getInt(KEY_DIAG_YEAR, 0)
        set(v) = sp.edit().putInt(KEY_DIAG_YEAR, v).apply()

    var medicationName: String
        get() = sp.getString(KEY_MED, "").orEmpty()
        set(v) = sp.edit().putString(KEY_MED, v.trim()).apply()

    var medicationDose: String
        get() = sp.getString(KEY_DOSE, "").orEmpty()
        set(v) = sp.edit().putString(KEY_DOSE, v.trim()).apply()

    var doseTime: String
        get() = sp.getString(KEY_DOSE_TIME, "08:00").orEmpty()
        set(v) = sp.edit().putString(KEY_DOSE_TIME, v).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(v) = sp.edit().putBoolean(KEY_ONBOARDED, v).apply()

    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val profileSummary: String
        get() = buildString {
            if (patientName.isNotBlank()) append("Patient: $patientName. ")
            if (patientAge > 0) append("Age: $patientAge. ")
            if (diagnosisYear > 0) append("Diagnosed: $diagnosisYear. ")
            if (medicationName.isNotBlank()) {
                append("Medication: $medicationName")
                if (medicationDose.isNotBlank()) append(" $medicationDose")
                append(". ")
            }
            if (doseTime.isNotBlank()) append("Dose time: $doseTime.")
        }.trim()

    companion object {
        private const val KEY_API = "ai_api_key"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_NAME = "patient_name"
        private const val KEY_AGE = "patient_age"
        private const val KEY_DIAG_YEAR = "diagnosis_year"
        private const val KEY_MED = "medication_name"
        private const val KEY_DOSE = "medication_dose"
        private const val KEY_DOSE_TIME = "dose_time"
        private const val KEY_ONBOARDED = "onboarding_done"
    }
}
