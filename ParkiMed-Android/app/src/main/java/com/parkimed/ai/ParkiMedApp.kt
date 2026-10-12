package com.parkimed.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.parkimed.ai.data.SyncWorker
import java.util.concurrent.TimeUnit

class ParkiMedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                "reminders",
                "Medication reminders",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )

        // Diagnostic: model load must succeed on-device (see index 0 of logcat).
        com.parkimed.ai.model.ParkiMedModel.probe(this)

        // Firebase is optional: initialize only if google-services.json shipped.
        // Without it the app stays fully offline (SyncWorker no-ops).
        val firebaseReady = try {
            com.google.firebase.FirebaseApp.initializeApp(this) != null
        } catch (e: Exception) {
            false // not configured — offline-only mode
        }

        // Periodic cloud sync (runs only when online; no-op when Firebase absent).
        if (firebaseReady) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "parkimed_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}