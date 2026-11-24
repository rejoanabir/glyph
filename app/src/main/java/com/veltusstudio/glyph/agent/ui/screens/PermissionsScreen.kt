package com.veltusstudio.glyph.agent.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors

@Composable
fun PermissionsScreen(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val context = LocalContext.current
    var isStorageGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        )
    }
    var isNotifEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "STEP 04 // ANDROID SANDBOX BRIDGE",
            color = colors.statusAmber,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.08.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Storage & System Permissions",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Glyph requires direct filesystem access to read, diff, and execute scripts in your project directories.",
            color = colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Amber Warning Card
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = Color(0x1EF59E0B),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40F59E0B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Warning",
                    tint = colors.statusAmber,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ALL FILES ACCESS REQUIRED",
                        color = colors.statusAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Android 11+ restricts access to external storage by default. To browse and modify projects in /storage/emulated/0 and git worktrees, please grant Manage All Files permission.",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Storage comparison visualizer
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "FILESYSTEM SCOPE OVERVIEW",
                    color = colors.textMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Internal Sandbox (/files)",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "App private directory, always accessible",
                            color = colors.textMuted,
                            fontSize = 9.sp
                        )
                    }
                    Text(
                        text = "GRANTED",
                        color = colors.statusEmerald,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shared Storage (/storage/emulated/0)",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Project codebases, downloads, git repos",
                            color = colors.textMuted,
                            fontSize = 9.sp
                        )
                    }
                    Text(
                        text = if (isStorageGranted) "GRANTED" else "MANAGE ALL FILES",
                        color = if (isStorageGranted) colors.statusEmerald else colors.statusAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Grant Permission CTA
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        context.startActivity(intent)
                    }
                }
                isStorageGranted = true
            },
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isStorageGranted) colors.surfaceElevated else colors.accentPrimary,
                contentColor = if (isStorageGranted) colors.accentPrimary else colors.accentOnPrimary
            ),
            border = if (isStorageGranted) androidx.compose.foundation.BorderStroke(1.dp, colors.accentPrimary) else null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isStorageGranted) Icons.Default.Check else Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isStorageGranted) "STORAGE PERMISSION GRANTED" else "GRANT ALL FILES ACCESS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.08.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notification Toggle Card
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = colors.accentPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "KeepAlive Daemon Notifications",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Maintains background gRPC socket during inference",
                            color = colors.textMuted,
                            fontSize = 9.sp
                        )
                    }
                }

                Switch(
                    checked = isNotifEnabled,
                    onCheckedChange = { isNotifEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.accentPrimary,
                        checkedTrackColor = colors.accentGlow,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceTerminal
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Proceed CTA
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
                text = "PROCEED TO ENGINE SETUP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
