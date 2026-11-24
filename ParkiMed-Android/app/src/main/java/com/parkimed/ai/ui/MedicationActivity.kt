package com.parkimed.ai.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.parkimed.ai.R

/**
 * Medication reminder: sets a repeating daily reminder via AlarmManager
 * (works fully offline). Pill-intake confirmation logs to Room via
 * PillCaptureActivity.
 */
class MedicationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnSetReminder).setOnClickListener {
            scheduleDailyReminder()
        }
        findViewById<MaterialButton>(R.id.btnLogPill).setOnClickListener {
            startActivity(Intent(this, com.parkimed.ai.camera.PillCaptureActivity::class.java))
        }
    }

    private fun scheduleDailyReminder() {
        val prefs = com.parkimed.ai.data.UserPrefs(this)
        val parts = prefs.doseTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
        }
        am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            AlarmManager.INTERVAL_DAY, pi)
        Toast.makeText(this, "Daily reminder set for %02d:%02d".format(hour, minute), Toast.LENGTH_SHORT).show()
    }
}