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
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

@Composable
fun ToolCallCard(
    toolCall: AgentToolCall,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    var isExpanded by remember {
        mutableStateOf(toolCall.status == ToolStatus.RUNNING || toolCall.status == ToolStatus.FAILED || !toolCall.output.isNullOrBlank())
    }

    val (badgeColor, badgeBg) = when (toolCall.status) {
        ToolStatus.COMPLETED -> colors.statusEmerald to colors.statusEmerald.copy(alpha = 0.15f)
        ToolStatus.RUNNING -> colors.accentPrimary to colors.accentPrimary.copy(alpha = 0.15f)
        ToolStatus.FAILED -> colors.statusCrimson to colors.statusCrimson.copy(alpha = 0.15f)
        ToolStatus.PENDING -> colors.statusAmber to colors.statusAmber.copy(alpha = 0.15f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(5.dp)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceCard
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
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = colors.textPrimary,
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
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = colors.textMuted,
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
                        .background(colors.surfaceTerminal)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (toolCall.name == "run_command") "$ ${toolCall.displayCommand}" else toolCall.displayCommand,
                        color = colors.textPrimary,
                        fontSize = 10.5.sp,
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
                            color = colors.textMuted,
                            fontSize = 8.5.sp,
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
                                .background(colors.surfaceTerminal)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = toolCall.output,
                                color = colors.textSecondary,
                                fontSize = 10.5.sp,
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
    val colors = LocalGlyphColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surfaceTerminal)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        if (diffBefore.isNotBlank()) {
            diffBefore.lines().forEach { line ->
                Text(
                    text = "- $line",
                    color = colors.statusCrimson,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (diffAfter.isNotBlank()) {
            diffAfter.lines().forEach { line ->
                Text(
                    text = "+ $line",
                    color = colors.statusEmerald,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
