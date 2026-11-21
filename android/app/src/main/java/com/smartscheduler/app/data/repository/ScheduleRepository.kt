package com.smartscheduler.app.data.repository

import com.smartscheduler.app.data.api.ApiService
import com.smartscheduler.app.data.model.*
import com.smartscheduler.app.util.Resource
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ScheduleRepository(private val apiService: ApiService) {

    private fun parseErrorBody(errorString: String?, defaultMsg: String): String {
        if (errorString.isNullOrBlank()) return defaultMsg
        return try {
            val json = JSONObject(errorString)
            if (json.has("detail")) json.getString("detail") else errorString
        } catch (e: Exception) {
            errorString
        }
    }

    private fun handleNetworkException(e: Exception): String {
        return when (e) {
            is ConnectException, is UnknownHostException, is SocketTimeoutException, is IOException -> {
                "Unable to connect to the scheduler server. Make sure the backend is running."
            }
            else -> e.localizedMessage ?: "Network connection error"
        }
    }

    suspend fun generateSchedule(request: ScheduleRequestDto): Resource<ScheduleResponseDto> {
        return try {
            val response = apiService.generateSchedule(request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to generate schedule")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun getSchedules(): Resource<List<SavedScheduleResponseDto>> {
        return try {
            val response = apiService.getSchedules()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to fetch schedules")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun getScheduleById(scheduleId: Int): Resource<SavedScheduleResponseDto> {
        return try {
            val response = apiService.getScheduleById(scheduleId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to fetch schedule")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun clearAllSchedules(): Resource<MessageResponse> {
        return try {
            val response = apiService.clearAllSchedules()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to clear schedule history")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun deleteSchedule(scheduleId: Int): Resource<MessageResponse> {
        return try {
            val response = apiService.deleteSchedule(scheduleId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to delete schedule")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun submitFeedback(feedback: FeedbackRequestDto): Resource<FeedbackResponseDto> {
        return try {
            val response = apiService.submitFeedback(feedback)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to submit feedback")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun retrainModel(): Resource<RetrainResponseDto> {
        return try {
            val response = apiService.retrainModel()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to retrain model")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun getMLStatus(): Resource<MLStatusResponseDto> {
        return try {
            val response = apiService.getMLStatus()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to fetch ML analytics status")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }
}

