package com.smartscheduler.app.data.model

import com.google.gson.annotations.SerializedName

data class FeedbackRequestDto(
    @SerializedName("task_name") val taskName: String,
    @SerializedName("planned_start") val plannedStart: Int,
    @SerializedName("planned_end") val plannedEnd: Int,
    @SerializedName("planned_duration") val plannedDuration: Int,
    @SerializedName("actual_duration") val actualDuration: Int,
    @SerializedName("completed") val completed: String,
    @SerializedName("feedback") val feedback: String
)

data class FeedbackResponseDto(
    @SerializedName("message") val message: String,
    @SerializedName("preference_recognized") val preferenceRecognized: Boolean? = false,
    @SerializedName("preference") val preference: String? = null,
    @SerializedName("preference_type") val preferenceType: String? = null
)

