package com.parkimed.ai.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        findViewById<android.widget.Button>(R.id.btnSetReminder).setOnClickListener {
            scheduleDailyReminder()
        }
        findViewById<android.widget.Button>(R.id.btnLogPill).setOnClickListener {
            startActivity(Intent(this, com.parkimed.ai.camera.PillCaptureActivity::class.java))
        }
    }

    private fun scheduleDailyReminder() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 8:00 AM daily
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 8)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            AlarmManager.INTERVAL_DAY, pi)
        Toast.makeText(this, "Daily reminder set for 8:00 AM", Toast.LENGTH_SHORT).show()
    }
}