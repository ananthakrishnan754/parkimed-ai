package com.parkimed.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.parkimed.ai.data.LocalStore
import com.parkimed.ai.data.UserPrefs
import com.parkimed.ai.tasks.SpiralTaskActivity
import com.parkimed.ai.tasks.TapTaskActivity
import com.parkimed.ai.tasks.VoiceTaskActivity
import com.parkimed.ai.tasks.WalkTaskActivity
import com.parkimed.ai.ui.HistoryActivity
import com.parkimed.ai.ui.MedicationActivity
import com.parkimed.ai.ui.OnboardingActivity
import com.parkimed.ai.ui.ResultsActivity
import com.parkimed.ai.ui.SettingsActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = UserPrefs(this)
        if (!prefs.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        setContentView(R.layout.activity_main)

        val txtDate = findViewById<TextView>(R.id.txtDate)
        txtDate.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())

        // Header gear -> Settings
        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.txtNextDose).text = buildString {
            val med = prefs.medicationName.ifBlank { "Levodopa" }
            val dose = prefs.medicationDose.ifBlank { "100mg" }
            append(med); if (dose.isNotBlank()) append(" $dose")
        }
        findViewById<TextView>(R.id.txtDoseTime).text = prefs.doseTime.ifBlank { "08:00" }

        val store = LocalStore(this)
        CoroutineScope(Dispatchers.IO).launch {
            val recent = store.recent(1)
            runOnUiThread {
                if (recent.isNotEmpty()) {
                    val r = recent.first()
                    val isOn = r.onOff == "ON"
                    findViewById<TextView>(R.id.txtState).text = if (isOn) "ON State" else "OFF State"
                    findViewById<TextView>(R.id.txtSeverity).text = String.format(Locale.getDefault(), "%.1f / 5.0", r.severity)
                    findViewById<com.parkimed.ai.ui.SeverityGaugeView>(R.id.gaugeSeverity).setSeverity(r.severity)
                    findViewById<TextView>(R.id.txtLastChecked).text = "Last assessed: " +
                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(r.timestamp)) +
                        "  ·  via ${r.sourcesUsed}"
                    findViewById<TextView>(R.id.txtStatusChip).text = r.onOff
                    findViewById<TextView>(R.id.txtStatusChip).setTextColor(
                        ContextCompat.getColor(this@MainActivity, if (isOn) R.color.state_on else R.color.state_off)
                    )
                    findViewById<TextView>(R.id.txtStatusDesc).text = if (isOn)
                        "Motor symptoms controlled. Voice & kinetic tests within healthy thresholds."
                    else
                        "Motor tremor or bradykinesia elevated. Check medication schedule."
                    findViewById<TextView>(R.id.txtState).setTextColor(
                        ContextCompat.getColor(this@MainActivity, if (isOn) R.color.state_on_dark else R.color.state_off)
                    )
                    findViewById<TextView>(R.id.txtLastChecked).setTextColor(
                        ContextCompat.getColor(this@MainActivity, if (isOn) R.color.state_on_dark else R.color.state_off)
                    )
                    findViewById<TextView>(R.id.txtStatusDesc).setTextColor(
                        ContextCompat.getColor(this@MainActivity, if (isOn) R.color.state_on_dark else R.color.state_off)
                    )
                    findViewById<LinearLayout>(R.id.cardStatus).setBackgroundResource(if (isOn) R.drawable.bg_on_state else R.drawable.bg_off_state)
                }
            }
        }

        findViewById<LinearLayout>(R.id.btnVoice).setOnClickListener {
            startActivity(Intent(this, VoiceTaskActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnMove).setOnClickListener {
            startActivity(Intent(this, WalkTaskActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnTap).setOnClickListener {
            startActivity(Intent(this, TapTaskActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnSpiral).setOnClickListener {
            startActivity(Intent(this, SpiralTaskActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnResults).setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnMedication).setOnClickListener {
            startActivity(Intent(this, MedicationActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        requestPermissionsOnFirstRun()
    }

    private fun requestPermissionsOnFirstRun() {
        val missing = listOf(
            Manifest.permission.RECORD_AUDIO to "audio tasks",
            Manifest.permission.CAMERA to "pill detection",
        ).filter { ContextCompat.checkSelfPermission(this, it.first) != PackageManager.PERMISSION_GRANTED }

        if (missing.isNotEmpty()) {
            permLauncher.launch(missing.map { it.first }.toTypedArray())
        }
    }
}