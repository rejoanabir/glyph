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
        window.statusBarColor = Color(0xFF07080A).toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color(0xFF07080A).toArgb()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF07080A),
                    surface = Color(0xFF0C0E14),
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFF0284C7),
                    tertiary = Color(0xFF10B981),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC)
                )
            ) {
                AntigravityMainWorkspace()
            }
        }
    }
}
