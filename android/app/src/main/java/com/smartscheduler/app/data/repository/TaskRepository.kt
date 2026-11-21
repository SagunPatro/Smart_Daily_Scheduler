package com.smartscheduler.app.data.repository

import com.smartscheduler.app.data.api.ApiService
import com.smartscheduler.app.data.model.*
import com.smartscheduler.app.util.Resource
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TaskRepository(private val apiService: ApiService) {

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

    suspend fun getTasks(): Resource<List<TaskDto>> {
        return try {
            val response = apiService.getTasks()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to fetch tasks")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun createTask(task: TaskCreateDto): Resource<TaskDto> {
        return try {
            val response = apiService.createTask(task)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to create task")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun updateTask(taskId: Int, task: TaskUpdateDto): Resource<TaskDto> {
        return try {
            val response = apiService.updateTask(taskId, task)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to update task")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }

    suspend fun deleteTask(taskId: Int): Resource<MessageResponse> {
        return try {
            val response = apiService.deleteTask(taskId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorBody(response.errorBody()?.string(), "Failed to delete task")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(handleNetworkException(e))
        }
    }
}
