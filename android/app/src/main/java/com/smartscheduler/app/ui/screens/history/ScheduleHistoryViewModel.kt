package com.smartscheduler.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartscheduler.app.auth.TokenManager
import com.smartscheduler.app.data.model.SavedScheduleResponseDto
import com.smartscheduler.app.data.repository.ScheduleRepository
import com.smartscheduler.app.util.Resource
import com.smartscheduler.app.util.TimeUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduleHistoryUiState(
    val schedules: List<SavedScheduleResponseDto> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val selectedSchedule: SavedScheduleResponseDto? = null,
    val isLoadingDetails: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val deletingScheduleId: Int? = null
)

sealed class HistoryNavigationEvent {
    object NavigateToLogin : HistoryNavigationEvent()
    data class ShowToast(val message: String) : HistoryNavigationEvent()
}

class ScheduleHistoryViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleHistoryUiState())
    val uiState: StateFlow<ScheduleHistoryUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<HistoryNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<HistoryNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        loadHistory()
        observeSessionExpiry()
    }

    fun loadHistory() {
        if (tokenManager.getToken().isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isLoadingHistory = false)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistory = true, errorMessage = null)
            when (val result = scheduleRepository.getSchedules()) {
                is Resource.Success -> {
                    // Retain only current month schedules (monthly lifecycle)
                    val currentMonthSchedules = result.data.filter { TimeUtils.isCurrentMonth(it.createdAt) }
                    _uiState.value = _uiState.value.copy(
                        isLoadingHistory = false,
                        schedules = currentMonthSchedules
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingHistory = false,
                        errorMessage = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun loadScheduleDetails(scheduleId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDetails = true, errorMessage = null)
            when (val result = scheduleRepository.getScheduleById(scheduleId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingDetails = false,
                        selectedSchedule = result.data
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingDetails = false,
                        errorMessage = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun deleteSchedule(scheduleId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingScheduleId = scheduleId, errorMessage = null)
            when (val result = scheduleRepository.deleteSchedule(scheduleId)) {
                is Resource.Success -> {
                    val updatedList = _uiState.value.schedules.filterNot { it.id == scheduleId }
                    _uiState.value = _uiState.value.copy(
                        deletingScheduleId = null,
                        schedules = updatedList,
                        successMessage = "Schedule deleted successfully"
                    )
                    if (_uiState.value.selectedSchedule?.id == scheduleId) {
                        _uiState.value = _uiState.value.copy(selectedSchedule = null)
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        deletingScheduleId = null,
                        errorMessage = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistory = true, errorMessage = null)
            when (val result = scheduleRepository.clearAllSchedules()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingHistory = false,
                        schedules = emptyList(),
                        selectedSchedule = null,
                        successMessage = "Schedule history cleared successfully"
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingHistory = false,
                        errorMessage = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun observeSessionExpiry() {
        viewModelScope.launch {
            tokenManager.sessionExpiredEvent.collect {
                _navigationEvent.emit(HistoryNavigationEvent.ShowToast("Session expired"))
                _navigationEvent.emit(HistoryNavigationEvent.NavigateToLogin)
            }
        }
    }
}
