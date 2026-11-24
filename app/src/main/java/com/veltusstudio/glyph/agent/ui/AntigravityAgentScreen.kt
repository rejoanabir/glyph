package com.veltusstudio.glyph.agent.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
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
import com.veltusstudio.glyph.agent.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityAgentScreen(
    workspaceDir: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val daemonManager = remember { AgyDaemonManager.getInstance(context) }
    val chatService = remember { AgyChatService.instance }

    val daemonState by daemonManager.daemonState.collectAsState()
    val statusMsg by daemonManager.statusMessage.collectAsState()

    val messages by chatService.messages.collectAsState()
    val agentStatus by chatService.agentStatus.collectAsState()
    val liveThinking by chatService.currentThinking.collectAsState()
    val pendingApproval by chatService.pendingApproval.collectAsState()
    val pendingQuestion by chatService.pendingQuestion.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    LaunchedEffect(Unit) {
        daemonManager.startOrConnectDaemon(workspaceDir)
    }

    LaunchedEffect(messages.size, liveThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF07080A),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ANTIGRAVITY // WORKBENCH",
                                color = Color(0xFFF8FAFC),
                                fontSize = 13.sp,
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
                        Text(
                            text = if (daemonState == DaemonState.RUNNING) "ONLINE · HUB :1237 · GEMINI 2.5 FLASH" else statusMsg.uppercase(),
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.05.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { chatService.newSession() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Session",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0D12)
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0D12))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
            ) {
                // Interactive approval or question overlays
                pendingApproval?.let { approval ->
                    ToolApprovalBar(approval = approval)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                pendingQuestion?.let { question ->
                    AskQuestionDialog(question = question)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Input Capsule Container
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF11141C),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2330)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "WORKSPACE: ${workspaceDir.substringAfterLast('/')}",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "GEMINI 2.5 FLASH",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        text = "Assign task, shell instruction, or question...",
                                        color = Color(0xFF475569),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color(0xFFF8FAFC),
                                    unfocusedTextColor = Color(0xFFF8FAFC)
                                ),
                                singleLine = false,
                                maxLines = 4
                            )

                            val isWorking = agentStatus == AgentStatus.THINKING || agentStatus == AgentStatus.EXECUTING_TOOL

                            if (isWorking) {
                                IconButton(
                                    onClick = { chatService.cancelCurrentTask() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE11D48))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (inputText.isNotBlank()) {
                                            val text = inputText
                                            inputText = ""
                                            chatService.sendPrompt(text, workspaceDir)
                                        }
                                    },
                                    enabled = inputText.isNotBlank(),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (inputText.isNotBlank()) Color(0xFF0284C7) else Color(0xFF1E2330))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (inputText.isNotBlank()) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (messages.isEmpty() && agentStatus == AgentStatus.IDLE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    Text(
                        text = "[ AGY NATIVE WORKSPACE ]",
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Google Antigravity engine connected locally over port 1237. Select an action or write an instruction below.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val quickPrompts = listOf(
                        "Review repository structure",
                        "Run build or test scripts",
                        "Check git status and pending changes",
                        "Analyze code architecture"
                    )

                    quickPrompts.forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0F1218),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2330)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    chatService.sendPrompt(prompt, workspaceDir)
                                }
                        ) {
                            Text(
                                text = "> $prompt",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageItem(message = message)
                }

                if (agentStatus == AgentStatus.THINKING && !liveThinking.isNullOrBlank()) {
                    item {
                        ThoughtCard(thoughtText = liveThinking ?: "", isStreaming = true)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: AgentMessage) {
    val isUser = message.role == Role.USER

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser && !message.thinking.isNullOrBlank()) {
            ThoughtCard(thoughtText = message.thinking, isStreaming = false)
        }

        if (!isUser && message.toolCalls.isNotEmpty()) {
            message.toolCalls.forEach { toolCall ->
                ToolCallCard(toolCall = toolCall)
            }
        }

        if (message.text.isNotBlank()) {
            if (isUser) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "YOU [ LOCAL ]",
                        color = Color(0xFF64748B),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF161922),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C3D)),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = Color(0xFFF8FAFC),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "ANTIGRAVITY [ HUB :1237 ]",
                        color = Color(0xFF38BDF8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0F1218),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2330)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.text,
                            color = Color(0xFFF1F5F9),
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
