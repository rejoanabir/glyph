package com.veltusstudio.glyph.agent.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.agent.core.AgyCsrfManager
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.daemon.DaemonState
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

data class SupportedAiModel(
    val id: String,
    val name: String,
    val provider: String,
    val desc: String
)

val DEFAULT_MODELS = listOf(
    SupportedAiModel(
        id = "gemini-2.5-flash",
        name = "Google Gemini 2.5 Flash",
        provider = "Google AI",
        desc = "High-speed reasoning model with native tool execution."
    ),
    SupportedAiModel(
        id = "gemini-2.5-pro",
        name = "Google Gemini 2.5 Pro",
        provider = "Google AI",
        desc = "Deep-thinking frontier model for complex code architecture."
    ),
    SupportedAiModel(
        id = "claude-3-7-sonnet",
        name = "Anthropic Claude 3.7 Sonnet",
        provider = "Anthropic",
        desc = "State-of-the-art hybrid reasoning model."
    ),
    SupportedAiModel(
        id = "claude-3-5-haiku",
        name = "Anthropic Claude 3.5 Haiku",
        provider = "Anthropic",
        desc = "Ultra-fast lightweight instruction execution."
    )
)

@Composable
fun HubTab(
    daemonManager: AgyDaemonManager,
    workspaceDir: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val daemonState by daemonManager.daemonState.collectAsState()
    val statusMsg by daemonManager.statusMessage.collectAsState()
    var selectedModelId by remember { mutableStateOf("gemini-2.5-flash") }
    val csrfManager = remember { AgyCsrfManager.instance }

    var port1236Open by remember { mutableStateOf(false) }
    var port1237Open by remember { mutableStateOf(false) }

    fun refreshTelemetry() {
        port1236Open = daemonManager.isPortOpen(1236, 400)
        port1237Open = daemonManager.isPortOpen(1237, 400)
    }

    LaunchedEffect(Unit) {
        refreshTelemetry()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // Hub Engine Status Card
        Surface(
            shape = RoundedCornerShape(6.dp),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Engine",
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GLYPH ENGINE RUNTIME",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.08.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = {
                            refreshTelemetry()
                            daemonManager.startOrConnectDaemon(workspaceDir)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticChip(
                        label = "PORT 1236 [BRIDGE]",
                        isOpen = port1236Open,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticChip(
                        label = "PORT 1237 [HUB gRPC]",
                        isOpen = port1237Open,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { daemonManager.startOrConnectDaemon(workspaceDir) },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = colors.accentOnPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "START / RESTART",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = { daemonManager.stopDaemon() },
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "STOP DAEMON",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Model Selection Cards
        Text(
            text = "AI MODEL REGISTRY",
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        DEFAULT_MODELS.forEach { model ->
            val isSelected = model.id == selectedModelId
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isSelected) colors.surfaceElevated else colors.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) colors.accentPrimary else colors.borderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { selectedModelId = model.id }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = model.name,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = colors.accentGlow
                            ) {
                                Text(
                                    text = model.provider.uppercase(),
                                    color = colors.accentPrimary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = model.desc,
                            color = colors.textMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quota Telemetry Cards
        Text(
            text = "USAGE & QUOTA LIMITS",
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                QuotaRow(label = "5-Hour Burst Limit", usage = "12%", resetIn = "Resets in 3h 14m")
                Spacer(modifier = Modifier.height(8.dp))
                QuotaRow(label = "Weekly Generation Quota", usage = "28%", resetIn = "Resets Sunday")
                Spacer(modifier = Modifier.height(8.dp))
                QuotaRow(label = "Prompt Credits", usage = "UNLIMITED", resetIn = "Active Developer Tier")
            }
        }
    }
}

@Composable
fun DiagnosticChip(label: String, isOpen: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalGlyphColors.current
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = colors.surfaceTerminal,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOpen) colors.statusEmerald else colors.statusCrimson),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isOpen) colors.statusEmerald else colors.statusCrimson)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = label,
                    color = colors.textSecondary,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isOpen) "ONLINE" else "OFFLINE",
                    color = if (isOpen) colors.statusEmerald else colors.statusCrimson,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun QuotaRow(label: String, usage: String, resetIn: String) {
    val colors = LocalGlyphColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = resetIn,
                color = colors.textMuted,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = usage,
            color = colors.accentPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
