package com.example.studypilot.data

class StudyTaskRepository(private val dao: StudyTaskDao) {
    val allTasks = dao.observeAllTasks()
    suspend fun getTask(id: Int) = dao.getTaskById(id)
    suspend fun add(task: StudyTask) = dao.insertTask(task)
    suspend fun update(task: StudyTask) = dao.updateTask(task)
    suspend fun delete(task: StudyTask) = dao.deleteTask(task)
}
