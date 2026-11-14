package com.example.studypilot.api

import com.example.studypilot.data.StudyTask
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class LoginRequest(val email: String, val passwordHash: String)
data class RegisterRequest(val username: String, val email: String, val passwordHash: String)
data class AuthResponse(val token: String, val username: String, val email: String)

interface StudyPilotApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("api/tasks")
    suspend fun getTasks(@Header("Authorization") token: String): Response<List<StudyTask>>

    @POST("api/tasks")
    suspend fun syncTask(@Header("Authorization") token: String, @Body task: StudyTask): Response<StudyTask>

    companion object {
        private const val BASE_URL = "https://api.studypilot.example.com/"

        fun create(): StudyPilotApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(StudyPilotApiService::class.java)
        }
    }
}

