package com.veltusstudio.glyph.agent.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Refresh
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
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var diffText by remember { mutableStateOf("") }
    val diffItems = remember { mutableStateListOf<FileDiffItem>() }

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
            .background(Color(0xFF07080A))
    ) {
        // Header
        Surface(
            color = Color(0xFF0B0D12),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF161A24)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Difference,
                        contentDescription = "Diff",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WORKSPACE MODIFICATIONS",
                        color = Color(0xFFF8FAFC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = { refreshDiffs() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Diff List
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (diffItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "WORKING DIRECTORY CLEAN",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No pending file diffs in $workspaceDir",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(diffItems) { item ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0C0E14),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2330)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.fileName,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFF050608))
                                        .border(1.dp, Color(0xFF151820), RoundedCornerShape(3.dp))
                                        .padding(6.dp)
                                ) {
                                    Column {
                                        item.lines.forEach { line ->
                                            val color = when {
                                                line.startsWith("+") -> Color(0xFF34D399)
                                                line.startsWith("-") -> Color(0xFFF87171)
                                                line.startsWith("@@") -> Color(0xFF38BDF8)
                                                else -> Color(0xFF94A3B8)
                                            }
                                            Text(
                                                text = line,
                                                color = color,
                                                fontSize = 10.sp,
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
    }
}
