package com.veltusstudio.glyph.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import com.veltusstudio.glyph.agent.ui.theme.GlyphTheme
import com.veltusstudio.glyph.agent.ui.theme.LocalActivePalette
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import com.veltusstudio.glyph.agent.ui.theme.PaletteId

class AgentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val activePaletteState = remember { mutableStateOf(PaletteId.NOIR_VIOLET) }
            CompositionLocalProvider(LocalActivePalette provides activePaletteState) {
                GlyphTheme(paletteId = activePaletteState.value) {
                    val colors = LocalGlyphColors.current
                    @Suppress("DEPRECATION")
                    window.statusBarColor = colors.surfaceGlass.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = colors.surfaceGlass.toArgb()

                    AntigravityMainWorkspace()
                }
            }
        }
    }
}
