package com.example.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

/**
 * Thin wrapper around AndroidX CredentialManager that launches the Google
 * One Tap bottom-sheet and returns a Firebase-ready ID token.
 *
 * The flow uses the Web OAuth client ID registered in google-services.json
 * (client_type 3) so Firebase can verify the returned ID token server-side.
 */
class GoogleAuthHelper(private val context: Context) {

    private val credentialManager: CredentialManager = CredentialManager.create(context)

    suspend fun signIn(): Result<String> {
        val signInOption = GetGoogleIdOption.Builder()
            .setServerClientId(com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()

        return launchCredentialFlow(signInOption)
    }

    /**
     * Fallback path: only show accounts the user has previously authorized
     * with this app. Used when the full picker was dismissed or returned
     * no credentials.
     */
    suspend fun signInExistingOnly(): Result<String> {
        val signInOption = GetGoogleIdOption.Builder()
            .setServerClientId(com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(false)
            .build()

        return launchCredentialFlow(signInOption)
    }

    private suspend fun launchCredentialFlow(
        googleIdOption: GetGoogleIdOption
    ): Result<String> {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = context,
                request = request
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdToken.idToken)
            } else {
                Result.failure(Exception(context.getString(R.string.google_err_unknown_credential)))
            }
        } catch (_: GetCredentialCancellationException) {
            Result.failure(Exception(context.getString(R.string.google_err_cancelled)))
        } catch (_: NoCredentialException) {
            Result.failure(Exception(context.getString(R.string.google_err_no_credential)))
        } catch (e: GetCredentialException) {
            Result.failure(Exception(context.getString(R.string.google_err_generic, e.message ?: "")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
