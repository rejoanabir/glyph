package com.veltusstudio.glyph.agent.ui.tabs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
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
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileDiffItem(
    val fileName: String,
    val lines: List<String>
)

@Composable
fun DiffsTab(
    workspaceDir: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var diffText by remember { mutableStateOf("") }
    val diffItems = remember { mutableStateListOf<FileDiffItem>() }
    var commitMessage by remember { mutableStateOf("") }
    var isCommitting by remember { mutableStateOf(false) }

    fun refreshDiffs() {
        isLoading = true
        coroutineScope.launch {
            val (output, _) = runShellCommand("git diff", workspaceDir)
            diffText = output
            diffItems.clear()

            if (output.isNotBlank()) {
                val chunks = output.split("diff --git ")
                for (chunk in chunks) {
                    if (chunk.isBlank()) continue
                    val header = chunk.lines().firstOrNull()?.substringAfter("b/") ?: "modified file"
                    diffItems.add(FileDiffItem(fileName = header, lines = chunk.lines()))
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(workspaceDir) {
        refreshDiffs()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
    ) {
        // Header
        Surface(
            color = colors.surfaceGlass,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Difference,
                        contentDescription = "Diff",
                        tint = colors.accentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WORKSPACE MODIFICATIONS",
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive)
                    ) {
                        Text(
                            text = "main",
                            color = colors.accentPrimary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { refreshDiffs() },
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
        }

        // Diff List
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = colors.accentPrimary,
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (diffItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.statusEmerald.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = colors.statusEmerald, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "WORKING DIRECTORY CLEAN",
                            color = colors.statusEmerald,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "No pending file diffs in $workspaceDir",
                            color = colors.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(diffItems) { item ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.surfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.fileName,
                                    color = colors.accentPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(colors.surfaceTerminal)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                                        .padding(6.dp)
                                ) {
                                    Column {
                                        item.lines.forEach { line ->
                                            val color = when {
                                                line.startsWith("+") -> colors.statusEmerald
                                                line.startsWith("-") -> colors.statusCrimson
                                                line.startsWith("@@") -> colors.accentPrimary
                                                else -> colors.textSecondary
                                            }
                                            Text(
                                                text = line,
                                                color = color,
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Git Commit & Push Capsule
        Surface(
            color = colors.surfaceGlass,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = {
                        Text(
                            text = "Commit message (e.g. feat: update engine telemetry)...",
                            color = colors.textMuted,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (commitMessage.isNotBlank()) {
                                isCommitting = true
                                coroutineScope.launch {
                                    val (out, code) = runShellCommand("git commit -am \"${commitMessage.replace("\"", "\\\"")}\"", workspaceDir)
                                    isCommitting = false
                                    Toast.makeText(context, if (code == 0) "Committed!" else "Git: $out", Toast.LENGTH_SHORT).show()
                                    commitMessage = ""
                                    refreshDiffs()
                                }
                            }
                        },
                        enabled = commitMessage.isNotBlank() && !isCommitting,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = colors.accentOnPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "COMMIT CHANGES", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val (out, code) = runShellCommand("git push", workspaceDir)
                                Toast.makeText(context, if (code == 0) "Pushed to origin!" else "Push: $out", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "PUSH ORIGIN", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
