package com.example.studypilot.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks ORDER BY completed ASC, createdAt DESC")
    fun observeAllTasks(): Flow<List<StudyTask>>

    @Query("SELECT * FROM study_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Int): StudyTask?

    @Insert
    suspend fun insertTask(task: StudyTask)

    @Update
    suspend fun updateTask(task: StudyTask)

    @Delete
    suspend fun deleteTask(task: StudyTask)
}
