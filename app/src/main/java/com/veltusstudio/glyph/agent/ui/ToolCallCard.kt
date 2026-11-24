package com.veltusstudio.glyph.agent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
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
import com.veltusstudio.glyph.agent.model.AgentToolCall
import com.veltusstudio.glyph.agent.model.ToolStatus

@Composable
fun ToolCallCard(
    toolCall: AgentToolCall,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember {
        mutableStateOf(toolCall.status == ToolStatus.RUNNING || toolCall.status == ToolStatus.FAILED || !toolCall.output.isNullOrBlank())
    }

    val (badgeColor, badgeBg) = when (toolCall.status) {
        ToolStatus.COMPLETED -> Color(0xFF10B981) to Color(0x1F10B981)
        ToolStatus.RUNNING -> Color(0xFF38BDF8) to Color(0x1F38BDF8)
        ToolStatus.FAILED -> Color(0xFFF43F5E) to Color(0x1FF43F5E)
        ToolStatus.PENDING -> Color(0xFFF59E0B) to Color(0x1FF59E0B)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF1E2330), RoundedCornerShape(6.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F1218)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (toolCall.name) {
                        "run_command" -> Icons.Default.Terminal
                        "edit_file", "write_file" -> Icons.Default.Edit
                        "read_file", "view_file" -> Icons.Default.Description
                        "web_search", "search_web" -> Icons.Default.Search
                        else -> Icons.Default.Code
                    }
                    val label = when (toolCall.name) {
                        "run_command" -> "BASH"
                        "edit_file", "write_file" -> "FILE WRITE"
                        "read_file", "view_file" -> "FILE VIEW"
                        "web_search", "search_web" -> "WEB SEARCH"
                        else -> toolCall.name.uppercase()
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = "Tool",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = Color(0xFFF1F5F9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.06.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = badgeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = toolCall.status.name,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Command / Target display
            if (toolCall.displayCommand.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF07090D))
                        .border(1.dp, Color(0xFF171A24), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (toolCall.name == "run_command") "$ ${toolCall.displayCommand}" else toolCall.displayCommand,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Expandable Diff & Output
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (!toolCall.diffBefore.isNullOrBlank() || !toolCall.diffAfter.isNullOrBlank()) {
                        DiffViewer(diffBefore = toolCall.diffBefore ?: "", diffAfter = toolCall.diffAfter ?: "")
                    }

                    if (!toolCall.output.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CONSOLE OUTPUT",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.05.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF050608))
                                .border(1.dp, Color(0xFF151820), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = toolCall.output,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiffViewer(diffBefore: String, diffAfter: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF050608))
            .border(1.dp, Color(0xFF151820), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        if (diffBefore.isNotBlank()) {
            diffBefore.lines().forEach { line ->
                Text(
                    text = "- $line",
                    color = Color(0xFFF87171),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (diffAfter.isNotBlank()) {
            diffAfter.lines().forEach { line ->
                Text(
                    text = "+ $line",
                    color = Color(0xFF34D399),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
