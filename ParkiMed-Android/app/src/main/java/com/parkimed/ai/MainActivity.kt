package com.parkimed.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.parkimed.ai.tasks.SpiralTaskActivity
import com.parkimed.ai.tasks.TapTaskActivity
import com.parkimed.ai.tasks.VoiceTaskActivity
import com.parkimed.ai.tasks.WalkTaskActivity
import com.parkimed.ai.ui.HistoryActivity
import com.parkimed.ai.ui.MedicationActivity
import com.parkimed.ai.ui.ResultsActivity

class MainActivity : AppCompatActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.widget.Button>(R.id.btnVoice).setOnClickListener {
            startActivity(Intent(this, VoiceTaskActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnMove).setOnClickListener {
            startActivity(Intent(this, WalkTaskActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnTap).setOnClickListener {
            startActivity(Intent(this, TapTaskActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnSpiral).setOnClickListener {
            startActivity(Intent(this, SpiralTaskActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnResults).setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnMedication).setOnClickListener {
            startActivity(Intent(this, MedicationActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnHistory).setOnClickListener {
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