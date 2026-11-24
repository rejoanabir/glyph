package com.veltusstudio.glyph.agent.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
        containerColor = Color(0xFF08080A),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ANTIGRAVITY",
                                color = Color(0xFFF8FAFC),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.1.sp,
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
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        Text(
                            text = if (daemonState == DaemonState.RUNNING) "ONLINE · HUB :1237 · GEMINI 3.8 FLASH" else statusMsg.uppercase(),
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
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { chatService.newSession() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Session",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF08080A)
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF08080A))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Approval Bar
                AnimatedVisibility(visible = pendingApproval != null) {
                    pendingApproval?.let { approval ->
                        ToolApprovalBar(approval = approval)
                    }
                }

                // Interactive Question Dialog
                AnimatedVisibility(visible = pendingQuestion != null) {
                    pendingQuestion?.let { question ->
                        AskQuestionDialog(question = question)
                    }
                }

                // Input Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111217))
                        .border(1.dp, Color(0xFF1E2029), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "Assign task, shell instruction, or question...",
                                    color = Color(0xFF475569),
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
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

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val text = inputText
                                    inputText = ""
                                    chatService.sendPrompt(text, workspaceDir)
                                }
                            },
                            enabled = inputText.isNotBlank() && agentStatus != AgentStatus.THINKING,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (inputText.isNotBlank()) Color(0xFF6366F1) else Color(0xFF1E2029))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
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
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E2029),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3344)),
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Text(
                        text = message.text,
                        color = Color(0xFFF8FAFC),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF101116),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1C24)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.text,
                        color = Color(0xFFF1F5F9),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
