package com.smartscheduler.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartscheduler.app.SmartSchedulerApp
import com.smartscheduler.app.ui.screens.auth.AuthViewModel
import com.smartscheduler.app.ui.screens.auth.LoginScreen
import com.smartscheduler.app.ui.screens.auth.SignupScreen
import com.smartscheduler.app.ui.screens.foundation.FoundationScreen
import com.smartscheduler.app.ui.screens.foundation.FoundationViewModel
import com.smartscheduler.app.ui.screens.history.SavedScheduleDetailScreen
import com.smartscheduler.app.ui.screens.history.ScheduleHistoryScreen
import com.smartscheduler.app.ui.screens.history.ScheduleHistoryViewModel
import com.smartscheduler.app.ui.screens.scheduler.TaskSchedulerScreen
import com.smartscheduler.app.ui.screens.scheduler.TaskSchedulerViewModel

@Composable
fun NavGraph(
    navController: NavHostController
) {
    val app = SmartSchedulerApp.instance
    val authViewModel = remember { AuthViewModel(app.authRepository, app.tokenManager) }
    val foundationViewModel = remember { FoundationViewModel(app.apiService, app.tokenManager) }
    val schedulerViewModel = remember { TaskSchedulerViewModel(app.taskRepository, app.scheduleRepository, app.tokenManager, app.scheduleSessionManager) }
    val historyViewModel = remember { ScheduleHistoryViewModel(app.scheduleRepository, app.tokenManager) }

    // Dynamic session start destination: if logged in, go straight to Scheduler screen; else Login screen
    val startDestination = if (app.tokenManager.getToken().isNullOrBlank()) {
        Screen.Login.route
    } else {
        Screen.Scheduler.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                navController = navController
            )
        }

        composable(Screen.Signup.route) {
            SignupScreen(
                viewModel = authViewModel,
                navController = navController
            )
        }

        composable(Screen.Scheduler.route) {
            TaskSchedulerScreen(
                viewModel = schedulerViewModel,
                navController = navController
            )
        }

        composable(Screen.ScheduleHistory.route) {
            ScheduleHistoryScreen(
                viewModel = historyViewModel,
                navController = navController
            )
        }

        composable(
            route = Screen.SavedScheduleDetail.route,
            arguments = listOf(navArgument("scheduleId") { type = NavType.IntType })
        ) { backStackEntry ->
            val scheduleId = backStackEntry.arguments?.getInt("scheduleId") ?: 0
            SavedScheduleDetailScreen(
                scheduleId = scheduleId,
                viewModel = historyViewModel,
                navController = navController
            )
        }

        composable(Screen.Foundation.route) {
            FoundationScreen(
                viewModel = foundationViewModel,
                authViewModel = authViewModel,
                navController = navController
            )
        }
    }
}
