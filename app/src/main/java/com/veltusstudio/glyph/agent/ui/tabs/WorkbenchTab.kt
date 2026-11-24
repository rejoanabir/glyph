package com.veltusstudio.glyph.agent.ui.tabs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
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
import com.veltusstudio.glyph.agent.core.AgyChatService
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.daemon.DaemonState
import com.veltusstudio.glyph.agent.model.*
import com.veltusstudio.glyph.agent.ui.*
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

@Composable
fun WorkbenchTab(
    workspaceDir: String,
    chatService: AgyChatService,
    daemonManager: AgyDaemonManager,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val daemonState by daemonManager.daemonState.collectAsState()
    val messages by chatService.messages.collectAsState()
    val agentStatus by chatService.agentStatus.collectAsState()
    val liveThinking by chatService.currentThinking.collectAsState()
    val pendingApproval by chatService.pendingApproval.collectAsState()
    val pendingQuestion by chatService.pendingQuestion.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, liveThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
    ) {
        // Disconnected warning banner
        if (daemonState != DaemonState.RUNNING) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = colors.statusCrimson.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.statusCrimson.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { daemonManager.startOrConnectDaemon(workspaceDir) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HUB CONNECTION REQUIRED",
                            color = colors.statusCrimson,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Port 1237 offline. Tap to connect or start daemon.",
                            color = colors.textSecondary,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.statusCrimson,
                        modifier = Modifier.clickable { daemonManager.startOrConnectDaemon(workspaceDir) }
                    ) {
                        Text(
                            text = "CONNECT",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && agentStatus == AgentStatus.IDLE) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(max = 340.dp)
                    ) {
                        Text(
                            text = "[ GLYPH NATIVE WORKBENCH ]",
                            color = colors.accentPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Antigravity engine connected locally over port 1237. Select an action or write an instruction below.",
                            color = colors.textMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        val quickPrompts = listOf(
                            "Review repository architecture",
                            "Run build and test scripts",
                            "Check git status and pending changes",
                            "Search for performance bottlenecks"
                        )

                        quickPrompts.forEach { prompt ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.surfaceCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        if (daemonState != DaemonState.RUNNING) {
                                            daemonManager.startOrConnectDaemon(workspaceDir)
                                        }
                                        chatService.sendPrompt(prompt, workspaceDir)
                                    }
                            ) {
                                Text(
                                    text = "> $prompt",
                                    color = colors.textPrimary,
                                    fontSize = 10.5.sp,
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
                        .padding(horizontal = 12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        WorkbenchMessageItem(message = message)
                    }

                    if (agentStatus == AgentStatus.THINKING && !liveThinking.isNullOrBlank()) {
                        item {
                            ThoughtCard(thoughtText = liveThinking ?: "", isStreaming = true)
                        }
                    }
                }
            }
        }

        // Bottom interactive approvals & Input capsule
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceGlass)
                .border(androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .imePadding()
        ) {
            pendingApproval?.let { approval ->
                ToolApprovalBar(approval = approval)
                Spacer(modifier = Modifier.height(6.dp))
            }

            pendingQuestion?.let { question ->
                AskQuestionDialog(question = question)
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Quick Slash Action Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("/status", "/plan", "/git diff", "/fix", "/terminal").forEach { chip ->
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                        modifier = Modifier.clickable {
                            inputText = if (inputText.isBlank()) chip else "$inputText $chip"
                        }
                    ) {
                        Text(
                            text = chip,
                            color = colors.accentPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
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
                            color = colors.textMuted,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "GEMINI 2.5 FLASH",
                            color = colors.accentPrimary,
                            fontSize = 8.5.sp,
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
                                    color = colors.textMuted,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            singleLine = false,
                            maxLines = 4
                        )

                        val isWorking = agentStatus == AgentStatus.THINKING || agentStatus == AgentStatus.EXECUTING_TOOL

                        if (isWorking) {
                            IconButton(
                                onClick = { chatService.cancelCurrentTask() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.statusCrimson)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        val text = inputText
                                        inputText = ""
                                        if (daemonState != DaemonState.RUNNING) {
                                            daemonManager.startOrConnectDaemon(workspaceDir)
                                        }
                                        chatService.sendPrompt(text, workspaceDir)
                                    }
                                },
                                enabled = inputText.isNotBlank(),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (inputText.isNotBlank()) colors.accentPrimary else colors.surfaceElevated)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (inputText.isNotBlank()) colors.accentOnPrimary else colors.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkbenchMessageItem(message: AgentMessage) {
    val colors = LocalGlyphColors.current
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
                        color = colors.textMuted,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = colors.textPrimary,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "GLYPH [ HUB :1237 ]",
                        color = colors.accentPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.text,
                            color = colors.textPrimary,
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
