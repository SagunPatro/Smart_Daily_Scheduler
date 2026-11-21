package com.smartscheduler.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages JWT access tokens securely using Android Keystore-backed EncryptedSharedPreferences.
 * Emits session-expired events via SharedFlow without directly manipulating Compose UI/Navigation.
 */
class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 64)
    val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    fun saveToken(token: String) {
        encryptedPrefs.edit().putString(KEY_ACCESS_TOKEN, token).commit()
    }

    fun getToken(): String? {
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun clearToken() {
        encryptedPrefs.edit().remove(KEY_ACCESS_TOKEN).commit()
    }

    fun onSessionExpired() {
        clearToken()
        _sessionExpiredEvent.tryEmit(Unit)
    }

    companion object {
        private const val PREFS_FILENAME = "smart_scheduler_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "jwt_access_token"
    }
}
