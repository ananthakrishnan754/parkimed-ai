package com.parkimed.ai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AssessmentRecord)

    @Query("SELECT * FROM assessments ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AssessmentRecord>

    @Query("SELECT * FROM assessments ORDER BY timestamp DESC")
    suspend fun all(): List<AssessmentRecord>

    @Query("UPDATE assessments SET postedToCloud = 1 WHERE id = :id")
    suspend fun markPosted(id: Long)
}