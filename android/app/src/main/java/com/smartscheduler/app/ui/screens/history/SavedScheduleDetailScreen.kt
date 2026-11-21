package com.smartscheduler.app.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartscheduler.app.data.model.ScheduleItemDto
import com.smartscheduler.app.ui.navigation.Screen
import com.smartscheduler.app.ui.screens.scheduler.NotebookPerforatedLine
import com.smartscheduler.app.ui.screens.scheduler.SpiralBindingHeader
import com.smartscheduler.app.ui.theme.*
import com.smartscheduler.app.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScheduleDetailScreen(
    scheduleId: Int,
    viewModel: ScheduleHistoryViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(scheduleId) {
        viewModel.loadScheduleDetails(scheduleId)
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is HistoryNavigationEvent.NavigateToLogin -> {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.ScheduleHistory.route) { inclusive = true }
                    }
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Schedule #$scheduleId", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Screen.ScheduleHistory.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to History", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
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

            if (uiState.isLoadingDetails) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                uiState.selectedSchedule?.let { schedule ->
                    // Header Overview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = schedule.title ?: "Daily Schedule #${schedule.id}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Day Window: ${TimeUtils.minutesToFormattedTime(schedule.dayStart)} - ${TimeUtils.minutesToFormattedTime(schedule.dayEnd)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Created At: ${TimeUtils.formatDateTime(schedule.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Historical Timeline Display Card (Spiral Notebook Note Style)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 14.dp, topEnd = 14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF7FD)),
                        border = BorderStroke(1.5.dp, Color(0xFFD8B4FE)),
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
                                    spiralColor = Color(0xFFC084FC),
                                    spiralHighlight = Color(0xFFE9D5FF),
                                    holeColor = Color(0xFF382952)
                                )
                            }

                            // 2. Perforated tear line beneath spirals
                            NotebookPerforatedLine()

                            // 3. Note content with bold text
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
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
                                            text = "Historical Schedule Note",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = PrimaryPurpleDark,
                                            fontSize = 18.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFEDE9FE),
                                        border = BorderStroke(1.dp, Color(0xFFC4B5FD))
                                    ) {
                                        Text(
                                            text = "SAVED NOTE",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = PrimaryPurple,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                val nonFreeCount = schedule.scheduleData.schedule.count { it.category.lowercase() != "free" }
                                Text(
                                    text = "Archived Schedule • $nonFreeCount Activities (${schedule.scheduleData.schedule.size} Total Blocks)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CharcoalMedium
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                if (schedule.scheduleData.warnings.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = WarningBg),
                                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("⚠ Note Warnings:", fontWeight = FontWeight.ExtraBold, color = WarningText, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            schedule.scheduleData.warnings.forEach { warning ->
                                                Text("• $warning", color = WarningText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    schedule.scheduleData.schedule.forEach { item ->
                                        HistoricalScheduleItemRow(item = item)
                                    }
                                }
                            }
                        }
                    }
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Schedule details unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoricalScheduleItemRow(item: ScheduleItemDto) {
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
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(accentBarColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeRangeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )
            }

            if (duration > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = textColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = TimeUtils.formatDuration(duration),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
