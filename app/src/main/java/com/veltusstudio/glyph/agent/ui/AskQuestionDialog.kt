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

@Composable
fun AskQuestionDialog(
    question: PendingUserQuestion,
    modifier: Modifier = Modifier
) {
    val selectedOptions = remember { mutableStateListOf<String>() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3730A3), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF131326)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Query",
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CLARIFICATION REQUIRED",
                    color = Color(0xFFA5B4FC),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.08.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question.questionText,
                color = Color(0xFFF1F5F9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Options List
            question.options.forEach { option ->
                val isSelected = selectedOptions.contains(option)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF312E81) else Color(0xFF0D0D1A))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF6366F1) else Color(0xFF1E1E38),
                            RoundedCornerShape(6.dp)
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
                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { question.onAnswer(selectedOptions.toList()) },
                enabled = selectedOptions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5),
                    disabledContainerColor = Color(0xFF232338)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = "Submit Selection",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
