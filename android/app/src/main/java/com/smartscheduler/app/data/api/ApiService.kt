package com.smartscheduler.app.data.api

import com.smartscheduler.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit 2 interface mapping all 13 REST API endpoints defined in ANDROID_API_CONTRACT.md.
 */
interface ApiService {

    @GET(ApiConstants.ENDPOINT_HEALTH)
    suspend fun getHealth(): Response<StatusResponse>

    @GET(ApiConstants.ENDPOINT_ROOT)
    suspend fun getRootStatus(): Response<StatusResponse>

    @POST(ApiConstants.ENDPOINT_SIGNUP)
    suspend fun signup(@Body request: SignupRequest): Response<MessageResponse>

    @POST(ApiConstants.ENDPOINT_LOGIN)
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    // Task CRUD
    @GET(ApiConstants.ENDPOINT_TASKS)
    suspend fun getTasks(): Response<List<TaskDto>>

    @POST(ApiConstants.ENDPOINT_TASKS)
    suspend fun createTask(@Body request: TaskCreateDto): Response<TaskDto>

    @PUT("${ApiConstants.ENDPOINT_TASKS}/{taskId}")
    suspend fun updateTask(
        @Path("taskId") taskId: Int,
        @Body request: TaskUpdateDto
    ): Response<TaskDto>

    @DELETE("${ApiConstants.ENDPOINT_TASKS}/{taskId}")
    suspend fun deleteTask(@Path("taskId") taskId: Int): Response<MessageResponse>

    // Schedule Endpoints
    @POST(ApiConstants.ENDPOINT_SCHEDULE)
    suspend fun generateSchedule(@Body request: ScheduleRequestDto): Response<ScheduleResponseDto>

    @GET(ApiConstants.ENDPOINT_SCHEDULES)
    suspend fun getSchedules(): Response<List<SavedScheduleResponseDto>>

    @GET("${ApiConstants.ENDPOINT_SCHEDULES}/{scheduleId}")
    suspend fun getScheduleById(@Path("scheduleId") scheduleId: Int): Response<SavedScheduleResponseDto>

    @DELETE(ApiConstants.ENDPOINT_SCHEDULES)
    suspend fun clearAllSchedules(): Response<MessageResponse>

    @DELETE("${ApiConstants.ENDPOINT_SCHEDULES}/{scheduleId}")
    suspend fun deleteSchedule(@Path("scheduleId") scheduleId: Int): Response<MessageResponse>

    // Feedback Endpoint
    @POST(ApiConstants.ENDPOINT_FEEDBACK)
    suspend fun submitFeedback(@Body request: FeedbackRequestDto): Response<FeedbackResponseDto>

    // ML Retraining Endpoint
    @POST(ApiConstants.ENDPOINT_RETRAIN)
    suspend fun retrainModel(): Response<RetrainResponseDto>

    // ML Status / Analytics Endpoint
    @GET(ApiConstants.ENDPOINT_ML_STATUS)
    suspend fun getMLStatus(): Response<MLStatusResponseDto>
}

