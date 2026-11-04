package com.accesspath.data.remote

import android.content.Context
import com.accesspath.domain.AppError
import com.accesspath.domain.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Supabase client over HTTPS: Auth (Google ID token, email/password, refresh) and the REST API.
 * Only the publishable key ships in the app; what each user may read or write is enforced on the
 * server by row level security.
 */
class SupabaseClient(context: Context, private val baseUrl: String, private val apiKey: String) {
    @Serializable data class User(val id: String, val email: String? = null)
    @Serializable data class Session(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_at") val expiresAt: Long = 0,
        val user: User
    )

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val prefs = context.applicationContext.getSharedPreferences("accesspath_auth", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()

    val configured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    var session: Session? = prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }
        private set

    private fun store(s: Session?) {
        session = s
        prefs.edit().apply { if (s == null) remove(KEY) else putString(KEY, json.encodeToString(Session.serializer(), s)) }.apply()
    }

    // ---- Auth -----------------------------------------------------------------------------------

    suspend fun signInWithGoogle(idToken: String, rawNonce: String) =
        authToken("id_token", """{"provider":"google","id_token":${q(idToken)},"nonce":${q(rawNonce)}}""")

    suspend fun signInWithEmail(email: String, password: String) =
        authToken("password", """{"email":${q(email)},"password":${q(password)}}""")

    /** Returns true when the account can sign in now, false when the email still has to be confirmed. */
    suspend fun signUpWithEmail(email: String, password: String): Boolean {
        val body = call("POST", "/auth/v1/signup", """{"email":${q(email)},"password":${q(password)}}""", bearer = apiKey)
        val obj = json.parseToJsonElement(body) as? JsonObject
        return if (obj?.containsKey("access_token") == true) { store(json.decodeFromString(Session.serializer(), body)); true } else false
    }

    suspend fun signOut() {
        val s = session ?: return
        runCatching { call("POST", "/auth/v1/logout", "{}", bearer = s.accessToken) }
        store(null)
    }

    private suspend fun authToken(grant: String, body: String) {
        store(json.decodeFromString(Session.serializer(), call("POST", "/auth/v1/token?grant_type=$grant", body, bearer = apiKey)))
    }

    /** A valid access token, refreshed when it is about to expire; null when signed out. */
    private suspend fun accessToken(): String? = refreshLock.withLock {
        val s = session ?: return null
        if (s.expiresAt == 0L || s.expiresAt - 60 > System.currentTimeMillis() / 1000) return s.accessToken
        return try {
            authToken("refresh_token", """{"refresh_token":${q(s.refreshToken)}}""")
            session?.accessToken
        } catch (e: AppError) {
            if (e.code == ErrorCode.SERVER) store(null) // refresh token rejected: sign out
            throw e
        }
    }

    // ---- REST -----------------------------------------------------------------------------------

    suspend fun get(pathAndQuery: String): String = call("GET", "/rest/v1/$pathAndQuery", null, bearer = accessToken() ?: apiKey)

    suspend fun upsert(table: String, body: String, onConflict: String) {
        val token = accessToken() ?: throw AppError(ErrorCode.NEEDS_SIGN_IN)
        call("POST", "/rest/v1/$table?on_conflict=$onConflict", body, bearer = token, prefer = "resolution=merge-duplicates,return=minimal")
    }

    suspend fun insertIgnoringDuplicates(table: String, body: String) {
        val token = accessToken() ?: throw AppError(ErrorCode.NEEDS_SIGN_IN)
        call("POST", "/rest/v1/$table?on_conflict=id", body, bearer = token, prefer = "resolution=ignore-duplicates,return=minimal")
    }

    /** Calls a database function (POST /rest/v1/rpc/name) as the signed-in user. */
    suspend fun rpc(name: String, body: String): String {
        val token = accessToken() ?: throw AppError(ErrorCode.NEEDS_SIGN_IN)
        return call("POST", "/rest/v1/rpc/$name", body, bearer = token)
    }

    private suspend fun call(method: String, path: String, body: String?, bearer: String, prefer: String? = null): String =
        withContext(Dispatchers.IO) {
            if (!configured) throw AppError(ErrorCode.NETWORK, "Server not configured")
            val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000; readTimeout = 20_000
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Accept", "application/json")
                prefer?.let { setRequestProperty("Prefer", it) }
                if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw AppError(ErrorCode.SERVER, errorMessage(text, code))
                text
            } catch (e: IOException) {
                throw AppError(ErrorCode.NETWORK, e.message)
            } finally {
                conn.disconnect()
            }
        }

    private fun errorMessage(text: String, code: Int): String = runCatching {
        val o = json.parseToJsonElement(text) as JsonObject
        listOf("error_description", "msg", "message", "error").firstNotNullOfOrNull { o[it]?.jsonPrimitive?.content }
    }.getOrNull() ?: "HTTP $code"

    private fun q(s: String) = json.encodeToString(String.serializer(), s)

    private companion object { const val KEY = "session" }
}
