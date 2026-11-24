package com.veltusstudio.glyph.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
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
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

data class OnboardSlide(
    val stepBadge: String,
    val headline: String,
    val subtext: String,
    val techHighlights: List<Pair<String, String>>
)

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    var currentSlide by remember { mutableStateOf(0) }

    val slides = listOf(
        OnboardSlide(
            stepBadge = "STEP 02 // AGENT RUNTIME",
            headline = "Autonomous AI Engineering.",
            subtext = "Desktop-grade AI agent on Android. Native Antigravity daemon runs natively on device with zero cloud latency.",
            techHighlights = listOf(
                "Wire gRPC Protocol" to "HTTP/2 Prior Knowledge loopback on port :1237",
                "Trajectory Engine" to "Live streaming reasoning chains with step inspection",
                "Safety Approvals" to "Human-in-the-loop permission gates for sensitive tasks"
            )
        ),
        OnboardSlide(
            stepBadge = "STEP 02 // CODEBASE ENGINE",
            headline = "Full Project IDE & Terminal.",
            subtext = "Read, write, diff, and execute bash scripts directly on your local device storage and GitHub repositories.",
            techHighlights = listOf(
                "Workspace Explorer" to "Tree browser, code viewer with line numbers & editor",
                "Embedded Terminal" to "Direct Android/Termux bash execution with ANSI styling",
                "Unified Diffs" to "Git staging, colored line diffs, commit and push"
            )
        ),
        OnboardSlide(
            stepBadge = "STEP 02 // FRONTIER MODELS",
            headline = "Multi-Model Orchestration.",
            subtext = "Instant switching between frontier reasoning models tailored for complex coding and refactoring tasks.",
            techHighlights = listOf(
                "Google Gemini 2.5" to "Flash and Pro deep-thinking frontier reasoning",
                "Anthropic Claude 3.7" to "Sonnet hybrid architecture and coding capability",
                "Quota Telemetry" to "Active burst limit and weekly resource monitoring"
            )
        )
    )

    val slide = slides[currentSlide]

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
            .padding(20.dp)
    ) {
        // Top row: Segmented progress bars and Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0..2) {
                    val isActive = i <= currentSlide
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isActive) colors.accentPrimary else colors.borderSubtle)
                    )
                }
            }

            Text(
                text = "SKIP",
                color = colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable { onDone() }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = slide.stepBadge,
            color = colors.accentPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.08.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = slide.headline,
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = slide.subtext,
            color = colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Hero Card Diagram
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                slide.techHighlights.forEach { (title, desc) ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.accentGlow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (currentSlide) {
                                        0 -> Icons.Default.Memory
                                        1 -> Icons.Default.Terminal
                                        else -> Icons.Default.Check
                                    },
                                    contentDescription = null,
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = title,
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = desc,
                                    color = colors.textSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentSlide > 0) {
                OutlinedButton(
                    onClick = { currentSlide-- },
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "BACK", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.width(10.dp))
            }

            Button(
                onClick = {
                    if (currentSlide < slides.size - 1) {
                        currentSlide++
                    } else {
                        onDone()
                    }
                },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.accentOnPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (currentSlide < slides.size - 1) "NEXT SLIDE" else "GET STARTED",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(14.dp))
            }
        }
    }
}
