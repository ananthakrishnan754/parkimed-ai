package com.parkimed.ai.data

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AssessmentRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assessmentDao(): AssessmentDao
}

/**
 * Offline-first helper: every model prediction is stored locally first,
 * then uploaded to Firebase by SyncWorker when the device is online.
 */
class LocalStore(context: Context) {
    private val dao = AppDatabaseProvider.get(context).assessmentDao()

    suspend fun save(record: AssessmentRecord) = dao.insert(record)
    suspend fun recent(limit: Int) = dao.recent(limit)
    suspend fun history() = dao.all()
    suspend fun markPosted(id: Long) = dao.markPosted(id)

    /**
     * Persists a completed assessment. [perModality] order is
     * (voiceP, motionP, tapP, spiralP); 0.5 = modality not used.
     */
    suspend fun record(
        pred: com.parkimed.ai.model.ParkiMedModel.Prediction,
        sources: String,
        perModality: FloatArray,
    ) {
        dao.insert(
            AssessmentRecord(
                timestamp = System.currentTimeMillis(),
                onOff = pred.onOff,
                severity = pred.severity,
                confidence = pred.confidence,
                voiceP = perModality.getOrElse(0) { 0.5f },
                motionP = perModality.getOrElse(1) { 0.5f },
                tapP = perModality.getOrElse(2) { 0.5f },
                spiralP = perModality.getOrElse(3) { 0.5f },
                sourcesUsed = sources,
            )
        )
    }
}