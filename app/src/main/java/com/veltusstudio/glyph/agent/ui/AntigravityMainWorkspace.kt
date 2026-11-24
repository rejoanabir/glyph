package com.veltusstudio.glyph.agent.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.veltusstudio.glyph.agent.ui.tabs.*
import kotlinx.coroutines.launch
import java.io.File

enum class AntigravityTab(val label: String) {
    WORKBENCH("WORKBENCH"),
    EXPLORER("EXPLORER"),
    TERMINAL("TERMINAL"),
    DIFFS("DIFFS"),
    HUB("HUB")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityMainWorkspace(
    initialWorkspace: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val daemonManager = remember { AgyDaemonManager.getInstance(context) }
    val chatService = remember { AgyChatService.instance }
    val daemonState by daemonManager.daemonState.collectAsState()
    val statusMsg by daemonManager.statusMessage.collectAsState()

    var activeTab by remember { mutableStateOf(AntigravityTab.WORKBENCH) }

    // Resolve default workspace directory
    var currentWorkspace by remember {
        val defaultDir = listOf(
            File("/storage/emulated/0/CodeOnTheGoProjects/Glyph"),
            File("/storage/emulated/0/CodeOnTheGoProjects"),
            File("/storage/emulated/0/Download"),
            File(context.filesDir, "workspace").apply { mkdirs() }
        ).firstOrNull { it.exists() && it.canRead() }?.absolutePath ?: context.filesDir.absolutePath

        mutableStateOf(initialWorkspace ?: defaultDir)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    // Automatically ensure daemon is alive
    LaunchedEffect(currentWorkspace) {
        daemonManager.startOrConnectDaemon(currentWorkspace)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            WorkspaceDrawer(
                currentWorkspace = currentWorkspace,
                onSelectWorkspace = { newPath ->
                    currentWorkspace = newPath
                    daemonManager.startOrConnectDaemon(newPath)
                },
                onNewSession = {
                    chatService.newSession()
                    activeTab = AntigravityTab.WORKBENCH
                },
                daemonManager = daemonManager,
                onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color(0xFF07080A),
            topBar = {
                Surface(
                    color = Color(0xFF090B10),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF161A24)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Column(
                                modifier = Modifier.clickable {
                                    if (daemonState != DaemonState.RUNNING) {
                                        daemonManager.startOrConnectDaemon(currentWorkspace)
                                    }
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "ANTIGRAVITY // IDE",
                                        color = Color(0xFFF8FAFC),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.08.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val dotColor = when (daemonState) {
                                        DaemonState.RUNNING -> Color(0xFF10B981)
                                        DaemonState.STARTING -> Color(0xFFF59E0B)
                                        DaemonState.ERROR -> Color(0xFFEF4444)
                                        DaemonState.STOPPED -> Color(0xFF64748B)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(dotColor.copy(alpha = if (daemonState == DaemonState.RUNNING) dotAlpha else 1f))
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (daemonState == DaemonState.RUNNING) "ONLINE · HUB :1237" else statusMsg.uppercase(),
                                        color = if (daemonState == DaemonState.RUNNING) Color(0xFF64748B) else Color(0xFFF59E0B),
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Project pill
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color(0xFF131A26),
                                        modifier = Modifier.clickable {
                                            coroutineScope.launch { drawerState.open() }
                                        }
                                    ) {
                                        Text(
                                            text = File(currentWorkspace).name.ifBlank { "ROOT" }.uppercase(),
                                            color = Color(0xFF38BDF8),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    chatService.newSession()
                                    activeTab = AntigravityTab.WORKBENCH
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Session",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { daemonManager.startOrConnectDaemon(currentWorkspace) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reconnect",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            },
            bottomBar = {
                Surface(
                    color = Color(0xFF090B10),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF161A24)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(56.dp)
                    ) {
                        AntigravityTab.values().forEach { tab ->
                            val isSelected = activeTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { activeTab = tab },
                                icon = {
                                    val icon = when (tab) {
                                        AntigravityTab.WORKBENCH -> Icons.Default.SmartToy
                                        AntigravityTab.EXPLORER -> Icons.Default.Folder
                                        AntigravityTab.TERMINAL -> Icons.Default.Code
                                        AntigravityTab.DIFFS -> Icons.Default.Difference
                                        AntigravityTab.HUB -> Icons.Default.Tune
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF38BDF8),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B),
                                    indicatorColor = Color(0x1F38BDF8)
                                )
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Crossfade(targetState = activeTab, label = "tabCrossfade") { tab ->
                    when (tab) {
                        AntigravityTab.WORKBENCH -> WorkbenchTab(
                            workspaceDir = currentWorkspace,
                            chatService = chatService,
                            daemonManager = daemonManager
                        )
                        AntigravityTab.EXPLORER -> ExplorerTab(
                            workspaceDir = currentWorkspace,
                            onSendFileToAgent = { file ->
                                activeTab = AntigravityTab.WORKBENCH
                                try {
                                    val content = file.readText().take(4000)
                                    chatService.sendPrompt(
                                        "Examine and review file: ${file.name}\n```\n$content\n```",
                                        currentWorkspace
                                    )
                                } catch (e: Exception) {
                                    chatService.sendPrompt(
                                        "Examine file at path: ${file.absolutePath}",
                                        currentWorkspace
                                    )
                                }
                            }
                        )
                        AntigravityTab.TERMINAL -> TerminalTab(
                            workspaceDir = currentWorkspace
                        )
                        AntigravityTab.DIFFS -> DiffsTab(
                            workspaceDir = currentWorkspace
                        )
                        AntigravityTab.HUB -> HubTab(
                            daemonManager = daemonManager,
                            workspaceDir = currentWorkspace
                        )
                    }
                }
            }
        }
    }
}
