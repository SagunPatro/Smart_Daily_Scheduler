package com.smartscheduler.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Foundation : Screen("foundation")
    object Scheduler : Screen("scheduler")
    object ScheduleHistory : Screen("schedule_history")
    object SavedScheduleDetail : Screen("saved_schedule_detail/{scheduleId}") {
        fun createRoute(scheduleId: Int) = "saved_schedule_detail/$scheduleId"
    }
}
