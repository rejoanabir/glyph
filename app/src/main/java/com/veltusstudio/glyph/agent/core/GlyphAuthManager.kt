package com.veltusstudio.glyph.agent.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthMethod(val label: String) {
    GOOGLE("GOOGLE OAUTH"),
    API_KEY("DIRECT API KEY"),
    SELF_HOSTED("SELF-HOSTED HUB"),
    GUEST("LOCAL GUEST")
}

data class GlyphAuthState(
    val isLoggedIn: Boolean = true,
    val authMethod: AuthMethod = AuthMethod.GOOGLE,
    val userEmail: String = "developer@glyph.ai",
    val displayName: String = "Glyph Developer",
    val apiKey: String = "",
    val hubEndpoint: String = "127.0.0.1:1237",
    val tier: String = "PRO DEVELOPER"
) {
    val avatarLetter: String
        get() = when {
            displayName.isNotBlank() -> displayName.take(1).uppercase()
            userEmail.isNotBlank() -> userEmail.take(1).uppercase()
            else -> "G"
        }
}

class GlyphAuthManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "glyph_auth_prefs"
        private const val KEY_LOGGED_IN = "key_logged_in"
        private const val KEY_AUTH_METHOD = "key_auth_method"
        private const val KEY_USER_EMAIL = "key_user_email"
        private const val KEY_DISPLAY_NAME = "key_display_name"
        private const val KEY_API_KEY = "key_api_key"
        private const val KEY_HUB_ENDPOINT = "key_hub_endpoint"
        private const val KEY_TIER = "key_tier"

        @Volatile
        private var instance: GlyphAuthManager? = null

        fun getInstance(context: Context): GlyphAuthManager {
            return instance ?: synchronized(this) {
                instance ?: GlyphAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(loadInitialState())
    val authState: StateFlow<GlyphAuthState> = _authState.asStateFlow()

    private fun loadInitialState(): GlyphAuthState {
        val isLoggedIn = prefs.getBoolean(KEY_LOGGED_IN, true)
        val methodStr = prefs.getString(KEY_AUTH_METHOD, AuthMethod.GOOGLE.name) ?: AuthMethod.GOOGLE.name
        val method = try { AuthMethod.valueOf(methodStr) } catch (_: Exception) { AuthMethod.GOOGLE }
        val email = prefs.getString(KEY_USER_EMAIL, "developer@glyph.ai") ?: "developer@glyph.ai"
        val name = prefs.getString(KEY_DISPLAY_NAME, "Glyph Developer") ?: "Glyph Developer"
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val endpoint = prefs.getString(KEY_HUB_ENDPOINT, "127.0.0.1:1237") ?: "127.0.0.1:1237"
        val tier = prefs.getString(KEY_TIER, "PRO DEVELOPER") ?: "PRO DEVELOPER"

        return GlyphAuthState(
            isLoggedIn = isLoggedIn,
            authMethod = method,
            userEmail = email,
            displayName = name,
            apiKey = apiKey,
            hubEndpoint = endpoint,
            tier = tier
        )
    }

    fun loginWithGoogle(
        email: String = "developer@glyph.ai",
        name: String = "Glyph Developer"
    ) {
        val newState = GlyphAuthState(
            isLoggedIn = true,
            authMethod = AuthMethod.GOOGLE,
            userEmail = email,
            displayName = name,
            tier = "PRO DEVELOPER"
        )
        saveState(newState)
    }

    fun loginWithApiKey(
        key: String,
        provider: String = "GEMINI"
    ) {
        val cleanKey = key.trim()
        val masked = if (cleanKey.length > 8) "${cleanKey.take(6)}...${cleanKey.takeLast(4)}" else cleanKey
        val newState = GlyphAuthState(
            isLoggedIn = true,
            authMethod = AuthMethod.API_KEY,
            userEmail = "$provider Key Active ($masked)",
            displayName = "$provider Frontier",
            apiKey = cleanKey,
            tier = "ENTERPRISE DIRECT"
        )
        saveState(newState)
    }

    fun loginWithSelfHosted(
        endpoint: String,
        csrfToken: String = ""
    ) {
        val cleanEndpoint = endpoint.trim().ifBlank { "127.0.0.1:1237" }
        val newState = GlyphAuthState(
            isLoggedIn = true,
            authMethod = AuthMethod.SELF_HOSTED,
            userEmail = "node@$cleanEndpoint",
            displayName = "Self-Hosted Hub",
            hubEndpoint = cleanEndpoint,
            tier = "CLUSTER NODE"
        )
        if (csrfToken.isNotBlank()) {
            AgyCsrfManager.instance.setHubCsrfToken(csrfToken)
        }
        saveState(newState)
    }

    fun logout() {
        val guestState = GlyphAuthState(
            isLoggedIn = false,
            authMethod = AuthMethod.GUEST,
            userEmail = "offline@glyph.local",
            displayName = "Guest User",
            tier = "ANONYMOUS"
        )
        saveState(guestState)
    }

    private fun saveState(state: GlyphAuthState) {
        _authState.value = state
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, state.isLoggedIn)
            .putString(KEY_AUTH_METHOD, state.authMethod.name)
            .putString(KEY_USER_EMAIL, state.userEmail)
            .putString(KEY_DISPLAY_NAME, state.displayName)
            .putString(KEY_API_KEY, state.apiKey)
            .putString(KEY_HUB_ENDPOINT, state.hubEndpoint)
            .putString(KEY_TIER, state.tier)
            .apply()
    }
}
