package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val company: String,
    val address: String,
    val hourlyRate: Double = 0.0,
    val colorHex: String = "#5D9CEC",
    val notes: String = ""
)
