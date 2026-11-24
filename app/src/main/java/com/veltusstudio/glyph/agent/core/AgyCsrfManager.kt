package com.veltusstudio.glyph.agent.core

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Manages security framing and CSRF tokens for communication with the Antigravity Hub (:1237)
 * and Go IDE Bridge (:1236).
 */
class AgyCsrfManager private constructor() {

    companion object {
        private const val TAG = "AgyCsrfManager"
        const val TOKEN_LENGTH = 12
        const val TOKEN_PREFIX = "x-ag"
        const val TOKEN_SUFFIX = "_9qx"
        private const val TOKEN_CHARSET = "0123456789abcdefghijklmnopqrstuvwxyz"

        val instance: AgyCsrfManager by lazy { AgyCsrfManager() }

        fun generateRandomToken(): String {
            val random = SecureRandom()
            val sb = StringBuilder(TOKEN_LENGTH)
            for (i in 0 until TOKEN_LENGTH) {
                sb.append(TOKEN_CHARSET[random.nextInt(TOKEN_CHARSET.length)])
            }
            return sb.toString()
        }

        fun buildFramedHeader(token: String): String {
            val clean = token.trim().take(TOKEN_LENGTH).padEnd(TOKEN_LENGTH, '0')
            return "$TOKEN_PREFIX$clean$TOKEN_SUFFIX"
        }
    }

    // 12-char bridge security token
    private val _securityToken = MutableStateFlow(readInitialSecurityToken())
    val securityToken: String get() = _securityToken.value

    // AGY Hub CSRF token (UUID string)
    private val _hubCsrfToken = MutableStateFlow("")
    val hubCsrfToken: String get() = _hubCsrfToken.value

    private val _csrfEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val csrfEvents: SharedFlow<String> = _csrfEvents.asSharedFlow()

    private val bridgeHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private fun readInitialSecurityToken(): String {
        val candidates = listOf(
            File("/data/user/0/ai.antigem/files/home/.antigem_bridge/csrf_token.txt"),
            File(System.getenv("HOME") ?: "", ".antigem_bridge/csrf_token.txt")
        )
        for (f in candidates) {
            try {
                if (f.exists()) {
                    val t = f.readText().trim()
                    if (t.isNotBlank()) return t
                }
            } catch (_: Exception) {}
        }
        return "0w6b8feu6b85"
    }

    fun setSecurityToken(token: String) {
        if (token.isNotBlank()) {
            _securityToken.value = token
        }
    }

    fun setHubCsrfToken(csrf: String) {
        if (csrf.isNotBlank()) {
            _hubCsrfToken.value = csrf
            Log.d(TAG, "Hub CSRF token updated (${csrf.take(8)}...)")
        }
    }

    val currentFramedHeader: String
        get() = buildFramedHeader(securityToken)

    fun refreshFromBridge(bridgeUrl: String = "http://127.0.0.1:1236"): String {
        try {
            val req = Request.Builder()
                .url("$bridgeUrl/api/status")
                .header(currentFramedHeader, "true")
                .get()
                .build()

            bridgeHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return ""
                    val json = JSONObject(body)
                    val hubObj = json.optJSONObject("hub")
                    val csrf = hubObj?.optString("csrf_token")
                    if (!csrf.isNullOrBlank()) {
                        setHubCsrfToken(csrf)
                        return csrf
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh CSRF from bridge: ${e.message}")
        }
        return hubCsrfToken
    }

    fun notifyCsrfExpired(endpoint: String = "", usedToken: String? = null) {
        val current = hubCsrfToken
        if (usedToken != null && usedToken != current) return
        Log.w(TAG, "CSRF token expired on $endpoint, clearing cache")
        _hubCsrfToken.compareAndSet(current, "")
        _csrfEvents.tryEmit("CSRF expired on $endpoint")
    }
}
