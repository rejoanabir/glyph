package com.veltusstudio.glyph.agent.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.veltusstudio.glyph.agent.core.AgyCsrfManager
import com.veltusstudio.glyph.agent.core.EngineMode
import com.veltusstudio.glyph.agent.core.GlyphAuthManager
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.daemon.DaemonState
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HubSetupDialog(
    onDismissRequest: () -> Unit,
    onNavigateAuth: () -> Unit,
    workspaceDir: String
) {
    val context = LocalContext.current
    val colors = LocalGlyphColors.current
    val authManager = remember { GlyphAuthManager.getInstance(context) }
    val daemonManager = remember { AgyDaemonManager.getInstance(context) }
    val csrfManager = remember { AgyCsrfManager.instance }
    val authState by authManager.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var isPort1236Open by remember { mutableStateOf(false) }
    var isPort1237Open by remember { mutableStateOf(false) }
    var isProbing by remember { mutableStateOf(false) }

    fun probePorts() {
        coroutineScope.launch {
            isProbing = true
            withContext(Dispatchers.IO) {
                isPort1236Open = daemonManager.isPortOpen(1236, 400)
                isPort1237Open = daemonManager.isPortOpen(1237, 400)
                csrfManager.getEffectiveCsrfToken()
            }
            isProbing = false
        }
    }

    LaunchedEffect(Unit) {
        probePorts()
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = "[ ENGINE & HUB CONTROLLER ]",
                    color = colors.accentPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.08.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configure Antigravity Hub (:1237) and Autonomous Gemini Direct Cloud Engine.",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Live Port Telemetry
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SYSTEM LOOPBACK PORTS",
                                color = colors.textMuted,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isProbing) {
                                CircularProgressIndicator(
                                    color = colors.accentPrimary,
                                    strokeWidth = 1.5.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                            } else {
                                Text(
                                    text = "TAP TO RE-PROBE",
                                    color = colors.accentPrimary,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { probePorts() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Port 1236 Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isPort1236Open) colors.statusEmerald else colors.statusCrimson)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PORT 1236 [IDE BRIDGE]",
                                    color = colors.textPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (isPort1236Open) "LISTENING" else "OFFLINE",
                                color = if (isPort1236Open) colors.statusEmerald else colors.textMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Port 1237 Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isPort1237Open) colors.statusEmerald else colors.statusCrimson)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PORT 1237 [AGY HUB gRPC]",
                                    color = colors.textPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (isPort1237Open) "CONNECTED" else "OFFLINE",
                                color = if (isPort1237Open) colors.statusEmerald else colors.textMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Engine Mode Selector
                Text(
                    text = "ACTIVE ENGINE MODE",
                    color = colors.textMuted,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EngineMode.values().forEach { mode ->
                        val isSelected = authState.engineMode == mode
                        val btnBg = if (isSelected) colors.accentPrimary else colors.surfaceCard
                        val btnBorder = if (isSelected) colors.accentPrimary else colors.borderSubtle
                        val textColor = if (isSelected) colors.accentOnPrimary else colors.textSecondary

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = btnBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, btnBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    authManager.setEngineMode(mode)
                                    val toastMsg = "Engine mode: ${mode.label}"
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                            ) {
                                Text(
                                    text = when (mode) {
                                        EngineMode.AUTO -> "AUTO"
                                        EngineMode.HUB_ONLY -> "HUB ONLY"
                                        EngineMode.CLOUD_DIRECT -> "CLOUD"
                                    },
                                    color = textColor,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Termux Command Card
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "START NATIVE HUB IN TERMUX",
                            color = colors.accentPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android application sandbox restricts direct exec(). Run the bridge daemon in Termux:",
                            color = colors.textSecondary,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val termuxCmd = "test_bridge -f -p 1236 --hub-port 1237"

                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = colors.bgObsidian,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = termuxCmd,
                                color = colors.accentPrimary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Termux Command", termuxCmd)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "COPY TERMUX COMMAND",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismissRequest()
                            onNavigateAuth()
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "AUTH SETTINGS",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "CLOSE",
                            color = colors.textPrimary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
