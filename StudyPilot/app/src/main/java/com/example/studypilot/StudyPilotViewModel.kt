package com.example.studypilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.studypilot.data.StudyTask
import com.example.studypilot.data.StudyTaskRepository
import com.example.studypilot.data.User
import com.example.studypilot.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudyPilotViewModel(
    private val repository: StudyTaskRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    val tasks = repository.allTasks
        .map { list -> list.filter { !it.subject.equals("english", ignoreCase = true) } }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
        )

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authError.value = null
            val result = userRepository.login(email, pass)
            result.onSuccess { user ->
                _currentUser.value = user
                onSuccess()
            }.onFailure { err ->
                _authError.value = err.message ?: "Login failed"
            }
        }
    }

    fun register(username: String, email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authError.value = null
            val result = userRepository.register(username, email, pass)
            result.onSuccess { user ->
                _currentUser.value = user
                onSuccess()
            }.onFailure { err ->
                _authError.value = err.message ?: "Registration failed"
            }
        }
    }

    fun logout() {
        _currentUser.value = null
    }

    fun addTask(subject: String, topic: String, minutes: Int, priority: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.add(
                StudyTask(
                    subject = subject.trim(),
                    topic = topic.trim(),
                    durationMinutes = minutes,
                    priority = priority
                )
            )
            onDone()
        }
    }

    fun updateTask(task: StudyTask) {
        viewModelScope.launch { repository.update(task) }
    }

    fun deleteTask(task: StudyTask, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.delete(task)
            onDone()
        }
    }

    suspend fun findTask(id: Int): StudyTask? = repository.getTask(id)

    fun toggleCompleted(task: StudyTask) {
        viewModelScope.launch { repository.update(task.copy(completed = !task.completed)) }
    }

    fun setCompleted(task: StudyTask, completed: Boolean) {
        viewModelScope.launch { repository.update(task.copy(completed = completed)) }
    }
}

class StudyPilotViewModelFactory(
    private val repository: StudyTaskRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyPilotViewModel::class.java)) {
            return StudyPilotViewModel(repository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
