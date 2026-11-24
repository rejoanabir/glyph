package com.veltusstudio.glyph.agent.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.daemon.DaemonState
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import kotlinx.coroutines.delay

@Composable
fun EngineSetupScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val context = LocalContext.current
    val daemonManager = remember { AgyDaemonManager.getInstance(context) }
    val daemonState by daemonManager.daemonState.collectAsState()
    val statusMsg by daemonManager.statusMessage.collectAsState()

    var progressPct by remember { mutableStateOf(48) }
    var currentStepIndex by remember { mutableStateOf(2) }

    LaunchedEffect(Unit) {
        daemonManager.startOrConnectDaemon()
        while (progressPct < 100) {
            delay(150)
            progressPct = (progressPct + 8).coerceAtMost(100)
            if (progressPct >= 75) currentStepIndex = 3
            if (progressPct >= 95) currentStepIndex = 4
        }
    }

    val steps = listOf(
        "Locate ARM64 binaries (agy, agy_ide_bridge)" to (currentStepIndex >= 1),
        "Extract to private executable sandbox" to (currentStepIndex >= 2),
        "Chmod 755 executable permissions" to (currentStepIndex >= 3),
        "Wire gRPC loopback handshake (:1237)" to (daemonState == DaemonState.RUNNING || progressPct >= 100)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "STEP 05 // NATIVE ENGINE SUBSYSTEM",
            color = colors.accentPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.08.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Daemon Engine Setup",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Extracting 64-bit ARM64 native binaries into private executable space and configuring loopback telemetry.",
            color = colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4-step execution checklist
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                steps.forEachIndexed { idx, (label, isDone) ->
                    val isCurrent = idx + 1 == currentStepIndex && !isDone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when {
                                isDone -> colors.statusEmerald
                                isCurrent -> colors.accentPrimary
                                else -> colors.surfaceTerminal
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDone || isCurrent) Color.Transparent else colors.borderActive
                            ),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isDone) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                } else if (isCurrent) {
                                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                                } else {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(colors.textMuted))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                color = if (isDone || isCurrent) colors.textPrimary else colors.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isDone || isCurrent) FontWeight.Medium else FontWeight.Normal
                            )
                            Text(
                                text = when {
                                    isDone -> "COMPLETED"
                                    isCurrent -> "IN PROGRESS"
                                    else -> "PENDING"
                                },
                                color = when {
                                    isDone -> colors.statusEmerald
                                    isCurrent -> colors.accentPrimary
                                    else -> colors.textMuted
                                },
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Progress bar container
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceTerminal,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (daemonState == DaemonState.RUNNING) "ENGINE DAEMON ACTIVE (:1237)" else statusMsg.uppercase(),
                        color = colors.accentPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$progressPct%",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.borderSubtle)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressPct / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.accentPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Launch Button
        Button(
            onClick = onComplete,
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentPrimary,
                contentColor = colors.accentOnPrimary
            ),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "COMPLETE EXTRACTION & LAUNCH IDE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
