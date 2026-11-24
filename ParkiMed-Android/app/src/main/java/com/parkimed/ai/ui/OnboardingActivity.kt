package com.parkimed.ai.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.parkimed.ai.R
import com.parkimed.ai.data.UserPrefs

/** First-launch profile collection. All data stays local. */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<MaterialButton>(R.id.btnContinue).setOnClickListener {
            val prefs = UserPrefs(this)
            val name = findViewById<TextInputEditText>(R.id.etName).text?.toString()?.trim().orEmpty()
            val age = findViewById<TextInputEditText>(R.id.etAge).text?.toString()?.toIntOrNull() ?: 0
            if (name.isEmpty() || age <= 0) {
                Toast.makeText(this, "Name and age are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.patientName = name
            prefs.patientAge = age
            prefs.diagnosisYear = findViewById<TextInputEditText>(R.id.etDiagYear).text?.toString()?.toIntOrNull() ?: 0
            prefs.medicationName = findViewById<TextInputEditText>(R.id.etMed).text?.toString()?.trim().orEmpty()
            prefs.medicationDose = findViewById<TextInputEditText>(R.id.etDose).text?.toString()?.trim().orEmpty()
            val t = findViewById<TextInputEditText>(R.id.etDoseTime).text?.toString()?.trim().orEmpty()
            if (Regex("^\\d{1,2}:\\d{2}$").matches(t)) prefs.doseTime = t
            prefs.onboardingDone = true
            finish()
        }
    }
}
