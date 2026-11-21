package com.smartscheduler.app.data.model

import com.google.gson.annotations.SerializedName

data class ScheduleRequestDto(
    @SerializedName("day_start") val dayStart: Int, // minutes from midnight
    @SerializedName("day_end") val dayEnd: Int, // minutes from midnight
    @SerializedName("tasks") val tasks: List<TaskCreateDto>,
    @SerializedName("title") val title: String? = "Daily Schedule",
    @SerializedName("save_to_history") val saveToHistory: Boolean = true
)

data class ScheduleItemDto(
    @SerializedName("start") val start: Int,
    @SerializedName("end") val end: Int,
    @SerializedName("title") val title: String,
    @SerializedName("category") val category: String,
    @SerializedName("formatted") val formatted: String? = null
)

data class ScheduleResponseDto(
    @SerializedName("schedule_id") val scheduleId: Int? = null,
    @SerializedName("schedule") val schedule: List<ScheduleItemDto>,
    @SerializedName("warnings") val warnings: List<String> = emptyList()
)

data class SavedScheduleResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("day_start") val dayStart: Int,
    @SerializedName("day_end") val dayEnd: Int,
    @SerializedName("schedule_data") val scheduleData: ScheduleResponseDto,
    @SerializedName("created_at") val createdAt: String
)

data class RetrainResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("sample_count") val sampleCount: Int,
    @SerializedName("message") val message: String
)

data class MLStatusResponseDto(
    @SerializedName("total_feedback_count") val totalFeedbackCount: Int,
    @SerializedName("user_feedback_count") val userFeedbackCount: Int,
    @SerializedName("personalized_task_count") val personalizedTaskCount: Int,
    @SerializedName("personalized_tasks") val personalizedTasks: List<String>,
    @SerializedName("model_last_trained") val modelLastTrained: String?,
    @SerializedName("is_model_cached") val isModelCached: Boolean
)

