package com.veltusstudio.glyph.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
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
import com.veltusstudio.glyph.agent.model.PendingUserQuestion
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

@Composable
fun AskQuestionDialog(
    question: PendingUserQuestion,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val selectedOptions = remember { mutableStateListOf<String>() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, colors.accentPrimary, RoundedCornerShape(5.dp)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceElevated
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Question",
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CLARIFICATION REQUIRED",
                    color = colors.accentPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.08.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question.questionText,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Options List
            question.options.forEach { option ->
                val isSelected = selectedOptions.contains(option)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) colors.accentGlow else colors.surfaceTerminal)
                        .border(
                            1.dp,
                            if (isSelected) colors.accentPrimary else colors.borderSubtle,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            if (question.isMultiSelect) {
                                if (isSelected) selectedOptions.remove(option) else selectedOptions.add(option)
                            } else {
                                selectedOptions.clear()
                                selectedOptions.add(option)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) colors.accentPrimary else colors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { question.onAnswer(selectedOptions.toList()) },
                enabled = selectedOptions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    disabledContainerColor = colors.borderSubtle
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = "SUBMIT CHOICE",
                    color = colors.accentOnPrimary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
