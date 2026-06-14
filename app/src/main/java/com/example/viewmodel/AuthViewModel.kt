package com.example.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    }

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.LoggedOut)
    val authState: StateFlow<AuthUiState> = _authState

    init {
        viewModelScope.launch {
            combine(
                context.dataStore.data.map { it[IS_LOGGED_IN_KEY] ?: false },
                authRepository.isLoggedIn
            ) { prefLoggedIn, firebaseLoggedIn -> prefLoggedIn || firebaseLoggedIn }
                .distinctUntilChanged()
                .collect { loggedIn ->
                    _authState.value = if (loggedIn) AuthUiState.LoggedIn else AuthUiState.LoggedOut
                    setLoggedInPref(loggedIn)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        authRepository.clear()
    }

    private suspend fun setLoggedInPref(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN_KEY] = value
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
                onFailure = { err ->
                    val message = err.message ?: "Gagal masuk sebagai tamu. Coba lagi."
                    _authState.value = AuthUiState.Error(message)
                    onError(message)
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
            }
            _authState.value = AuthUiState.LoggedOut
            onSuccess()
        }
    }

    fun sendPasswordResetEmail(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = {
                    _authState.value = AuthUiState.LoggedOut
                    onSuccess()
                },
                onFailure = { error ->
                    val message = error.message ?: "Gagal mengirim email reset"
                    _authState.value = AuthUiState.Error(message)
                    onError(message)
                }
            )
        }
    }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN_KEY] ?: false
    }
}
