package com.parkimed.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assessments")
data class AssessmentRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val onOff: String,            // "ON" or "OFF"
    val severity: Float,          // 0..5
    val confidence: Float,        // 0..1
    val voiceP: Float,            // per-modality ON probabilities (for explainability)
    val motionP: Float,
    val tapP: Float,
    val spiralP: Float,
    val sourcesUsed: String,      // e.g. "voice,motion,tap"
    val postedToCloud: Boolean = false,
)