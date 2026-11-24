package com.parkimed.ai.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.parkimed.ai.R
import com.parkimed.ai.data.UserPrefs

/** Settings: AI key, profile, medication — all local-only. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = UserPrefs(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val etKey = findViewById<TextInputEditText>(R.id.etApiKey)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etAge = findViewById<TextInputEditText>(R.id.etAge)
        val etDiagYear = findViewById<TextInputEditText>(R.id.etDiagYear)
        val etMed = findViewById<TextInputEditText>(R.id.etMed)
        val etDose = findViewById<TextInputEditText>(R.id.etDose)
        val etDoseTime = findViewById<TextInputEditText>(R.id.etDoseTime)
        val swAi = findViewById<MaterialSwitch>(R.id.swAi)

        etKey.setText(prefs.apiKey)
        etName.setText(prefs.patientName)
        if (prefs.patientAge > 0) etAge.setText(prefs.patientAge.toString())
        if (prefs.diagnosisYear > 0) etDiagYear.setText(prefs.diagnosisYear.toString())
        etMed.setText(prefs.medicationName)
        etDose.setText(prefs.medicationDose)
        etDoseTime.setText(prefs.doseTime)
        swAi.isChecked = prefs.aiEnabled

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            prefs.apiKey = etKey.text?.toString().orEmpty()
            prefs.aiEnabled = swAi.isChecked
            prefs.patientName = etName.text?.toString().orEmpty()
            prefs.patientAge = etAge.text?.toString()?.toIntOrNull() ?: 0
            prefs.diagnosisYear = etDiagYear.text?.toString()?.toIntOrNull() ?: 0
            prefs.medicationName = etMed.text?.toString().orEmpty()
            prefs.medicationDose = etDose.text?.toString().orEmpty()
            val t = etDoseTime.text?.toString()?.trim().orEmpty()
            if (t.isNotBlank() && Regex("^\\d{1,2}:\\d{2}$").matches(t)) prefs.doseTime = t
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
