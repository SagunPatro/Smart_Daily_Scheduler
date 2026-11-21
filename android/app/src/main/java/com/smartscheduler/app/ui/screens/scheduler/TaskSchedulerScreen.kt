package com.smartscheduler.app.ui.screens.scheduler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.smartscheduler.app.data.model.MLStatusResponseDto
import com.smartscheduler.app.data.model.ScheduleItemDto
import com.smartscheduler.app.data.model.ScheduleResponseDto
import com.smartscheduler.app.data.model.TaskDto
import com.smartscheduler.app.ui.components.WavyHeaderDivider
import com.smartscheduler.app.ui.navigation.Screen
import com.smartscheduler.app.ui.theme.*
import com.smartscheduler.app.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSchedulerScreen(
    viewModel: TaskSchedulerViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var isMenuExpanded by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAndResetScheduleIfExpired()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadTasks()
        viewModel.loadMLStatus()
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is SchedulerNavigationEvent.NavigateToLogin -> {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Scheduler.route) { inclusive = true }
                    }
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Smart Daily Scheduler", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Session Status: Logged in / Active",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                        }
                    },
                    actions = {
                        // Refresh button
                        IconButton(onClick = { viewModel.loadTasks(); viewModel.loadMLStatus() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Tasks",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // Hamburger Menu button (Point 8)
                        Box {
                            IconButton(onClick = { isMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Schedule History") },
                                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        isMenuExpanded = false
                                        navController.navigate(Screen.ScheduleHistory.route)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Feedback & Learning") },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        isMenuExpanded = false
                                        viewModel.openFeedbackInfoDialog()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Saved Tasks") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        isMenuExpanded = false
                                        viewModel.openSavedTasksDialog()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Reset Schedule / New Day") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        isMenuExpanded = false
                                        viewModel.resetActiveSchedule()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        isMenuExpanded = false
                                        viewModel.openSettingsDialog()
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        isMenuExpanded = false
                                        viewModel.logout()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                // Wavy partition with smooth color transition beneath top bar
                WavyHeaderDivider(
                    modifier = Modifier.fillMaxWidth(),
                    height = 24.dp
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error Card Banner
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.errorMessage?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearMessages() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("❌", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Success Card Banner
            AnimatedVisibility(
                visible = uiState.successMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.successMessage?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, LavenderBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✅ $msg",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearMessages() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("❌", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Feedback Success Card Banner
            AnimatedVisibility(
                visible = uiState.feedbackSuccessMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.feedbackSuccessMessage?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, LavenderBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✅ $msg",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissFeedbackStatus() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("❌", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Schedule Reset Notice Banner (e.g. Day-End or Morning Auto-Reset)
            AnimatedVisibility(
                visible = uiState.scheduleResetNotice != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.scheduleResetNotice?.let { notice ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)),
                        border = BorderStroke(1.dp, Color(0xFFC084FC))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Notice",
                                tint = PrimaryPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = notice,
                                color = PrimaryPurpleDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissScheduleResetNotice() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("❌", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // SECTION 1: Day Settings Card with Native M3 TimePicker (Point 2)
            DaySettingsCard(
                dayStartMinutes = uiState.dayStartMinutes,
                dayEndMinutes = uiState.dayEndMinutes,
                onSettingsChanged = { start, end -> viewModel.updateDaySettings(start, end) }
            )

            // SECTION 2: Task Management Header & List
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, LavenderBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tasks (${uiState.tasks.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = { viewModel.openAddTaskDialog() },
                            shape = RoundedCornerShape(10.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 3.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Task", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.isLoadingTasks) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (uiState.tasks.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = LavenderBackground,
                            border = BorderStroke(1.dp, LavenderBorder.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No tasks added yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Add Task' above to add your daily flexible or fixed time tasks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.animateContentSize()
                        ) {
                            uiState.tasks.forEach { task ->
                                TaskItemCard(
                                    task = task,
                                    onEdit = { viewModel.openEditTaskDialog(task) },
                                    onDelete = { viewModel.deleteTask(task.id) }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 3: Smart Duration Learning & Analytics (Point 6 Clarification)
            SmartLearningCard(
                isRetraining = uiState.isRetraining,
                retrainMessage = uiState.retrainMessage,
                retrainError = uiState.retrainError,
                mlStatus = uiState.mlStatus,
                onRetrainClick = { viewModel.retrainModel() },
                onDismissMessage = { viewModel.dismissRetrainStatus() }
            )

            // SECTION 4: Generate Schedule Action Button
            Button(
                onClick = { viewModel.generateSchedule() },
                enabled = !uiState.isGeneratingSchedule && uiState.tasks.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (uiState.isGeneratingSchedule) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating Schedule...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Generate Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Daily Schedule", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // SECTION 5: Saved Schedule Display on Screen (if confirmed and saved)
            AnimatedVisibility(
                visible = uiState.scheduleResponse != null,
                enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.scheduleResponse?.let { resp ->
                    ScheduleDisplayCard(
                        scheduleResponse = resp,
                        onFeedbackClick = { item -> viewModel.openFeedbackDialog(item) },
                        onResetScheduleClick = { viewModel.resetActiveSchedule() }
                    )
                }
            }
            if (uiState.scheduleResponse == null && uiState.tasks.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = LavenderBackground,
                    border = BorderStroke(1.dp, LavenderBorder.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Configure your day window and tasks above, then tap 'Generate Daily Schedule'. You will be able to review and confirm saving the schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // DIALOG 1: Add / Edit Task Dialog with M3 TimePicker & Auto-calculated End Time (Points 2, 3, 4)
    if (uiState.isTaskDialogOpen) {
        TaskFormDialog(
            uiState = uiState,
            onNameChange = { viewModel.onFormTaskNameChanged(it) },
            onDurationChange = { viewModel.onFormDurationChanged(it) },
            onIsFixedChange = { viewModel.onFormIsFixedChanged(it) },
            onFixedStartSelected = { viewModel.onFormFixedStartSelected(it) },
            onSave = { viewModel.saveTask() },
            onDismiss = { viewModel.closeTaskDialog() }
        )
    }

    // DIALOG 2: Schedule Preview & Confirmation Dialog (Point 7)
    if (uiState.generatedSchedulePreview != null) {
        ScheduleConfirmationDialog(
            preview = uiState.generatedSchedulePreview!!,
            dayStart = uiState.dayStartMinutes,
            dayEnd = uiState.dayEndMinutes,
            isSaving = uiState.isSavingSchedule,
            onSave = { viewModel.confirmAndSaveSchedule() },
            onDiscard = { viewModel.discardGeneratedSchedule() }
        )
    }

    // DIALOG 3: Task Feedback Submission Modal
    if (uiState.selectedScheduleItemForFeedback != null) {
        FeedbackSubmissionDialog(
            item = uiState.selectedScheduleItemForFeedback!!,
            isSubmitting = uiState.isSubmittingFeedback,
            error = uiState.feedbackError,
            onSubmit = { taskName, start, end, plannedDur, actualDur, completed, note ->
                viewModel.submitTaskFeedback(taskName, start, end, plannedDur, actualDur, completed, note)
            },
            onDismiss = { viewModel.closeFeedbackDialog() }
        )
    }

    // DIALOG 4: Feedback Info Dialog (Hamburger Menu Item)
    if (uiState.isFeedbackInfoDialogOpen) {
        FeedbackInfoDialog(
            mlStatus = uiState.mlStatus,
            onDismiss = { viewModel.closeFeedbackInfoDialog() }
        )
    }

    // DIALOG 5: Saved Tasks Overview Dialog (Hamburger Menu Item)
    if (uiState.isSavedTasksDialogOpen) {
        SavedTasksOverviewDialog(
            tasks = uiState.tasks,
            onDismiss = { viewModel.closeSavedTasksDialog() }
        )
    }

    // DIALOG 6: Settings Dialog (Hamburger Menu Item)
    if (uiState.isSettingsDialogOpen) {
        SettingsDialog(
            dayStartMinutes = uiState.dayStartMinutes,
            dayEndMinutes = uiState.dayEndMinutes,
            onDismiss = { viewModel.closeSettingsDialog() }
        )
    }
}

// -------------------------------------------------------------
// NATIVE MATERIAL 3 TIME PICKER DIALOG (Point 2 - Input Mode)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Material3TimePickerDialog(
    title: String = "Select Time",
    initialMinutes: Int = 540,
    onConfirm: (minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHour = (initialMinutes / 60) % 24
    val initialMinute = initialMinutes % 60
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    val timePickerColors = TimePickerDefaults.colors(
        clockDialColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectorColor = MaterialTheme.colorScheme.primary,
        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
        timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
        periodSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
        periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Clock-style Material 3 TimePicker (Google Calendar style)
                TimePicker(state = timeState, colors = timePickerColors)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val totalMinutes = timeState.hour * 60 + timeState.minute
                    onConfirm(totalMinutes)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// -------------------------------------------------------------
// DAY SETTINGS CARD WITH TIME PICKERS (Point 2)
// -------------------------------------------------------------
@Composable
fun DaySettingsCard(
    dayStartMinutes: Int,
    dayEndMinutes: Int,
    onSettingsChanged: (Int, Int) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LavenderBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Day Window Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Day Start Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showStartPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = LavenderBackground,
                    border = BorderStroke(1.dp, LavenderBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Day Start",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${TimeUtils.minutesToFormattedTime(dayStartMinutes)} (${TimeUtils.minutesTo12HourTime(dayStartMinutes)})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Day End Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEndPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = LavenderBackground,
                    border = BorderStroke(1.dp, LavenderBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Day End",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${TimeUtils.minutesToFormattedTime(dayEndMinutes)} (${TimeUtils.minutesTo12HourTime(dayEndMinutes)})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        Material3TimePickerDialog(
            title = "Set Day Start Time",
            initialMinutes = dayStartMinutes,
            onConfirm = { minutes ->
                showStartPicker = false
                onSettingsChanged(minutes, dayEndMinutes)
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        Material3TimePickerDialog(
            title = "Set Day End Time",
            initialMinutes = dayEndMinutes,
            onConfirm = { minutes ->
                showEndPicker = false
                onSettingsChanged(dayStartMinutes, minutes)
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

// -------------------------------------------------------------
// TASK ITEM CARD
// -------------------------------------------------------------
@Composable
fun TaskItemCard(
    task: TaskDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = PrimaryPurple.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.2.dp, PrimaryPurple.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.taskName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (task.isFixed) FixedTaskBg else FlexibleTaskBg,
                        border = BorderStroke(0.5.dp, if (task.isFixed) FixedTaskText.copy(alpha = 0.25f) else FlexibleTaskText.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = if (task.isFixed) "Fixed" else "Flexible",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (task.isFixed) FixedTaskText else FlexibleTaskText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val timeSpan = if (task.isFixed && task.fixedStart != null && task.fixedEnd != null) {
                    " | ${TimeUtils.minutesTo12HourTime(task.fixedStart)} - ${TimeUtils.minutesTo12HourTime(task.fixedEnd)}"
                } else ""
                Text(
                    text = "Duration: ${TimeUtils.formatDuration(task.duration)}$timeSpan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ADD / EDIT TASK DIALOG (Points 2, 3, 4)
// -------------------------------------------------------------
@Composable
fun TaskFormDialog(
    uiState: TaskSchedulerUiState,
    onNameChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onIsFixedChange: (Boolean) -> Unit,
    onFixedStartSelected: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var showStartTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            Text(
                if (uiState.editingTaskId == null) "Add New Task" else "Edit Task",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                uiState.formError?.let { err ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = err,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.formTaskName,
                    onValueChange = onNameChange,
                    label = { Text("Task Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.formDurationMinutes,
                    onValueChange = onDurationChange,
                    label = { Text("Planned Duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Fixed Time Task?", fontWeight = FontWeight.SemiBold)
                        Text("Task with specific start & end time", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.formIsFixed,
                        onCheckedChange = onIsFixedChange
                    )
                }

                if (uiState.formIsFixed) {
                    // Fixed Start Picker Trigger
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStartTimePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Fixed Start Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${TimeUtils.minutesToFormattedTime(uiState.formFixedStartMinutes)} (${TimeUtils.minutesTo12HourTime(uiState.formFixedStartMinutes)})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            TextButton(onClick = { showStartTimePicker = true }) {
                                Text("Change", fontSize = 12.sp)
                            }
                        }
                    }

                    // READ-ONLY Auto-Calculated End Time (Point 3)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fixed End Time (Auto-calculated)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${TimeUtils.minutesToFormattedTime(uiState.formFixedEndMinutes)} (${TimeUtils.minutesTo12HourTime(uiState.formFixedEndMinutes)})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Start + ${uiState.formDurationMinutes} mins duration",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Task", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showStartTimePicker) {
        Material3TimePickerDialog(
            title = "Select Fixed Start Time",
            initialMinutes = uiState.formFixedStartMinutes,
            onConfirm = { minutes ->
                showStartTimePicker = false
                onFixedStartSelected(minutes)
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
}

// -------------------------------------------------------------
// SCHEDULE PREVIEW & CONFIRMATION DIALOG (Point 7)
// -------------------------------------------------------------
@Composable
fun ScheduleConfirmationDialog(
    preview: ScheduleResponseDto,
    dayStart: Int,
    dayEnd: Int,
    isSaving: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDiscard,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            Column {
                Text("Generated Schedule Preview", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Day Window: ${TimeUtils.minutesToFormattedTime(dayStart)} - ${TimeUtils.minutesToFormattedTime(dayEnd)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (preview.warnings.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningBg)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Warnings:", fontWeight = FontWeight.Bold, color = WarningText, fontSize = 12.sp)
                            preview.warnings.forEach { warning ->
                                Text("• $warning", color = WarningText, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Text(
                    text = "Timeline Blocks (${preview.schedule.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                preview.schedule.forEach { item ->
                    val (bgColor, textColor) = when (item.category.lowercase()) {
                        "task" -> Pair(FlexibleTaskBg, FlexibleTaskText)
                        "fixed_task" -> Pair(FixedTaskBg, FixedTaskText)
                        "break" -> Pair(BreakBg, BreakText)
                        "meal", "breakfast", "lunch", "dinner" -> Pair(MealBg, MealText)
                        else -> Pair(FreeTimeBg, FreeTimeText)
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = bgColor
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.formatted ?: "${TimeUtils.minutesToFormattedTime(item.start)} - ${TimeUtils.minutesToFormattedTime(item.end)} | ${item.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = TimeUtils.formatDuration(item.end - item.start),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Would you like to save this generated schedule to your history?",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Saving...")
                } else {
                    Text("Save Schedule", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDiscard,
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Discard")
            }
        }
    )
}

// -------------------------------------------------------------
// SPIRAL NOTEPAD COMPONENTS (Light Purple Spirals & Bold Note)
// -------------------------------------------------------------
@Composable
fun SpiralBindingHeader(
    modifier: Modifier = Modifier,
    spiralColor: Color = Color(0xFFC084FC),      // Light purple wire
    spiralHighlight: Color = Color(0xFFE9D5FF),  // Soft light purple metallic highlight
    holeColor: Color = Color(0xFF382952)         // Punch hole shadow
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val loopSpacing = 28.dp.toPx()
        val count = (size.width / loopSpacing).toInt().coerceAtLeast(8)
        val step = size.width / count
        val wireWidth = 3.2.dp.toPx()
        val holeRadius = 5.dp.toPx()
        val holeCenterY = size.height * 0.65f

        for (i in 0 until count) {
            val cx = step * (i + 0.5f)

            // 1. Draw paper punch hole (dark circle with inner depth)
            drawCircle(
                color = holeColor.copy(alpha = 0.25f),
                radius = holeRadius + 1.2.dp.toPx(),
                center = Offset(cx, holeCenterY)
            )
            drawCircle(
                color = holeColor.copy(alpha = 0.65f),
                radius = holeRadius,
                center = Offset(cx, holeCenterY)
            )

            // 2. Spiral wire loop coming from above the top edge down through the hole
            val loopWidth = 9.dp.toPx()
            val loopHeight = holeCenterY - 2.dp.toPx()
            val loopLeft = cx - loopWidth / 2f
            val loopTop = 2.dp.toPx()

            // Main wire ring (Light Purple)
            drawRoundRect(
                color = spiralColor,
                topLeft = Offset(loopLeft, loopTop),
                size = Size(loopWidth, loopHeight + holeRadius * 0.6f),
                cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()),
                style = Stroke(width = wireWidth, cap = StrokeCap.Round)
            )

            // Shiny light reflection on top-left of the wire
            drawArc(
                color = spiralHighlight,
                startAngle = 170f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(loopLeft + 1.dp.toPx(), loopTop + 1.dp.toPx()),
                size = Size(loopWidth - 2.dp.toPx(), loopHeight * 0.7f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun NotebookPerforatedLine(
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFC084FC).copy(alpha = 0.45f)
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        drawLine(
            color = lineColor,
            start = Offset(0f, 1.dp.toPx()),
            end = Offset(size.width, 1.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}

// -------------------------------------------------------------
// SAVED SCHEDULE DISPLAY CARD (Spiral Notebook Note Style with Bold Text)
// -------------------------------------------------------------
@Composable
fun ScheduleDisplayCard(
    scheduleResponse: ScheduleResponseDto,
    onFeedbackClick: ((ScheduleItemDto) -> Unit)? = null,
    onResetScheduleClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 14.dp, topEnd = 14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFAF7FD) // Light pastel notepad paper background
        ),
        border = BorderStroke(1.5.dp, Color(0xFFD8B4FE)), // Light purple note border
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Light Purple Spirals at the top of the note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3E8FF).copy(alpha = 0.5f))
                    .padding(vertical = 4.dp)
            ) {
                SpiralBindingHeader(
                    spiralColor = Color(0xFFC084FC),      // Light purple spirals
                    spiralHighlight = Color(0xFFE9D5FF),  // Shiny metallic lavender highlight
                    holeColor = Color(0xFF382952)
                )
            }

            // 2. Perforated tear line beneath the spirals
            NotebookPerforatedLine()

            // 3. Note content with bold text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Notepad Header Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Saved Note Icon",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily Schedule Note",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold, // Bold text on note
                            color = PrimaryPurpleDark,
                            fontSize = 18.sp
                        )
                    }

                    // Bold Saved Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEDE9FE),
                        border = BorderStroke(1.dp, Color(0xFFC4B5FD))
                    ) {
                        Text(
                            text = "✓ SAVED NOTE",
                            fontWeight = FontWeight.ExtraBold, // Bold text
                            fontSize = 11.sp,
                            color = PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Timeline blocks count subtitle in bold
                val nonFreeCount = scheduleResponse.schedule.count { it.category.lowercase() != "free" }
                Text(
                    text = "Active Schedule • $nonFreeCount Scheduled Activities (${scheduleResponse.schedule.size} Total Blocks)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold, // Bold text
                    color = CharcoalMedium
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Warnings section with bold text
                if (scheduleResponse.warnings.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningBg),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "⚠ Note Warnings:",
                                fontWeight = FontWeight.ExtraBold, // Bold text
                                color = WarningText,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            scheduleResponse.warnings.forEach { warning ->
                                Text(
                                    text = "• $warning",
                                    color = WarningText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold // Bold text
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Schedule Note Items
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scheduleResponse.schedule.forEach { item ->
                        ScheduleItemRow(item = item, onFeedbackClick = onFeedbackClick)
                    }
                }

                if (onResetScheduleClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(
                        color = Color(0xFFE9D5FF),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { onResetScheduleClick() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple),
                        border = BorderStroke(1.5.dp, PrimaryPurple.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Schedule",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Active Schedule / Plan New Day",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItemRow(
    item: ScheduleItemDto,
    onFeedbackClick: ((ScheduleItemDto) -> Unit)? = null
) {
    val (bgColor, textColor, accentBarColor) = when (item.category.lowercase()) {
        "task" -> Triple(FlexibleTaskBg, FlexibleTaskText, PrimaryPurple)
        "fixed_task" -> Triple(FixedTaskBg, FixedTaskText, Color(0xFFDC2626))
        "break" -> Triple(BreakBg, BreakText, Color(0xFFD97706))
        "meal", "breakfast", "lunch", "dinner" -> Triple(MealBg, MealText, Color(0xFF16A34A))
        else -> Triple(FreeTimeBg, FreeTimeText, Color(0xFF94A3B8))
    }

    val startFormatted = TimeUtils.minutesToFormattedTime(item.start)
    val endFormatted = TimeUtils.minutesToFormattedTime(item.end)
    val timeRangeText = "$startFormatted - $endFormatted"
    val isFeedbackEligible = item.category.lowercase() in listOf("task", "meal", "breakfast", "lunch", "dinner") ||
        item.title.lowercase() in listOf("breakfast", "lunch", "dinner")
    val duration = item.end - item.start

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Note row indicator stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(accentBarColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeRangeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, // Bold time text
                        color = textColor.copy(alpha = 0.9f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, // Bold title text
                    color = textColor
                )
            }

            if (isFeedbackEligible && onFeedbackClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onFeedbackClick(item) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    border = BorderStroke(1.dp, textColor)
                ) {
                    Text("Feedback", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) // Bold button text
                }
            } else if (duration > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = textColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = TimeUtils.formatDuration(duration),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold, // Bold duration text
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SMART LEARNING CARD (Point 6 Clarification)
// -------------------------------------------------------------
@Composable
fun SmartLearningCard(
    isRetraining: Boolean,
    retrainMessage: String?,
    retrainError: String?,
    mlStatus: MLStatusResponseDto?,
    onRetrainClick: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LavenderBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Duration Learning (ML)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Updates the AI duration-prediction model using your completed task history. (Note: Improves future schedule estimates; does not save the current schedule).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRetrainClick,
                    enabled = !isRetraining,
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 3.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isRetraining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retraining...", fontSize = 12.sp)
                    } else {
                        Text("Retrain Model", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ML Status Analytics Panel
            mlStatus?.let { status ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = LavenderBackground,
                    border = BorderStroke(1.dp, LavenderBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Feedback recorded: ${status.userFeedbackCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Personalized tasks: ${status.personalizedTaskCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (status.personalizedTasks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Personalized for: " + status.personalizedTasks.joinToString(", ") { "• $it" },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        status.modelLastTrained?.let { trainedAt ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Last model update: ${trainedAt.take(19).replace("T", " ")}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = retrainMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                retrainMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, LavenderBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "✅ $msg",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissMessage,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("❌", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = retrainError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                retrainError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "❌ $err",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissMessage,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("❌", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// FEEDBACK SUBMISSION DIALOG
// -------------------------------------------------------------
@Composable
fun FeedbackSubmissionDialog(
    item: ScheduleItemDto,
    isSubmitting: Boolean,
    error: String?,
    onSubmit: (taskName: String, plannedStart: Int, plannedEnd: Int, plannedDur: Int, actualDur: Int, completed: Boolean, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val plannedDuration = item.end - item.start
    var actualDurationInput by remember { mutableStateOf(plannedDuration.toString()) }
    var isCompleted by remember { mutableStateOf(true) }
    var feedbackNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = { Text("Task Feedback: ${item.title}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                error?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = err,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = "Planned Window: ${TimeUtils.minutesToFormattedTime(item.start)} - ${TimeUtils.minutesToFormattedTime(item.end)} ($plannedDuration mins)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = actualDurationInput,
                    onValueChange = { actualDurationInput = it },
                    label = { Text("Actual Duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Task Completed?")
                    Switch(
                        checked = isCompleted,
                        onCheckedChange = { isCompleted = it }
                    )
                }

                OutlinedTextField(
                    value = feedbackNote,
                    onValueChange = { feedbackNote = it },
                    label = { Text("Notes / Feedback (optional)") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val actualDur = actualDurationInput.toIntOrNull() ?: plannedDuration
                    onSubmit(item.title, item.start, item.end, plannedDuration, actualDur, isCompleted, feedbackNote)
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Submitting...")
                } else {
                    Text("Submit Feedback")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}

// -------------------------------------------------------------
// HAMBURGER MENU DIALOGS (Point 8)
// -------------------------------------------------------------
@Composable
fun FeedbackInfoDialog(
    mlStatus: MLStatusResponseDto?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = { Text("Task Feedback & ML Learning", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "How it works:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "• After generating a schedule, click the 'Feedback' button on any flexible task or meal window to log your actual duration.\n• Once a task has 3+ logged entries, the AI model personalizes its duration estimates for you.\n• Tap 'Retrain Model' anytime to update predictions.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                mlStatus?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total feedback logged: ${it.userFeedbackCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Personalized tasks: ${it.personalizedTaskCount}", fontSize = 12.sp)
                            if (it.personalizedTasks.isNotEmpty()) {
                                Text("Tasks: " + it.personalizedTasks.joinToString(", "), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Got It")
            }
        }
    )
}

@Composable
fun SavedTasksOverviewDialog(
    tasks: List<TaskDto>,
    onDismiss: () -> Unit
) {
    val fixedCount = tasks.count { it.isFixed }
    val flexibleCount = tasks.count { !it.isFixed }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = { Text("Saved Tasks Overview (${tasks.size})", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Flexible Tasks: $flexibleCount", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Fixed Tasks: $fixedCount", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF991B1B))
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (tasks.isEmpty()) {
                    Text("No tasks added yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tasks.forEach { t ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = PrimaryPurple.copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(t.taskName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    if (t.isFixed && t.fixedStart != null && t.fixedEnd != null)
                                        "${TimeUtils.minutesToFormattedTime(t.fixedStart)} - ${TimeUtils.minutesToFormattedTime(t.fixedEnd)}"
                                    else TimeUtils.formatDuration(t.duration),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SettingsDialog(
    dayStartMinutes: Int,
    dayEndMinutes: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = { Text("App Settings & Info", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Configuration:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("• Active Day Window: ${TimeUtils.minutesToFormattedTime(dayStartMinutes)} - ${TimeUtils.minutesToFormattedTime(dayEndMinutes)}", fontSize = 12.sp)
                Text("• Backend Server: http://10.0.2.2:8000 (Local / Emulator)", fontSize = 12.sp)
                Text("• Theme: Material 3 Purple & Lavender", fontSize = 12.sp)
                Text("• Version: 1.0.0", fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Close")
            }
        }
    )
}
