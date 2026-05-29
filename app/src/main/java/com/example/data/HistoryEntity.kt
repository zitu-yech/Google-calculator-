package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val result: String,
    val type: String, // "calculation", "conversion"
    val timestamp: Long = System.currentTimeMillis()
)
