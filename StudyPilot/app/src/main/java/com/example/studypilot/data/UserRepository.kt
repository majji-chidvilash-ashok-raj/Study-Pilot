package com.example.studypilot.data

import com.example.studypilot.api.StudyPilotApiService
import com.example.studypilot.api.LoginRequest
import com.example.studypilot.api.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(
    private val userDao: UserDao,
    private val apiService: StudyPilotApiService = StudyPilotApiService.create()
) {
    suspend fun register(username: String, email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val existing = userDao.getUserByEmail(email)
            if (existing != null) {
                return@withContext Result.failure(Exception("Email already registered"))
            }
            // Simple secure hash simulation (in production use BCrypt/Argon2)
            val hash = password.hashCode().toString()
            val user = User(username = username, email = email, passwordHash = hash, token = "token_${System.currentTimeMillis()}")
            userDao.insertUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserByEmail(email)
            if (user == null) {
                return@withContext Result.failure(Exception("User not found"))
            }
            val hash = password.hashCode().toString()
            if (user.passwordHash != hash) {
                return@withContext Result.failure(Exception("Invalid password"))
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
