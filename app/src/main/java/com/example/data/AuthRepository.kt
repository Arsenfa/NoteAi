package com.example.data

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _isLoggedIn.value = firebaseAuth.currentUser != null
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    fun clear() {
        auth.removeAuthStateListener(authStateListener)
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signInWithGoogle(idToken: String): Result<AuthResult> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            _isLoggedIn.value = true
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<AuthResult> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            _isLoggedIn.value = true
            Result.success(result)
        } catch (e: FirebaseAuthInvalidUserException) {
            // Covers ERROR_USER_NOT_FOUND and ERROR_USER_DISABLED
            val message = when (e.errorCode) {
                "ERROR_USER_DISABLED" -> "Akun ini telah dinonaktifkan"
                else -> "Email atau password salah"
            }
            Result.failure(Exception(message))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            // Covers ERROR_WRONG_PASSWORD and ERROR_INVALID_CREDENTIAL
            Result.failure(Exception("Email atau password salah"))
        } catch (e: FirebaseAuthException) {
            val message = when (e.errorCode) {
                "ERROR_TOO_MANY_REQUESTS" -> "Terlalu banyak percobaan. Coba lagi nanti"
                "ERROR_USER_DISABLED" -> "Akun ini telah dinonaktifkan"
                "ERROR_INVALID_CREDENTIAL" -> "Email atau password salah"
                else -> "Email atau password salah"
            }
            Result.failure(Exception(message))
        } catch (e: Exception) {
            Result.failure(Exception("Terjadi kesalahan. Coba lagi nanti"))
        }
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<AuthResult> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            _isLoggedIn.value = true
            Result.success(result)
        } catch (e: FirebaseAuthException) {
            val message = when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> "Email sudah terdaftar. Silakan sign in"
                "ERROR_WEAK_PASSWORD" -> "Password terlalu lemah. Gunakan minimal 6 karakter"
                "ERROR_INVALID_EMAIL" -> "Format email tidak valid"
                "ERROR_TOO_MANY_REQUESTS" -> "Terlalu banyak percobaan. Coba lagi nanti"
                else -> "Gagal membuat akun. Coba lagi nanti"
            }
            Result.failure(Exception(message))
        } catch (e: Exception) {
            Result.failure(Exception("Terjadi kesalahan. Coba lagi nanti"))
        }
    }

    suspend fun signInAnonymously(): Result<AuthResult> {
        return try {
            val result = auth.signInAnonymously().await()
            _isLoggedIn.value = true
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun linkAnonymousToEmail(email: String, password: String): Result<AuthResult> {
        return try {
            val user = auth.currentUser ?: throw Exception("No user to link")
            val credential = EmailAuthProvider.getCredential(email, password)
            val result = user.linkWithCredential(credential).await()
            _isLoggedIn.value = true
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun linkAnonymousToGoogle(idToken: String): Result<AuthResult> {
        return try {
            val user = auth.currentUser ?: throw Exception("No user to link")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = user.linkWithCredential(credential).await()
            _isLoggedIn.value = true
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
        _isLoggedIn.value = false
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthException) {
            val message = when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Invalid email format"
                "ERROR_USER_NOT_FOUND" -> "No account found with this email"
                "ERROR_TOO_MANY_REQUESTS" -> "Too many requests. Try again later"
                else -> e.message ?: "Failed to send reset email"
            }
            Result.failure(Exception(message))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to send reset email: ${e.message}"))
        }
    }
}
