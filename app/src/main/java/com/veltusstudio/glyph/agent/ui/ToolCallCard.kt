package com.veltusstudio.glyph.agent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.agent.model.AgentToolCall
import com.veltusstudio.glyph.agent.model.ToolStatus

@Composable
fun ToolCallCard(
    toolCall: AgentToolCall,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(toolCall.status == ToolStatus.RUNNING || toolCall.status == ToolStatus.FAILED) }

    val statusColor = when (toolCall.status) {
        ToolStatus.COMPLETED -> Color(0xFF10B981) // Green
        ToolStatus.RUNNING -> Color(0xFF3B82F6) // Blue
        ToolStatus.FAILED -> Color(0xFFEF4444) // Red
        ToolStatus.PENDING -> Color(0xFFF59E0B) // Amber
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xCC141417)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (toolCall.name == "run_command") Icons.Default.Terminal else Icons.Default.Code,
                        contentDescription = "Tool",
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = toolCall.name,
                        color = Color(0xFFF8FAFC),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = null
                ) {
                    Text(
                        text = toolCall.status.name,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Command preview
            if (toolCall.displayCommand.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xE60B0B0D)
                ) {
                    Text(
                        text = if (toolCall.name == "run_command") "$ ${toolCall.displayCommand}" else toolCall.displayCommand,
                        color = Color(0xFFE4E4E7),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Expandable Output or Diff
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Diff View if present
                    if (!toolCall.diffBefore.isNullOrBlank() || !toolCall.diffAfter.isNullOrBlank()) {
                        DiffViewer(diffBefore = toolCall.diffBefore ?: "", diffAfter = toolCall.diffAfter ?: "")
                    }

                    // Terminal Output if present
                    if (!toolCall.output.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Terminal Output:",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF050507)
                        ) {
                            Text(
                                text = toolCall.output,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
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
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF09090C))
            .padding(6.dp)
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
                    color = Color(0xFF4ADE80),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
