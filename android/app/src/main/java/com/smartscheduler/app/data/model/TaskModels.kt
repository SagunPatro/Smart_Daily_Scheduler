package com.smartscheduler.app.data.model

import com.google.gson.annotations.SerializedName

data class TaskCreateDto(
    @SerializedName("task_name") val taskName: String,
    @SerializedName("duration") val duration: Int, // minutes
    @SerializedName("is_fixed") val isFixed: Boolean = false,
    @SerializedName("fixed_start") val fixedStart: Int? = null, // minutes from midnight
    @SerializedName("fixed_end") val fixedEnd: Int? = null // minutes from midnight
)

data class TaskUpdateDto(
    @SerializedName("task_name") val taskName: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("is_fixed") val isFixed: Boolean? = null,
    @SerializedName("fixed_start") val fixedStart: Int? = null,
    @SerializedName("fixed_end") val fixedEnd: Int? = null
)

data class TaskDto(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("task_name") val taskName: String,
    @SerializedName("duration") val duration: Int,
    @SerializedName("is_fixed") val isFixed: Boolean = false,
    @SerializedName("fixed_start") val fixedStart: Int? = null,
    @SerializedName("fixed_end") val fixedEnd: Int? = null
)
