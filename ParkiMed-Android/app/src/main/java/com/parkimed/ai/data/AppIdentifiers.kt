package com.parkimed.ai.data

import android.content.Context
import java.util.UUID

/** Stable per-install identifier used to group assessments in the doctor dashboard. */
object AppIdentifiers {
    private const val PREFS = "parkimed_ids"
    private const val KEY_DEVICE = "device_id"

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE, null)
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE, fresh).apply()
        return fresh
    }
}