package com.parkimed.ai.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.parkimed.ai.R

/** Shows the daily medication reminder notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val n = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ParkiMed — Medication Time")
            .setContentText("Time for your Parkinson's medication. Log your intake.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted yet — ignore
        }
    }
}