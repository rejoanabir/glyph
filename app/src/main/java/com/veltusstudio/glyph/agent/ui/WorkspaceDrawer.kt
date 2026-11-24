package com.veltusstudio.glyph.agent.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.agent.core.AgyChatService
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.daemon.DaemonState
import java.io.File

@Composable
fun WorkspaceDrawer(
    currentWorkspace: String,
    onSelectWorkspace: (String) -> Unit,
    onNewSession: () -> Unit,
    daemonManager: AgyDaemonManager,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val daemonState by daemonManager.daemonState.collectAsState()
    var showCustomPathDialog by remember { mutableStateOf(false) }
    var customPathInput by remember { mutableStateOf("") }
    var selectedSafetyMode by remember { mutableStateOf("Autonomous") }

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
                    color = Color(0xFFF8FAFC),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        text = "Specify an absolute path on storage:",
                        color = Color(0xFF94A3B8),
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
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF8FAFC)
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
                    Text(text = "OPEN", color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPathDialog = false }) {
                    Text(text = "CANCEL", color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = Color(0xFF0C0E14)
        )
    }

    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp),
        drawerContainerColor = Color(0xFF07090D),
        drawerContentColor = Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0284C7),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "AG",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ANTIGRAVITY",
                            color = Color(0xFFF8FAFC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.08.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "MOBILE IDE · v2.1",
                            color = Color(0xFF64748B),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                IconButton(onClick = onCloseDrawer, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action: New Session
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF0E121B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNewSession()
                        onCloseDrawer()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Session",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEW CASCADE SESSION",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Workspaces section
            Text(
                text = "WORKSPACES",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            presetWorkspaces.forEach { dir ->
                val isSelected = dir.absolutePath == currentWorkspace
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) Color(0xFF131A26) else Color(0xFF0A0D14),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF0284C7) else Color(0xFF1A1F2C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
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
                            tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dir.name.ifBlank { "Root" },
                                color = if (isSelected) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = dir.absolutePath,
                                color = Color(0xFF475569),
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
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2330)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "Custom Path",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OPEN CUSTOM FOLDER...",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Safety Mode
            Text(
                text = "EXECUTION SAFETY MODE",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            listOf("Autonomous", "Supervised", "Read-Only").forEach { mode ->
                val isSelected = selectedSafetyMode == mode
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) Color(0xFF131A26) else Color(0xFF0A0D14),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF0284C7) else Color(0xFF1A1F2C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { selectedSafetyMode = mode }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mode.uppercase(),
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Storage Permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E170A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB45309)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "STORAGE PERMISSION",
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap to grant All Files access for full device workspace support.",
                            color = Color(0xFFD97706),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Daemon status footer
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF0A0D14),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1F2C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "DAEMON RUNTIME",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = when (daemonState) {
                                DaemonState.RUNNING -> "ACTIVE (HUB :1237)"
                                DaemonState.STARTING -> "STARTING..."
                                DaemonState.ERROR -> "ERROR / OFFLINE"
                                DaemonState.STOPPED -> "STOPPED"
                            },
                            color = when (daemonState) {
                                DaemonState.RUNNING -> Color(0xFF10B981)
                                DaemonState.STARTING -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = { daemonManager.startOrConnectDaemon(currentWorkspace) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Daemon",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
