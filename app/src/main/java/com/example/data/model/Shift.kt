package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val jobId: Int,
    val date: String,          // Format: YYYY-MM-DD
    val startTime: String,     // Format: HH:MM (24h)
    val endTime: String,       // Format: HH:MM (24h)
    val notes: String = "",
    val transitTimeToNext: Int? = null,      // Trip travel duration in minutes
    val transitDistanceToNext: Double? = null, // Trip distance in miles
    val transitMode: String? = null,          // "Driving", "Transit", "Bicycling", "Walking"
    val transitSummary: String? = null,       // Route summary
    val isAIEstimated: Boolean = false
)
