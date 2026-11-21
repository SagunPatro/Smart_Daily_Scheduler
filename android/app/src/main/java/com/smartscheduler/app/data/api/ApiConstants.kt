package com.smartscheduler.app.data.api

object ApiConstants {
    // 10.0.2.2 points to host machine localhost (127.0.0.1) in Android Emulator
    const val BASE_URL = "http://10.0.2.2:8000/"

    const val ENDPOINT_HEALTH = "health"
    const val ENDPOINT_ROOT = ""
    const val ENDPOINT_SIGNUP = "signup"
    const val ENDPOINT_LOGIN = "login"
    const val ENDPOINT_TASKS = "tasks"
    const val ENDPOINT_SCHEDULE = "schedule"
    const val ENDPOINT_SCHEDULES = "schedules"
    const val ENDPOINT_FEEDBACK = "feedback"
    const val ENDPOINT_RETRAIN = "retrain"
    const val ENDPOINT_ML_STATUS = "ml/status"
}
