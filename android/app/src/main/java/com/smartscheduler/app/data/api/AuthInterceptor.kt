package com.smartscheduler.app.data.api

import com.smartscheduler.app.auth.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        val token = tokenManager.getToken()
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        // Handle HTTP 401 Unauthorized globally by delegating session expiration to TokenManager
        // Only trigger session expiration if a token was actually present and rejected
        if (response.code == 401 && !token.isNullOrBlank()) {
            tokenManager.onSessionExpired()
        }

        return response
    }
}
