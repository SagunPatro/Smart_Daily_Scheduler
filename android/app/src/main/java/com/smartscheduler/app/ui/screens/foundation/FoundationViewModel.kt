package com.smartscheduler.app.ui.screens.foundation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartscheduler.app.auth.TokenManager
import com.smartscheduler.app.data.api.ApiService
import com.smartscheduler.app.util.Resource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FoundationUiState(
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val statusMessage: String = "Checking connection to http://10.0.2.2:8000/health...",
    val serverStatus: String = "Unknown",
    val tokenStored: Boolean = false
)

sealed class NavigationEvent {
    object NavigateToAuth : NavigationEvent()
}

class FoundationViewModel(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoundationUiState())
    val uiState: StateFlow<FoundationUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        checkTokenState()
        checkHealth()
        observeSessionExpiry()
    }

    fun checkTokenState() {
        val hasToken = !tokenManager.getToken().isNullOrBlank()
        _uiState.value = _uiState.value.copy(tokenStored = hasToken)
    }

    fun checkHealth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Connecting to 10.0.2.2:8000...")
            try {
                val response = apiService.getHealth()
                if (response.isSuccessful && response.body() != null) {
                    val status = response.body()!!.status
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isConnected = true,
                        serverStatus = status,
                        statusMessage = "Connected successfully to http://10.0.2.2:8000/health (status: $status)"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isConnected = false,
                        statusMessage = "Server returned error: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isConnected = false,
                    statusMessage = "Connection failed: ${e.message}"
                )
            }
        }
    }

    private fun observeSessionExpiry() {
        viewModelScope.launch {
            tokenManager.sessionExpiredEvent.collect {
                _uiState.value = _uiState.value.copy(tokenStored = false)
                _navigationEvent.emit(NavigationEvent.NavigateToAuth)
            }
        }
    }
}
