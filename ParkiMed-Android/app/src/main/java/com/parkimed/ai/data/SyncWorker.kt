package com.parkimed.ai.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Online-only worker: pushes pending local records to Firestore so the
 * doctor dashboard can read them. The app remains fully functional offline —
 * sync happens only when this runs while connected AND Firebase is configured
 * (i.e. google-services.json is present).
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Firebase not configured (no google-services.json) -> nothing to do.
        val app = try {
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            null
        }
        if (app == null) return@withContext Result.success()

        try {
            val store = LocalStore(applicationContext)
            val deviceId = AppIdentifiers.deviceId(applicationContext)
            val db = FirebaseFirestore.getInstance()
            val pending = store.recent(200).filter { !it.postedToCloud }
            if (pending.isEmpty()) return@withContext Result.success()

            pending.forEach { rec ->
                db.collection("assessments").add(
                    mapOf(
                        "deviceId" to deviceId,
                        "timestamp" to rec.timestamp,
                        "onOff" to rec.onOff,
                        "severity" to rec.severity,
                        "confidence" to rec.confidence,
                        "voiceP" to rec.voiceP,
                        "motionP" to rec.motionP,
                        "tapP" to rec.tapP,
                        "spiralP" to rec.spiralP,
                        "sourcesUsed" to rec.sourcesUsed,
                    )
                ).await()
                store.markPosted(rec.id)
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}