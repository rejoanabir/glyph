package com.veltusstudio.glyph.agent.ui.tabs

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
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
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class TerminalLogEntry(
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun TerminalTab(
    workspaceDir: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val coroutineScope = rememberCoroutineScope()
    var commandInput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<TerminalLogEntry>() }
    val listState = rememberLazyListState()

    fun executeCommand(cmd: String) {
        if (cmd.isBlank() || isRunning) return
        val trimmed = cmd.trim()
        commandInput = ""

        if (trimmed == "clear") {
            history.clear()
            return
        }

        isRunning = true
        coroutineScope.launch {
            val (output, code) = runShellCommand(trimmed, workspaceDir)
            history.add(TerminalLogEntry(command = trimmed, output = output, exitCode = code))
            isRunning = false
            if (history.isNotEmpty()) {
                listState.animateScrollToItem(history.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceTerminal)
    ) {
        // Quick Action Bar
        Surface(
            color = colors.surfaceGlass,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickCmds = listOf("git status", "ls -la", "pwd", "git log -n 3 --oneline")
                    quickCmds.forEach { cmd ->
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = colors.surfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                            modifier = Modifier.clickable { executeCommand(cmd) }
                        ) {
                            Text(
                                text = cmd,
                                color = colors.accentPrimary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { history.clear() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Terminal Output History
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "BASH CONSOLE READY",
                            color = colors.accentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Commands execute in $workspaceDir",
                            color = colors.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(history) { entry ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$ ",
                                    color = colors.statusEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = entry.command,
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (entry.exitCode == 0) "EXIT 0" else "EXIT ${entry.exitCode}",
                                    color = if (entry.exitCode == 0) colors.statusEmerald else colors.statusCrimson,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .background(
                                            if (entry.exitCode == 0) colors.statusEmerald.copy(alpha = 0.15f) else colors.statusCrimson.copy(alpha = 0.15f),
                                            RoundedCornerShape(2.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }

                            if (entry.output.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .background(colors.surfaceCard, RoundedCornerShape(3.dp))
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = entry.output,
                                        color = colors.textSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Command Input
        Surface(
            color = colors.surfaceGlass,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth().imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ ",
                    color = colors.statusEmerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                )

                TextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = {
                        Text(
                            text = if (isRunning) "Executing command..." else "Enter bash command...",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
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
                    singleLine = true,
                    enabled = !isRunning
                )

                IconButton(
                    onClick = { executeCommand(commandInput) },
                    enabled = commandInput.isNotBlank() && !isRunning,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (commandInput.isNotBlank() && !isRunning) colors.accentPrimary else colors.surfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Run",
                        tint = if (commandInput.isNotBlank() && !isRunning) colors.accentOnPrimary else colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

suspend fun runShellCommand(cmd: String, cwd: String): Pair<String, Int> = withContext(Dispatchers.IO) {
    try {
        val shell = if (File("/data/data/com.termux/files/usr/bin/bash").exists()) {
            "/data/data/com.termux/files/usr/bin/bash"
        } else if (File("/data/user/0/ai.antigem/files/usr/bin/bash").exists()) {
            "/data/user/0/ai.antigem/files/usr/bin/bash"
        } else {
            "/system/bin/sh"
        }

        val pb = ProcessBuilder(shell, "-c", cmd)
        pb.directory(File(cwd))
        pb.redirectErrorStream(true)
        val proc = pb.start()

        val output = BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            val sb = StringBuilder()
            var line: String? = reader.readLine()
            var count = 0
            while (line != null && count < 500) {
                sb.appendLine(line)
                line = reader.readLine()
                count++
            }
            sb.toString().trim()
        }

        val code = proc.waitFor()
        Pair(output, code)
    } catch (e: Exception) {
        Pair("Execution failed: ${e.message}", -1)
    }
}
