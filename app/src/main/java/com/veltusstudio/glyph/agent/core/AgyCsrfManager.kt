package com.veltusstudio.glyph.agent.core

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom

/**
 * Manages CSRF and security tokens for local communication with the Antigravity (AGY) daemon and Go IDE bridge.
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

    private val _token = MutableStateFlow("")
    val token: String get() = _token.value

    private val _csrfEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val csrfEvents: SharedFlow<String> = _csrfEvents.asSharedFlow()

    fun setToken(newToken: String) {
        if (newToken.isNotBlank()) {
            _token.value = newToken
            Log.d(TAG, "CSRF token initialized (${newToken.take(4)}...)")
        }
    }

    suspend fun awaitToken(timeoutMs: Long = 10_000L): String {
        return token.ifBlank {
            withTimeoutOrNull(timeoutMs) {
                _token.first { it.isNotBlank() }
            } ?: ""
        }
    }

    fun notifyCsrfExpired(endpoint: String = "", usedToken: String? = null) {
        val current = token
        if (usedToken != null && usedToken != current) {
            return
        }
        Log.w(TAG, "CSRF token expired on $endpoint")
        _token.compareAndSet(current, "")
        _csrfEvents.tryEmit("CSRF token expired on $endpoint")
    }

    val currentFramedHeader: String
        get() = buildFramedHeader(token)
}
