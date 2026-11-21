package com.smartscheduler.app.data.repository

import com.smartscheduler.app.auth.TokenManager
import com.smartscheduler.app.data.api.ApiService
import com.smartscheduler.app.data.model.*
import com.smartscheduler.app.util.Resource

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun signup(request: SignupRequest): Resource<MessageResponse> {
        return try {
            val response = apiService.signup(request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error(response.errorBody()?.string() ?: "Registration failed", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network connection error")
        }
    }

    suspend fun login(request: LoginRequest): Resource<AuthResponse> {
        return try {
            val response = apiService.login(request)
            if (response.isSuccessful && response.body() != null) {
                val authBody = response.body()!!
                tokenManager.saveToken(authBody.accessToken)
                Resource.Success(authBody)
            } else {
                Resource.Error(response.errorBody()?.string() ?: "Invalid username or password", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network connection error")
        }
    }

    suspend fun checkHealth(): Resource<StatusResponse> {
        return try {
            val response = apiService.getHealth()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Backend health check failed", response.code())
            }
        } catch (e: Exception) {
            Resource.Error("Could not connect to backend at 10.0.2.2:8000")
        }
    }

    fun logout() {
        tokenManager.clearToken()
    }
}
