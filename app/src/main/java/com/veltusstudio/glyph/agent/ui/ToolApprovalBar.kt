package com.veltusstudio.glyph.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
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
import com.veltusstudio.glyph.agent.model.PendingToolApproval
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

@Composable
fun ToolApprovalBar(
    approval: PendingToolApproval,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    var rememberChoice by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, colors.statusAmber.copy(alpha = 0.5f), RoundedCornerShape(5.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1EF59E0B)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Security",
                    tint = colors.statusAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACTION REQUIRES AUTHORIZATION",
                    color = colors.statusAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.08.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${approval.actionSummary}:",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceTerminal)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = approval.commandOrDiff,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.accentPrimary,
                            uncheckedColor = colors.textMuted
                        )
                    )
                    Text(
                        text = "Remember for session",
                        color = colors.textSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row {
                    OutlinedButton(
                        onClick = { approval.onDecision(false, rememberChoice) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.statusCrimson
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.statusCrimson.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "DENY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { approval.onDecision(true, rememberChoice) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.statusEmerald
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "APPROVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
