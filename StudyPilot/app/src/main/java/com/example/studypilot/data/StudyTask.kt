package com.example.studypilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_tasks")
data class StudyTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subject: String,
    val topic: String,
    val durationMinutes: Int,
    val priority: String,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
