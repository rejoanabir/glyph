package com.veltusstudio.glyph.agent.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.agent.core.AgyCsrfManager
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

@Composable
fun AuthScreen(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val csrfManager = remember { AgyCsrfManager.instance }

    var apiKeyInput by remember { mutableStateOf("") }
    var endpointInput by remember { mutableStateOf("127.0.0.1:1237") }
    var csrfToken by remember { mutableStateOf(csrfManager.currentFramedHeader) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "STEP 03 // API & LOOPBACK",
            color = colors.accentPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.08.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Auth & Connection Setup",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Link your Google Account or specify direct frontier API credentials for Antigravity engine synthesis.",
            color = colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Google Account Button
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    Toast.makeText(context, "Google Account linked for inference", Toast.LENGTH_SHORT).show()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = colors.accentGlow,
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "G",
                            color = colors.accentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Continue with Google Account",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.borderSubtle))
            Text(
                text = "OR DIRECT CREDENTIALS",
                color = colors.textMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.borderSubtle))
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Card 1: API Key
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "DIRECT API KEY (GEMINI / ANTHROPIC)",
                    color = colors.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = {
                        Text(
                            text = "AIzaSyD9... (optional if using local daemon)",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Self-hosted Hub Endpoint
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SELF-HOSTED HUB ENDPOINT (IP:PORT)",
                    color = colors.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = { endpointInput = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accentPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: CSRF Handshake Token
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CSRF HANDSHAKE TOKEN",
                        color = colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "AUTO-GENERATED",
                        color = colors.statusEmerald,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceTerminal,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = csrfToken,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(csrfToken))
                            Toast.makeText(context, "CSRF token copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = colors.accentPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "COPY", color = colors.accentPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Establish Handshake CTA
        Button(
            onClick = onProceed,
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentPrimary,
                contentColor = colors.accentOnPrimary
            ),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ESTABLISH HANDSHAKE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
