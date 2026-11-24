package com.veltusstudio.glyph.agent.ui.tabs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import java.io.File

@Composable
fun ExplorerTab(
    workspaceDir: String,
    onSendFileToAgent: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    var currentDir by remember { mutableStateOf(File(workspaceDir)) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val files = remember(currentDir, searchQuery) {
        try {
            val list = currentDir.listFiles()?.toList() ?: emptyList()
            val sorted = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            if (searchQuery.isBlank()) sorted else sorted.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
    ) {
        // Top breadcrumb bar
        Surface(
            color = colors.surfaceGlass,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentDir.absolutePath != workspaceDir && currentDir.parentFile != null) {
                    IconButton(
                        onClick = {
                            selectedFile = null
                            currentDir = currentDir.parentFile ?: currentDir
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Up",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = currentDir.absolutePath.replace(workspaceDir, "").ifBlank { "/" },
                    color = colors.accentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${files.size} ITEMS",
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Search Filter
        Surface(
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Filter files & directories...",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        // Main content: either file list or code viewer
        Box(modifier = Modifier.weight(1f)) {
            selectedFile?.let { file ->
                CodeViewer(
                    file = file,
                    onBack = { selectedFile = null },
                    onCopy = {
                        try {
                            clipboardManager.setText(AnnotatedString(file.readText()))
                            Toast.makeText(context, "File copied to clipboard", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSendToAgent = { onSendFileToAgent(file) }
                )
            } ?: run {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(files, key = { it.absolutePath }) { file ->
                        val isDir = file.isDirectory
                        val icon = when {
                            isDir -> Icons.Default.Folder
                            file.extension.lowercase() in listOf("kt", "java", "py", "rs", "cpp", "c", "js", "ts", "json", "xml", "gradle") -> Icons.Default.Code
                            else -> Icons.Default.Description
                        }

                        Surface(
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isDir) {
                                        currentDir = file
                                    } else {
                                        selectedFile = file
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = if (isDir) "Dir" else "File",
                                    tint = if (isDir) colors.accentPrimary else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        color = colors.textPrimary,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isDir) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (!isDir) {
                                        Text(
                                            text = "${formatFileSize(file.length())} · ${file.extension.uppercase().ifBlank { "TEXT" }}",
                                            color = colors.textMuted,
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace
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
}

@Composable
fun CodeViewer(
    file: File,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onSendToAgent: () -> Unit
) {
    val colors = LocalGlyphColors.current
    val fileContent = remember(file) {
        try {
            if (file.length() > 2_000_000) {
                "File too large to display directly (>2MB)"
            } else {
                file.readText()
            }
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    val lines = remember(fileContent) { fileContent.lines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceTerminal)
    ) {
        // Toolbar
        Surface(
            color = colors.surfaceGlass,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = file.name,
                        color = colors.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onSendToAgent,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.accentPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Ask Agent",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "ASK AGENT", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Code viewport with line numbers
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Line numbers
                Column(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .widthIn(min = 28.dp)
                ) {
                    lines.indices.forEach { idx ->
                        Text(
                            text = "${idx + 1}",
                            color = colors.textMuted,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Code text
                Column(modifier = Modifier.weight(1f)) {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            color = colors.textPrimary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}
