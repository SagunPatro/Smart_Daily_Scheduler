package com.smartscheduler.app.ui.screens.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartscheduler.app.auth.TokenManager
import com.smartscheduler.app.data.model.*
import com.smartscheduler.app.data.repository.ScheduleRepository
import com.smartscheduler.app.data.repository.TaskRepository
import com.smartscheduler.app.data.session.ScheduleSessionManager
import com.smartscheduler.app.util.Resource
import com.smartscheduler.app.util.TimeUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskSchedulerUiState(
    val dayStartMinutes: Int = 540, // 09:00
    val dayEndMinutes: Int = 1380, // 23:00
    val tasks: List<TaskDto> = emptyList(),
    val isLoadingTasks: Boolean = false,
    val isGeneratingSchedule: Boolean = false,
    val isSavingSchedule: Boolean = false,
    val scheduleResponse: ScheduleResponseDto? = null,
    val generatedSchedulePreview: ScheduleResponseDto? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Task Dialog Form state
    val isTaskDialogOpen: Boolean = false,
    val editingTaskId: Int? = null,
    val formTaskName: String = "",
    val formDurationMinutes: String = "60",
    val formIsFixed: Boolean = false,
    val formFixedStartMinutes: Int = 600, // 10:00 (minutes from midnight)
    val formFixedEndMinutes: Int = 660,   // 11:00 (auto-calculated: start + duration)
    val formError: String? = null,
    // ML Retraining state
    val isRetraining: Boolean = false,
    val retrainMessage: String? = null,
    val retrainError: String? = null,
    // ML Analytics & Feedback state
    val mlStatus: MLStatusResponseDto? = null,
    val isSubmittingFeedback: Boolean = false,
    val feedbackSuccessMessage: String? = null,
    val feedbackError: String? = null,
    val selectedScheduleItemForFeedback: ScheduleItemDto? = null,
    // Top-bar Hamburger menu dialogs
    val isFeedbackInfoDialogOpen: Boolean = false,
    val isSavedTasksDialogOpen: Boolean = false,
    val isSettingsDialogOpen: Boolean = false,
    val scheduleResetNotice: String? = null
)

sealed class SchedulerNavigationEvent {
    object NavigateToLogin : SchedulerNavigationEvent()
    data class ShowToast(val message: String) : SchedulerNavigationEvent()
}

class TaskSchedulerViewModel(
    private val taskRepository: TaskRepository,
    private val scheduleRepository: ScheduleRepository,
    private val tokenManager: TokenManager,
    private val sessionManager: ScheduleSessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskSchedulerUiState())
    val uiState: StateFlow<TaskSchedulerUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<SchedulerNavigationEvent>(replay = 0, extraBufferCapacity = 64)
    val navigationEvent: SharedFlow<SchedulerNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        restoreOrResetSessionSchedule()
        loadTasks()
        loadMLStatus()
        observeSessionExpiry()
    }

    private fun restoreOrResetSessionSchedule() {
        val sm = sessionManager ?: return
        if (sm.isScheduleExpired()) {
            sm.clearActiveSchedule()
            _uiState.value = _uiState.value.copy(
                scheduleResponse = null,
                generatedSchedulePreview = null,
                scheduleResetNotice = "Previous day schedule has ended. Ready to input new timings & durations for today!"
            )
        } else {
            val active = sm.getActiveSchedule()
            if (active != null) {
                _uiState.value = _uiState.value.copy(
                    scheduleResponse = active,
                    dayStartMinutes = sm.getSavedDayStart(),
                    dayEndMinutes = sm.getSavedDayEnd()
                )
            }
        }
    }

    fun checkAndResetScheduleIfExpired() {
        val sm = sessionManager ?: return
        if (sm.isScheduleExpired()) {
            sm.clearActiveSchedule()
            if (_uiState.value.scheduleResponse != null || _uiState.value.generatedSchedulePreview != null) {
                _uiState.value = _uiState.value.copy(
                    scheduleResponse = null,
                    generatedSchedulePreview = null,
                    scheduleResetNotice = "Previous day schedule has ended. Ready to input new timings & durations for today!"
                )
            }
        }
    }

    fun resetActiveSchedule() {
        sessionManager?.clearActiveSchedule()
        _uiState.value = _uiState.value.copy(
            scheduleResponse = null,
            generatedSchedulePreview = null,
            scheduleResetNotice = "Schedule reset for new day planning. You can now adjust timings and durations."
        )
    }

    fun dismissScheduleResetNotice() {
        _uiState.value = _uiState.value.copy(scheduleResetNotice = null)
    }

    private fun handleAuthError(resultMessage: String?, resultCode: Int?) {
        val msg = resultMessage ?: ""
        if (resultCode == 401 ||
            msg.contains("credential", ignoreCase = true) ||
            msg.contains("session expired", ignoreCase = true) ||
            msg.contains("token", ignoreCase = true)
        ) {
            tokenManager.clearToken()
            viewModelScope.launch {
                _navigationEvent.emit(SchedulerNavigationEvent.NavigateToLogin)
            }
        }
    }

    fun loadTasks() {
        if (tokenManager.getToken().isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isLoadingTasks = false)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTasks = true, errorMessage = null)
            when (val result = taskRepository.getTasks()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingTasks = false,
                        tasks = result.data
                    )
                }
                is Resource.Error -> {
                    if (result.code == 401 ||
                        result.message.contains("credential", ignoreCase = true) ||
                        result.message.contains("session expired", ignoreCase = true)
                    ) {
                        handleAuthError(result.message, result.code)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingTasks = false,
                            errorMessage = result.message
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun updateDaySettings(dayStart: Int, dayEnd: Int) {
        if (dayStart < 0 || dayStart >= 1440 || dayEnd < 0 || dayEnd >= 1440) {
            _uiState.value = _uiState.value.copy(errorMessage = "Day times must be valid times between 00:00 and 23:59")
            return
        }
        if (dayStart >= dayEnd) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Day start time (${TimeUtils.minutesToFormattedTime(dayStart)}) must be before day end time (${TimeUtils.minutesToFormattedTime(dayEnd)})"
            )
            return
        }
        // Invalidate stale schedule when day window changes
        sessionManager?.clearActiveSchedule()
        _uiState.value = _uiState.value.copy(
            dayStartMinutes = dayStart,
            dayEndMinutes = dayEnd,
            scheduleResponse = null,
            generatedSchedulePreview = null,
            errorMessage = null
        )
    }

    // Task Dialog Form Handlers
    fun openAddTaskDialog() {
        val defaultStart = (_uiState.value.dayStartMinutes + 60).coerceIn(0, 1439)
        val defaultDuration = 60
        val defaultEnd = TimeUtils.calculateEndTime(defaultStart, defaultDuration)

        _uiState.value = _uiState.value.copy(
            isTaskDialogOpen = true,
            editingTaskId = null,
            formTaskName = "",
            formDurationMinutes = defaultDuration.toString(),
            formIsFixed = false,
            formFixedStartMinutes = defaultStart,
            formFixedEndMinutes = defaultEnd,
            formError = null
        )
    }

    fun openEditTaskDialog(task: TaskDto) {
        val startMin = task.fixedStart ?: (_uiState.value.dayStartMinutes + 60).coerceIn(0, 1439)
        val endMin = task.fixedEnd ?: TimeUtils.calculateEndTime(startMin, task.duration)

        _uiState.value = _uiState.value.copy(
            isTaskDialogOpen = true,
            editingTaskId = task.id,
            formTaskName = task.taskName,
            formDurationMinutes = task.duration.toString(),
            formIsFixed = task.isFixed,
            formFixedStartMinutes = startMin,
            formFixedEndMinutes = endMin,
            formError = null
        )
    }

    fun closeTaskDialog() {
        _uiState.value = _uiState.value.copy(isTaskDialogOpen = false, formError = null)
    }

    fun onFormTaskNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(formTaskName = name, formError = null)
    }

    fun onFormDurationChanged(durationStr: String) {
        val duration = durationStr.toIntOrNull() ?: 0
        val newEnd = TimeUtils.calculateEndTime(_uiState.value.formFixedStartMinutes, duration)
        _uiState.value = _uiState.value.copy(
            formDurationMinutes = durationStr,
            formFixedEndMinutes = newEnd,
            formError = null
        )
    }

    fun onFormIsFixedChanged(isFixed: Boolean) {
        val duration = _uiState.value.formDurationMinutes.toIntOrNull() ?: 60
        val newEnd = TimeUtils.calculateEndTime(_uiState.value.formFixedStartMinutes, duration)
        _uiState.value = _uiState.value.copy(
            formIsFixed = isFixed,
            formFixedEndMinutes = newEnd,
            formError = null
        )
    }

    fun onFormFixedStartSelected(startMinutes: Int) {
        val duration = _uiState.value.formDurationMinutes.toIntOrNull() ?: 60
        val newEnd = TimeUtils.calculateEndTime(startMinutes, duration)
        _uiState.value = _uiState.value.copy(
            formFixedStartMinutes = startMinutes,
            formFixedEndMinutes = newEnd,
            formError = null
        )
    }

    fun saveTask() {
        val name = _uiState.value.formTaskName.trim()
        val duration = _uiState.value.formDurationMinutes.toIntOrNull()
        val isFixed = _uiState.value.formIsFixed
        val dayStart = _uiState.value.dayStartMinutes
        val dayEnd = _uiState.value.dayEndMinutes
        val editingId = _uiState.value.editingTaskId

        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(formError = "Task name cannot be empty")
            return
        }
        if (duration == null || duration <= 0) {
            _uiState.value = _uiState.value.copy(formError = "Task duration must be a positive number of minutes")
            return
        }

        var fixedStartMinutes: Int? = null
        var fixedEndMinutes: Int? = null

        if (isFixed) {
            fixedStartMinutes = _uiState.value.formFixedStartMinutes
            fixedEndMinutes = TimeUtils.calculateEndTime(fixedStartMinutes, duration)

            // Validate midnight boundary
            if (fixedEndMinutes > 1440) {
                _uiState.value = _uiState.value.copy(
                    formError = "Fixed task end time exceeds midnight (24:00). Please select an earlier start time or shorter duration."
                )
                return
            }

            // Validate day window
            if (fixedStartMinutes < dayStart || fixedEndMinutes > dayEnd) {
                _uiState.value = _uiState.value.copy(
                    formError = "Fixed task window (${TimeUtils.minutesToFormattedTime(fixedStartMinutes)} - ${TimeUtils.minutesToFormattedTime(fixedEndMinutes)}) is outside day window (${TimeUtils.minutesToFormattedTime(dayStart)} - ${TimeUtils.minutesToFormattedTime(dayEnd)})."
                )
                return
            }

            // Validate overlaps with other fixed tasks
            val otherFixedTasks = _uiState.value.tasks.filter { t ->
                t.isFixed && t.id != editingId && t.fixedStart != null && t.fixedEnd != null
            }
            val conflicting = otherFixedTasks.find { t ->
                TimeUtils.hasOverlap(fixedStartMinutes, fixedEndMinutes, t.fixedStart!!, t.fixedEnd!!)
            }
            if (conflicting != null) {
                _uiState.value = _uiState.value.copy(
                    formError = "Fixed task overlaps with '${conflicting.taskName}' (${TimeUtils.minutesToFormattedTime(conflicting.fixedStart!!)} - ${TimeUtils.minutesToFormattedTime(conflicting.fixedEnd!!)})."
                )
                return
            }
        }

        viewModelScope.launch {
            if (editingId == null) {
                val createDto = TaskCreateDto(
                    taskName = name,
                    duration = duration,
                    isFixed = isFixed,
                    fixedStart = fixedStartMinutes,
                    fixedEnd = fixedEndMinutes
                )
                when (val result = taskRepository.createTask(createDto)) {
                    is Resource.Success -> {
                        closeTaskDialog()
                        _uiState.value = _uiState.value.copy(scheduleResponse = null, generatedSchedulePreview = null)
                        loadTasks()
                    }
                    is Resource.Error -> {
                        handleAuthError(result.message, result.code)
                        _uiState.value = _uiState.value.copy(formError = result.message)
                    }
                    else -> {}
                }
            } else {
                val updateDto = TaskUpdateDto(
                    taskName = name,
                    duration = duration,
                    isFixed = isFixed,
                    fixedStart = fixedStartMinutes,
                    fixedEnd = fixedEndMinutes
                )
                when (val result = taskRepository.updateTask(editingId, updateDto)) {
                    is Resource.Success -> {
                        closeTaskDialog()
                        _uiState.value = _uiState.value.copy(scheduleResponse = null, generatedSchedulePreview = null)
                        loadTasks()
                    }
                    is Resource.Error -> {
                        handleAuthError(result.message, result.code)
                        _uiState.value = _uiState.value.copy(formError = result.message)
                    }
                    else -> {}
                }
            }
        }
    }

    fun deleteTask(taskId: Int) {
        viewModelScope.launch {
            when (val result = taskRepository.deleteTask(taskId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(scheduleResponse = null, generatedSchedulePreview = null)
                    loadTasks()
                }
                is Resource.Error -> {
                    handleAuthError(result.message, result.code)
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
                else -> {}
            }
        }
    }

    // Schedule Generation: Step 1 (Preview with save_to_history = false)
    fun generateSchedule() {
        val currentTasks = _uiState.value.tasks
        val dayStart = _uiState.value.dayStartMinutes
        val dayEnd = _uiState.value.dayEndMinutes

        if (currentTasks.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "No tasks added yet. Please add at least one task before generating a schedule.")
            return
        }

        if (dayStart >= dayEnd) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Day start time (${TimeUtils.minutesToFormattedTime(dayStart)}) must be before day end time (${TimeUtils.minutesToFormattedTime(dayEnd)})"
            )
            return
        }

        val invalidFixedTask = currentTasks.find { t ->
            t.isFixed && (t.fixedStart == null || t.fixedEnd == null || t.fixedStart < dayStart || t.fixedEnd > dayEnd)
        }
        if (invalidFixedTask != null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Fixed task '${invalidFixedTask.taskName}' is outside the selected day window (${TimeUtils.minutesToFormattedTime(dayStart)} - ${TimeUtils.minutesToFormattedTime(dayEnd)}). Please edit the task time range."
            )
            return
        }

        val taskDtos = currentTasks.map { task ->
            TaskCreateDto(
                taskName = task.taskName,
                duration = task.duration,
                isFixed = task.isFixed,
                fixedStart = task.fixedStart,
                fixedEnd = task.fixedEnd
            )
        }

        val request = ScheduleRequestDto(
            dayStart = dayStart,
            dayEnd = dayEnd,
            tasks = taskDtos,
            title = "Daily Focus Plan",
            saveToHistory = false
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingSchedule = true, errorMessage = null)
            when (val result = scheduleRepository.generateSchedule(request)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingSchedule = false,
                        generatedSchedulePreview = result.data
                    )
                }
                is Resource.Error -> {
                    handleAuthError(result.message, result.code)
                    _uiState.value = _uiState.value.copy(
                        isGeneratingSchedule = false,
                        errorMessage = result.message
                    )
                }
                else -> {}
            }
        }
    }

    // Schedule Generation: Step 2 (Explicit Save Confirmation with save_to_history = true)
    fun confirmAndSaveSchedule() {
        val currentTasks = _uiState.value.tasks
        val dayStart = _uiState.value.dayStartMinutes
        val dayEnd = _uiState.value.dayEndMinutes

        val taskDtos = currentTasks.map { task ->
            TaskCreateDto(
                taskName = task.taskName,
                duration = task.duration,
                isFixed = task.isFixed,
                fixedStart = task.fixedStart,
                fixedEnd = task.fixedEnd
            )
        }

        val request = ScheduleRequestDto(
            dayStart = dayStart,
            dayEnd = dayEnd,
            tasks = taskDtos,
            title = "Daily Focus Plan",
            saveToHistory = true
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingSchedule = true, errorMessage = null)
            when (val result = scheduleRepository.generateSchedule(request)) {
                is Resource.Success -> {
                    sessionManager?.saveActiveSchedule(result.data, dayStart, dayEnd)
                    _uiState.value = _uiState.value.copy(
                        isSavingSchedule = false,
                        scheduleResponse = result.data,
                        generatedSchedulePreview = null,
                        scheduleResetNotice = null,
                        successMessage = "Schedule saved to history successfully!"
                    )
                }
                is Resource.Error -> {
                    handleAuthError(result.message, result.code)
                    _uiState.value = _uiState.value.copy(
                        isSavingSchedule = false,
                        errorMessage = result.message
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isSavingSchedule = false)
                }
            }
        }
    }

    // Schedule Generation: Discard action
    fun discardGeneratedSchedule() {
        _uiState.value = _uiState.value.copy(
            generatedSchedulePreview = null,
            successMessage = "Generated schedule discarded."
        )
    }

    fun logout() {
        tokenManager.clearToken()
        viewModelScope.launch {
            _navigationEvent.emit(SchedulerNavigationEvent.NavigateToLogin)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    fun retrainModel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRetraining = true,
                retrainMessage = null,
                retrainError = null
            )
            when (val result = scheduleRepository.retrainModel()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isRetraining = false,
                        retrainMessage = "Model updated successfully with ${result.data.sampleCount} learning samples. Future predictions will use this updated model.",
                        retrainError = null
                    )
                    loadMLStatus()
                }
                is Resource.Error -> {
                    handleAuthError(result.message, result.code)
                    val actionableErr = if (result.message.contains("not enough", ignoreCase = true) || result.message.contains("sample", ignoreCase = true)) {
                        "Cannot retrain model yet: Complete tasks and submit feedback to provide learning samples first."
                    } else {
                        result.message
                    }
                    _uiState.value = _uiState.value.copy(
                        isRetraining = false,
                        retrainMessage = null,
                        retrainError = actionableErr
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isRetraining = false)
                }
            }
        }
    }

    fun dismissRetrainStatus() {
        _uiState.value = _uiState.value.copy(
            retrainMessage = null,
            retrainError = null
        )
    }

    fun loadMLStatus() {
        if (tokenManager.getToken().isNullOrBlank()) return
        viewModelScope.launch {
            when (val result = scheduleRepository.getMLStatus()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(mlStatus = result.data)
                }
                is Resource.Error -> {
                    handleAuthError(result.message, result.code)
                }
                else -> {}
            }
        }
    }

    fun openFeedbackDialog(item: ScheduleItemDto) {
        _uiState.value = _uiState.value.copy(
            selectedScheduleItemForFeedback = item,
            feedbackError = null
        )
    }

    fun closeFeedbackDialog() {
        _uiState.value = _uiState.value.copy(
            selectedScheduleItemForFeedback = null,
            feedbackError = null
        )
    }

    fun submitTaskFeedback(
        taskName: String,
        plannedStart: Int,
        plannedEnd: Int,
        plannedDuration: Int,
        actualDuration: Int,
        completed: Boolean,
        feedbackNote: String? = null
    ) {
        if (taskName.isBlank()) {
            _uiState.value = _uiState.value.copy(feedbackError = "Task name cannot be empty")
            return
        }
        if (actualDuration < 1) {
            _uiState.value = _uiState.value.copy(feedbackError = "Actual duration must be at least 1 minute")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmittingFeedback = true,
                feedbackError = null,
                feedbackSuccessMessage = null
            )
            val request = FeedbackRequestDto(
                taskName = taskName,
                plannedStart = plannedStart,
                plannedEnd = plannedEnd,
                plannedDuration = plannedDuration,
                actualDuration = actualDuration,
                completed = if (completed) "yes" else "no",
                feedback = feedbackNote ?: ""
            )
            when (val result = scheduleRepository.submitFeedback(request)) {
                is Resource.Success -> {
                    val resp = result.data
                    val successMsg = if (resp?.preferenceRecognized == true && !resp.preference.isNullOrBlank()) {
                        resp.message.ifBlank { "Preference saved: ${resp.preference}." }
                    } else {
                        "Feedback recorded for '$taskName'. Your future schedules will learn from this."
                    }
                    _uiState.value = _uiState.value.copy(
                        isSubmittingFeedback = false,
                        selectedScheduleItemForFeedback = null,
                        feedbackSuccessMessage = successMsg,
                        feedbackError = null
                    )
                    loadMLStatus()
                }
                is Resource.Error -> {
                    handleAuthError(result.message, result.code)
                    _uiState.value = _uiState.value.copy(
                        isSubmittingFeedback = false,
                        feedbackError = result.message
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isSubmittingFeedback = false)
                }
            }
        }
    }

    fun dismissFeedbackStatus() {
        _uiState.value = _uiState.value.copy(
            feedbackSuccessMessage = null,
            feedbackError = null
        )
    }

    // Top-bar Hamburger menu dialog actions
    fun openFeedbackInfoDialog() {
        _uiState.value = _uiState.value.copy(isFeedbackInfoDialogOpen = true)
    }

    fun closeFeedbackInfoDialog() {
        _uiState.value = _uiState.value.copy(isFeedbackInfoDialogOpen = false)
    }

    fun openSavedTasksDialog() {
        _uiState.value = _uiState.value.copy(isSavedTasksDialogOpen = true)
    }

    fun closeSavedTasksDialog() {
        _uiState.value = _uiState.value.copy(isSavedTasksDialogOpen = false)
    }

    fun openSettingsDialog() {
        _uiState.value = _uiState.value.copy(isSettingsDialogOpen = true)
    }

    fun closeSettingsDialog() {
        _uiState.value = _uiState.value.copy(isSettingsDialogOpen = false)
    }

    private fun observeSessionExpiry() {
        viewModelScope.launch {
            tokenManager.sessionExpiredEvent.collect {
                _navigationEvent.emit(SchedulerNavigationEvent.ShowToast("Session expired. Please log in again."))
                _navigationEvent.emit(SchedulerNavigationEvent.NavigateToLogin)
            }
        }
    }
}
