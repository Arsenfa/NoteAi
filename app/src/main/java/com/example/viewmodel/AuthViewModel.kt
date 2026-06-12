package com.example.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Loading : AuthUiState()
    object LoggedOut : AuthUiState()
    object LoggedIn : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(private val authRepository: AuthRepository, private val context: Context) : ViewModel() {
    
    val currentUser: com.google.firebase.auth.FirebaseUser?
        get() = authRepository.currentUser
    
    companion object {
        val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        val MOCK_USER_EMAIL_KEY = stringPreferencesKey("mock_user_email")
    }

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val authState: StateFlow<AuthUiState> = _authState

    val mockUserEmail = MutableStateFlow<String?>(null)

    init {
        // Observe local datastore preferences for mock log in status first
        viewModelScope.launch {
            context.dataStore.data.collect { preferences ->
                val isMockLoggedIn = preferences[IS_LOGGED_IN_KEY] ?: false
                mockUserEmail.value = preferences[MOCK_USER_EMAIL_KEY]
                if (isMockLoggedIn) {
                    _authState.value = AuthUiState.LoggedIn
                } else if (authRepository.isLoggedIn.value) {
                    _authState.value = AuthUiState.LoggedIn
                } else {
                    _authState.value = AuthUiState.LoggedOut
                }
            }
        }

        // Observe login status from Firebase Auth
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    _authState.value = AuthUiState.LoggedIn
                    setLoggedInPref(true)
                } else {
                    context.dataStore.data.first().let { preferences ->
                        val isMockLoggedIn = preferences[IS_LOGGED_IN_KEY] ?: false
                        if (!isMockLoggedIn) {
                            _authState.value = AuthUiState.LoggedOut
                            setLoggedInPref(false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun setLoggedInPref(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN_KEY] = value
        }
    }

    private fun setMockUserSession(email: String?) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[IS_LOGGED_IN_KEY] = true
                if (email != null) {
                    preferences[MOCK_USER_EMAIL_KEY] = email
                } else {
                    preferences.remove(MOCK_USER_EMAIL_KEY)
                }
            }
            mockUserEmail.value = email
            _authState.value = AuthUiState.LoggedIn
        }
    }

    fun signInAnonymously(onSuccess: () -> Unit, onError: (String) -> Unit) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.signInAnonymously()
            result.fold(
                onSuccess = {
                    _authState.value = AuthUiState.LoggedIn
                    onSuccess()
                },
                onFailure = {
                    setMockUserSession("guest_user@example.com")
                    onSuccess()
                }
            )
        }
    }

    fun signInWithEmail(email: String, emailPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.signInWithEmail(email, emailPass)
            result.fold(
                onSuccess = {
                    _authState.value = AuthUiState.LoggedIn
                    onSuccess()
                },
                onFailure = { error ->
                    val message = error.message ?: "Terjadi kesalahan. Coba lagi nanti"
                    _authState.value = AuthUiState.Error(message)
                    onError(message)
                }
            )
        }
    }

    fun signUpWithEmail(email: String, emailPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.signUpWithEmail(email, emailPass)
            result.fold(
                onSuccess = {
                    _authState.value = AuthUiState.LoggedIn
                    onSuccess()
                },
                onFailure = { error ->
                    val message = error.message ?: "Gagal membuat akun. Coba lagi nanti"
                    _authState.value = AuthUiState.Error(message)
                    onError(message)
                }
            )
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken)
            result.fold(
                onSuccess = {
                    _authState.value = AuthUiState.LoggedIn
                    onSuccess()
                },
                onFailure = { error ->
                    val message = error.message ?: "Gagal login dengan Google. Coba lagi nanti"
                    _authState.value = AuthUiState.Error(message)
                    onError(message)
                }
            )
        }
    }

    fun linkAnonymousToEmail(email: String, emailPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.linkAnonymousToEmail(email, emailPass)
            result.fold(
                onSuccess = {
                    _authState.value = AuthUiState.LoggedIn
                    onSuccess()
                },
                onFailure = { error ->
                    val message = error.message ?: "Gagal menghubungkan akun. Coba lagi nanti"
                    _authState.value = AuthUiState.Error(message)
                    onError(message)
                }
            )
        }
    }

    fun linkAnonymousToGoogle(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.linkAnonymousToGoogle(idToken)
            result.fold(
                onSuccess = {
                    _authState.value = AuthUiState.LoggedIn
                    onSuccess()
                },
                onFailure = { error ->
                    val message = error.message ?: "Gagal menghubungkan akun Google. Coba lagi nanti"
                    _authState.value = AuthUiState.Error(message)
                    onError(message)
                }
            )
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        authRepository.signOut()
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[IS_LOGGED_IN_KEY] = false
                preferences.remove(MOCK_USER_EMAIL_KEY)
            }
            mockUserEmail.value = null
            _authState.value = AuthUiState.LoggedOut
            onSuccess()
        }
    }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN_KEY] ?: false
    }
}
