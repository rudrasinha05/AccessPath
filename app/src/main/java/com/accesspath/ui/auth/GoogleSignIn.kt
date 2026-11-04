package com.accesspath.ui.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.accesspath.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/** Outcome of the Google account picker. */
sealed interface GoogleResult {
    data class Token(val idToken: String, val rawNonce: String) : GoogleResult
    data object Cancelled : GoogleResult
    data object NoAccount : GoogleResult
    data class Failed(val message: String?) : GoogleResult
}

/**
 * Shows Google's account picker and returns an ID token for Supabase. Google receives the SHA-256 of a
 * one-time nonce and Supabase the raw nonce, so a stolen token cannot be replayed.
 */
object GoogleSignIn {
    suspend fun idToken(activity: Activity): GoogleResult {
        val rawNonce = UUID.randomUUID().toString()
        val hashed = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashed).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
            } else GoogleResult.Failed("Unexpected credential type")
        } catch (e: GetCredentialCancellationException) {
            GoogleResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleResult.NoAccount
        } catch (e: Exception) {
            GoogleResult.Failed(e.message)
        }
    }
}
