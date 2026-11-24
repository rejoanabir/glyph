package com.veltusstudio.glyph.agent.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.R
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import kotlinx.coroutines.delay

@Composable
fun BootScreen(
    onBootComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    var traceProgress by remember { mutableStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "bootPulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    val traceLogs = listOf(
        "INITIALIZING HYPERVISOR SUBSYSTEM...",
        "MOUNTING ANDROID EXT4 RUNTIME SANDBOX...",
        "PROBING PORTS :1236 (BRIDGE) & :1237 (HUB)...",
        "ESTABLISHING WIRE gRPC HTTP/2 CLIENT...",
        "GLYPH MOBILE IDE SUBSYSTEM ONLINE"
    )

    LaunchedEffect(Unit) {
        for (i in 0 until traceLogs.size) {
            delay(350)
            traceProgress = i + 1
        }
        delay(400)
        onBootComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Original Brushed Silver Glyph Logo with Ambient Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(110.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size((92 * glowScale).dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accentGlow)
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.borderActive),
                    modifier = Modifier.size(80.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.glyph_logo),
                        contentDescription = "Glyph Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "GLYPH // IDE",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.12.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "MOBILE AI DEVELOPER ENVIRONMENT",
                color = colors.accentPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Cybernetic terminal trace
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.surfaceTerminal,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BOOT TRACE",
                            color = colors.textMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.statusEmerald)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PORT :1237",
                                color = colors.statusEmerald,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    traceLogs.take(traceProgress).forEach { log ->
                        Text(
                            text = "> $log",
                            color = if (log.contains("ONLINE")) colors.statusEmerald else colors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onBootComplete,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.accentOnPrimary
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ENTER IDE WORKSPACE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Bottom Architecture Footer
        Text(
            text = "v2.1 · ARM64-v8a · WIRE gRPC HTTP/2 CLIENT",
            color = colors.textMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
