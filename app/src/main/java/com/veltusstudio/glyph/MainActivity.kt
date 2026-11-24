package com.veltusstudio.glyph

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.veltusstudio.glyph.agent.ui.AntigravityMainWorkspace
import com.veltusstudio.glyph.agent.ui.theme.GlyphTheme
import com.veltusstudio.glyph.agent.ui.theme.LocalActivePalette
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import com.veltusstudio.glyph.agent.ui.theme.PaletteId

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()
        startKeepAliveService()

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

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startKeepAliveService() {
        try {
            val serviceIntent = Intent(this, GlyphKeepAliveService::class.java).apply {
                putExtra("status", "Glyph Agent Subsystem Active")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Keep alive service is optional
        }
    }
}
