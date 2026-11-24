package com.veltusstudio.glyph.agent.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.R
import com.veltusstudio.glyph.agent.core.AgyChatService
import com.veltusstudio.glyph.agent.core.GlyphAuthManager
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.daemon.DaemonState
import com.veltusstudio.glyph.agent.ui.screens.*
import com.veltusstudio.glyph.agent.ui.tabs.*
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import kotlinx.coroutines.launch
import java.io.File

enum class AntigravityTab(val label: String) {
    WORKBENCH("WORKBENCH"),
    EXPLORER("EXPLORER"),
    TERMINAL("TERMINAL"),
    DIFFS("DIFFS"),
    HUB("HUB")
}

import com.veltusstudio.glyph.agent.core.EngineMode
import com.veltusstudio.glyph.agent.ui.dialogs.HubSetupDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityMainWorkspace(
    initialWorkspace: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val colors = LocalGlyphColors.current

    val daemonManager = remember { AgyDaemonManager.getInstance(context) }
    val chatService = remember { AgyChatService.instance.apply { initialize(context) } }
    val authManager = remember { GlyphAuthManager.getInstance(context) }
    val daemonState by daemonManager.daemonState.collectAsState()
    val statusMsg by daemonManager.statusMessage.collectAsState()
    val authState by authManager.authState.collectAsState()

    var activeScreen by remember { mutableStateOf("ide") }
    var activeTab by remember { mutableStateOf(AntigravityTab.WORKBENCH) }
    var hubSubTab by remember { mutableStateOf(HubSubTab.RUNTIME) }
    var showHubDialog by remember { mutableStateOf(false) }

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

    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val radarPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarPulseScale"
    )
    val radarPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarPulseAlpha"
    )

    // Automatically ensure daemon is alive
    LaunchedEffect(currentWorkspace) {
        daemonManager.startOrConnectDaemon(currentWorkspace)
    }

    // Outer Screen Router
    when (activeScreen) {
        "boot" -> BootScreen(onBootComplete = { activeScreen = "ide" })
        "onboard" -> OnboardingScreen(onDone = { activeScreen = "ide" })
        "auth" -> AuthScreen(
            onProceed = { activeScreen = "ide" },
            onBackToIde = { activeScreen = "ide" }
        )
        "perms" -> PermissionsScreen(onProceed = { activeScreen = "daemon" })
        "daemon" -> EngineSetupScreen(onComplete = { activeScreen = "ide" })
        else -> {
            // Main IDE Shell
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
                        onNavigateScreen = { targetScreen ->
                            activeScreen = targetScreen
                        },
                        onOpenHubSubTab = { targetSubTab ->
                            hubSubTab = targetSubTab
                            activeTab = AntigravityTab.HUB
                        },
                        daemonManager = daemonManager,
                        onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
                    )
                }
            ) {
                Scaffold(
                    modifier = modifier.fillMaxSize(),
                    containerColor = colors.bgObsidian,
                    topBar = {
                        Surface(
                            color = colors.surfaceGlass,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            showHubDialog = true
                                        }
                                    ) {
                                        // Original Brushed Silver Glyph Logo
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.surfaceCard,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.glyph_logo),
                                                contentDescription = "Glyph Logo",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(2.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = "GLYPH // IDE",
                                            color = colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.08.sp,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Glowing Radar Indicator
                                        val dotColor = when {
                                            daemonState == DaemonState.RUNNING -> colors.statusEmerald
                                            daemonState == DaemonState.STARTING -> colors.statusAmber
                                            authState.engineMode == EngineMode.CLOUD_DIRECT || authState.apiKey.isNotBlank() -> colors.accentPrimary
                                            else -> colors.statusAmber
                                        }

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(14.dp)
                                        ) {
                                            if (daemonState == DaemonState.RUNNING) {
                                                Box(
                                                    modifier = Modifier
                                                        .size((6 * radarPulseScale).dp)
                                                        .clip(CircleShape)
                                                        .background(dotColor.copy(alpha = radarPulseAlpha))
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Active Project Pill
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = colors.surfaceElevated,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                                            modifier = Modifier.clickable {
                                                coroutineScope.launch { drawerState.open() }
                                            }
                                        ) {
                                            Text(
                                                text = "[ ${File(currentWorkspace).name.ifBlank { "GLYPH" }.uppercase()} ]",
                                                color = colors.accentPrimary,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
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
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                actions = {
                                    Surface(
                                        shape = CircleShape,
                                        color = colors.accentGlow,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentPrimary),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { activeScreen = "auth" }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = authState.avatarLetter,
                                                color = colors.accentPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = {
                                            chatService.newSession()
                                            activeTab = AntigravityTab.WORKBENCH
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "New Session",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { daemonManager.startOrConnectDaemon(currentWorkspace) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reconnect",
                                            tint = colors.textSecondary,
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
                        val density = LocalDensity.current
                        val imeBottom = WindowInsets.ime.getBottom(density)
                        val isKeyboardVisible = imeBottom > 0

                        AnimatedVisibility(
                            visible = !isKeyboardVisible,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            Surface(
                                color = colors.surfaceGlass,
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp,
                                    windowInsets = WindowInsets(0, 0, 0, 0),
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
                                                selectedIconColor = colors.accentPrimary,
                                                selectedTextColor = colors.accentPrimary,
                                                unselectedIconColor = colors.textMuted,
                                                unselectedTextColor = colors.textMuted,
                                                indicatorColor = colors.accentGlow
                                            )
                                        )
                                    }
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
                                    workspaceDir = currentWorkspace,
                                    initialSubTab = hubSubTab,
                                    onRunSkillInWorkbench = { prompt ->
                                        chatService.sendPrompt(prompt, currentWorkspace)
                                        activeTab = AntigravityTab.WORKBENCH
                                    }
                                )
                            }
                        }
                    }

                    if (showHubDialog) {
                        HubSetupDialog(
                            onDismissRequest = { showHubDialog = false },
                            onNavigateAuth = {
                                showHubDialog = false
                                activeScreen = "auth"
                            },
                            workspaceDir = currentWorkspace
                        )
                    }
                }
            }
        }
    }
}
