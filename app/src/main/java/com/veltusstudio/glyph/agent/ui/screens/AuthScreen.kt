package com.veltusstudio.glyph.agent.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.R
import com.veltusstudio.glyph.agent.core.AgyCsrfManager
import com.veltusstudio.glyph.agent.core.AuthMethod
import com.veltusstudio.glyph.agent.core.GlyphAuthManager
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

@Composable
fun AuthScreen(
    onProceed: () -> Unit,
    onBackToIde: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val authManager = remember { GlyphAuthManager.getInstance(context) }
    val authState by authManager.authState.collectAsState()
    val csrfManager = remember { AgyCsrfManager.instance }

    var apiKeyInput by remember { mutableStateOf(authState.apiKey) }
    var endpointInput by remember { mutableStateOf(authState.hubEndpoint) }
    val csrfToken = remember { csrfManager.currentFramedHeader }

    var showGoogleDialog by remember { mutableStateOf(false) }
    var customGoogleEmail by remember { mutableStateOf("") }

    if (showGoogleDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleDialog = false },
            title = {
                Text(
                    text = "LINK GOOGLE ACCOUNT",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your Google email address for inference allocation:",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customGoogleEmail,
                        onValueChange = { customGoogleEmail = it },
                        placeholder = {
                            Text(
                                text = "developer@gmail.com",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = customGoogleEmail.trim().ifBlank { "developer@glyph.ai" }
                        val name = email.substringBefore('@').replaceFirstChar { it.uppercase() }
                        authManager.loginWithGoogle(email = email, name = name)
                        Toast.makeText(context, "Google Account linked: $email", Toast.LENGTH_SHORT).show()
                        showGoogleDialog = false
                        onProceed()
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("SIGN IN", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleDialog = false }) {
                    Text("CANCEL", color = colors.textMuted, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = colors.surfaceCard
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Top Bar with Logo and Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                    modifier = Modifier.size(28.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.glyph_logo),
                        contentDescription = "Glyph",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STEP 03 // AUTH & API",
                    color = colors.accentPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.08.sp
                )
            }

            if (onBackToIde != null) {
                IconButton(onClick = onBackToIde, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to IDE",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Auth & Connection Setup",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Link your Google Account, configure direct frontier API keys, or connect to a self-hosted Antigravity daemon.",
            color = colors.textSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Active Account Card (if logged in)
        if (authState.isLoggedIn) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = colors.accentPrimary,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = authState.avatarLetter,
                                        color = colors.accentOnPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = authState.displayName,
                                    color = colors.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = authState.userEmail,
                                    color = colors.textMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = colors.accentGlow
                        ) {
                            Text(
                                text = authState.tier,
                                color = colors.accentPrimary,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { authManager.logout() },
                            shape = RoundedCornerShape(3.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.statusCrimson),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.statusCrimson),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SIGN OUT", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onProceed,
                            shape = RoundedCornerShape(3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CONTINUE", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Google Account Button
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showGoogleDialog = true
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
                            text = "AIzaSyD9... (optional if using local hub)",
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

                if (apiKeyInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            authManager.loginWithApiKey(apiKeyInput)
                            Toast.makeText(context, "API Key saved and active", Toast.LENGTH_SHORT).show()
                            onProceed()
                        },
                        shape = RoundedCornerShape(3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("SAVE & ACTIVATE KEY", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
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

                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        authManager.loginWithSelfHosted(endpointInput)
                        Toast.makeText(context, "Endpoint set: $endpointInput", Toast.LENGTH_SHORT).show()
                        onProceed()
                    },
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("CONNECT TO ENDPOINT", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
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

        Spacer(modifier = Modifier.height(24.dp))

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
