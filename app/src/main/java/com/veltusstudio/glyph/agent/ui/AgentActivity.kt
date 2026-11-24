package com.veltusstudio.glyph.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

class AgentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.statusBarColor = Color(0xFF09090B).toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color(0xFF09090B).toArgb()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF09090B),
                    surface = Color(0xFF141417),
                    primary = Color(0xFF6366F1),
                    secondary = Color(0xFF38BDF8),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC)
                )
            ) {
                AntigravityAgentScreen(
                    workspaceDir = filesDir.absolutePath,
                    onClose = { finish() }
                )
            }
        }
    }
}
