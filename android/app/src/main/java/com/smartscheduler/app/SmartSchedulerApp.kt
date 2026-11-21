package com.smartscheduler.app

import android.app.Application
import com.smartscheduler.app.auth.TokenManager
import com.smartscheduler.app.data.api.ApiConstants
import com.smartscheduler.app.data.api.ApiService
import com.smartscheduler.app.data.api.AuthInterceptor
import com.smartscheduler.app.data.repository.AuthRepository
import com.smartscheduler.app.data.repository.ScheduleRepository
import com.smartscheduler.app.data.repository.TaskRepository
import com.smartscheduler.app.data.session.ScheduleSessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class SmartSchedulerApp : Application() {

    lateinit var tokenManager: TokenManager
        private set

    lateinit var apiService: ApiService
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var taskRepository: TaskRepository
        private set

    lateinit var scheduleRepository: ScheduleRepository
        private set

    lateinit var scheduleSessionManager: ScheduleSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        tokenManager = TokenManager(this)
        scheduleSessionManager = ScheduleSessionManager(this)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)

        authRepository = AuthRepository(apiService, tokenManager)
        taskRepository = TaskRepository(apiService)
        scheduleRepository = ScheduleRepository(apiService)
    }

    companion object {
        lateinit var instance: SmartSchedulerApp
            private set
    }
}
