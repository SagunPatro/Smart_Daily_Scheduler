package com.smartscheduler.app.data.model

import com.google.gson.annotations.SerializedName

data class SignupRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
    @SerializedName("username") val username: String
)

data class MessageResponse(
    @SerializedName("message") val message: String
)

data class StatusResponse(
    @SerializedName("status") val status: String
)
