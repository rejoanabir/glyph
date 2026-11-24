package com.veltusstudio.glyph.agent.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.R
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.daemon.DaemonState
import com.veltusstudio.glyph.agent.ui.theme.LocalActivePalette
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import com.veltusstudio.glyph.agent.ui.theme.PaletteId
import java.io.File

@Composable
fun WorkspaceDrawer(
    currentWorkspace: String,
    onSelectWorkspace: (String) -> Unit,
    onNewSession: () -> Unit,
    onNavigateScreen: (String) -> Unit,
    daemonManager: AgyDaemonManager,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val activePaletteState = LocalActivePalette.current
    val context = LocalContext.current
    val daemonState by daemonManager.daemonState.collectAsState()

    var showCustomPathDialog by remember { mutableStateOf(false) }
    var customPathInput by remember { mutableStateOf("") }
    var selectedSafetyMode by remember { mutableStateOf("SUPERVISED") }

    val presetWorkspaces = remember {
        listOf(
            File("/storage/emulated/0/CodeOnTheGoProjects/Glyph"),
            File("/storage/emulated/0/CodeOnTheGoProjects"),
            File("/storage/emulated/0/Download"),
            File(context.filesDir, "workspace").apply { mkdirs() }
        ).filter { it.exists() || it.mkdirs() }
    }

    if (showCustomPathDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPathDialog = false },
            title = {
                Text(
                    text = "OPEN WORKSPACE DIRECTORY",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        text = "Specify an absolute path on storage:",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        placeholder = {
                            Text(
                                text = "/storage/emulated/0/...",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = customPathInput.trim()
                        if (trimmed.isNotBlank()) {
                            val f = File(trimmed)
                            if (!f.exists()) f.mkdirs()
                            onSelectWorkspace(f.absolutePath)
                        }
                        showCustomPathDialog = false
                    }
                ) {
                    Text(text = "OPEN", color = colors.accentPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPathDialog = false }) {
                    Text(text = "CANCEL", color = colors.textMuted, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = colors.surfaceCard
        )
    }

    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp),
        drawerContainerColor = colors.surfaceGlass,
        drawerContentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header with Original Brushed Silver Glyph Logo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.glyph_logo),
                            contentDescription = "Glyph Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "GLYPH // MOBILE IDE",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.06.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "v2.1 · ARM64 · ANTIGRAVITY ENGINE",
                            color = colors.textMuted,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                IconButton(onClick = onCloseDrawer, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CTA: New Cascade Session
            Button(
                onClick = {
                    onNewSession()
                    onCloseDrawer()
                },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.accentOnPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEW CASCADE SESSION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.08.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Screen Navigation Flow
            Text(
                text = "SCREEN FLOW NAVIGATOR",
                color = colors.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            val screens = listOf(
                "boot" to "01 BOOT SEQUENCE",
                "onboard" to "02 ONBOARDING CAROUSEL",
                "auth" to "03 AUTH & CREDENTIALS",
                "perms" to "04 SYSTEM PERMISSIONS",
                "daemon" to "05 DAEMON ENGINE SETUP",
                "ide" to "06 MAIN IDE SHELL"
            )

            screens.forEach { (id, title) ->
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable {
                            onNavigateScreen(id)
                            onCloseDrawer()
                        }
                ) {
                    Text(
                        text = title,
                        color = if (id == "ide") colors.accentPrimary else colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Theme Palette Switcher (All 5 Live Palettes)
            Text(
                text = "LIVE PALETTE ENGINE",
                color = colors.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            PaletteId.values().forEach { palette ->
                val isSelected = activePaletteState.value == palette
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) colors.surfaceElevated else colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) colors.accentPrimary else colors.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.5.dp)
                        .clickable { activePaletteState.value = palette }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette.dotColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = palette.label,
                                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = palette.vibe,
                                    color = colors.textMuted,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = colors.accentPrimary, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Workspaces section
            Text(
                text = "WORKSPACES",
                color = colors.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            presetWorkspaces.forEach { dir ->
                val isSelected = dir.absolutePath == currentWorkspace
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) colors.surfaceElevated else colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) colors.accentPrimary else colors.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.5.dp)
                        .clickable {
                            onSelectWorkspace(dir.absolutePath)
                            onCloseDrawer()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = "Folder",
                            tint = if (isSelected) colors.accentPrimary else colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dir.name.ifBlank { "Root" },
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = dir.absolutePath,
                                color = colors.textMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = { showCustomPathDialog = true },
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "+ OPEN CUSTOM FOLDER...",
                    color = colors.textSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Safety Mode
            Text(
                text = "EXECUTION SAFETY MODE",
                color = colors.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            listOf("AUTONOMOUS", "SUPERVISED", "READ-ONLY").forEach { mode ->
                val isSelected = selectedSafetyMode == mode
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) colors.surfaceElevated else colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) colors.accentPrimary else colors.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.5.dp)
                        .clickable { selectedSafetyMode = mode }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mode,
                            color = if (isSelected) colors.accentPrimary else colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = colors.accentPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Daemon status footer
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = colors.bgObsidian,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when (daemonState) {
                                        DaemonState.RUNNING -> colors.statusEmerald
                                        DaemonState.STARTING -> colors.statusAmber
                                        else -> colors.statusCrimson
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (daemonState) {
                                DaemonState.RUNNING -> "ACTIVE (HUB :1237)"
                                DaemonState.STARTING -> "STARTING..."
                                DaemonState.ERROR -> "OFFLINE / ERROR"
                                DaemonState.STOPPED -> "STOPPED"
                            },
                            color = colors.textSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = { daemonManager.startOrConnectDaemon(currentWorkspace) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Daemon",
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
