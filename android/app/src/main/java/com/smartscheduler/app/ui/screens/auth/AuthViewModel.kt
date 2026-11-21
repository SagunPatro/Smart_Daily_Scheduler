package com.smartscheduler.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartscheduler.app.auth.TokenManager
import com.smartscheduler.app.data.model.LoginRequest
import com.smartscheduler.app.data.model.SignupRequest
import com.smartscheduler.app.data.repository.AuthRepository
import com.smartscheduler.app.util.Resource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val usernameInput: String = "",
    val emailInput: String = "",
    val passwordInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isAuthenticated: Boolean = false
)

sealed class AuthNavigationEvent {
    object NavigateToFoundation : AuthNavigationEvent()
    object NavigateToLogin : AuthNavigationEvent()
    object NavigateToSignup : AuthNavigationEvent()
    data class ShowToast(val message: String) : AuthNavigationEvent()
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<AuthNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<AuthNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        checkAuthenticationStatus()
        observeSessionExpiry()
    }

    fun checkAuthenticationStatus() {
        val isLoggedIn = !tokenManager.getToken().isNullOrBlank()
        _uiState.value = _uiState.value.copy(isAuthenticated = isLoggedIn)
    }

    fun onUsernameChanged(input: String) {
        _uiState.value = _uiState.value.copy(usernameInput = input, errorMessage = null)
    }

    fun onEmailChanged(input: String) {
        _uiState.value = _uiState.value.copy(emailInput = input, errorMessage = null)
    }

    fun onPasswordChanged(input: String) {
        _uiState.value = _uiState.value.copy(passwordInput = input, errorMessage = null)
    }

    fun login() {
        val username = _uiState.value.usernameInput.trim()
        val password = _uiState.value.passwordInput.trim()

        if (username.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Username cannot be empty")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val request = LoginRequest(username = username, password = password)
            when (val result = authRepository.login(request)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        passwordInput = ""
                    )
                    _navigationEvent.emit(AuthNavigationEvent.NavigateToFoundation)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message ?: "Authentication failed"
                    )
                }
                else -> {}
            }
        }
    }

    fun signup() {
        val username = _uiState.value.usernameInput.trim()
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput.trim()

        if (username.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Username cannot be empty")
            return
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a valid email address")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, successMessage = null)
            val request = SignupRequest(username = username, email = email, password = password)
            when (val result = authRepository.signup(request)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Account created successfully! Please log in.",
                        passwordInput = ""
                    )
                    _navigationEvent.emit(AuthNavigationEvent.NavigateToLogin)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message ?: "Registration failed"
                    )
                }
                else -> {}
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = AuthUiState(isAuthenticated = false)
        viewModelScope.launch {
            _navigationEvent.emit(AuthNavigationEvent.NavigateToLogin)
        }
    }

    private fun observeSessionExpiry() {
        viewModelScope.launch {
            tokenManager.sessionExpiredEvent.collect {
                _uiState.value = AuthUiState(
                    isAuthenticated = false,
                    errorMessage = "Session expired. Please log in again."
                )
                _navigationEvent.emit(AuthNavigationEvent.ShowToast("Session expired"))
                _navigationEvent.emit(AuthNavigationEvent.NavigateToLogin)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
