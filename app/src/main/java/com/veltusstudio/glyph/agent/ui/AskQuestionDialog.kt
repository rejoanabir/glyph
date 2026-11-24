package com.veltusstudio.glyph.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            .padding(12.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF14142B)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Help,
                    contentDescription = "Question",
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Agent Question",
                    color = Color(0xFF818CF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = question.questionText,
                color = Color(0xFFF8FAFC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Options List
            question.options.forEach { option ->
                val isSelected = selectedOptions.contains(option)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (question.isMultiSelect) {
                                if (isSelected) selectedOptions.remove(option) else selectedOptions.add(option)
                            } else {
                                selectedOptions.clear()
                                selectedOptions.add(option)
                            }
                        },
                    color = if (isSelected) Color(0xFF4F46E5) else Color(0x336366F1),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D818CF8))
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else Color(0xFFE0E7FF),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { question.onAnswer(selectedOptions.toList()) },
                enabled = selectedOptions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Submit Answer", color = Color.White)
            }
        }
    }
}
