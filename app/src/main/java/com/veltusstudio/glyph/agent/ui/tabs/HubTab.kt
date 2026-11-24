package com.veltusstudio.glyph.agent.ui.tabs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.veltusstudio.glyph.agent.core.*
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.ui.theme.LocalGlyphColors
import kotlinx.coroutines.launch

enum class HubSubTab(val label: String) {
    RUNTIME("RUNTIME"),
    MCP("MCP SERVERS"),
    PLUGINS("PLUGINS"),
    SKILLS("SKILLS STUDIO")
}

data class SupportedAiModel(
    val id: String,
    val name: String,
    val provider: String,
    val desc: String
)

val DEFAULT_MODELS = listOf(
    SupportedAiModel(
        id = "gemini-2.5-flash",
        name = "Google Gemini 2.5 Flash",
        provider = "Google AI",
        desc = "High-speed reasoning model with native tool execution."
    ),
    SupportedAiModel(
        id = "gemini-2.5-pro",
        name = "Google Gemini 2.5 Pro",
        provider = "Google AI",
        desc = "Deep-thinking frontier model for complex code architecture."
    ),
    SupportedAiModel(
        id = "claude-3-7-sonnet",
        name = "Anthropic Claude 3.7 Sonnet",
        provider = "Anthropic",
        desc = "State-of-the-art hybrid reasoning model."
    ),
    SupportedAiModel(
        id = "claude-3-5-haiku",
        name = "Anthropic Claude 3.5 Haiku",
        provider = "Anthropic",
        desc = "Ultra-fast lightweight instruction execution."
    )
)

@Composable
fun HubTab(
    daemonManager: AgyDaemonManager,
    workspaceDir: String,
    initialSubTab: HubSubTab = HubSubTab.RUNTIME,
    onRunSkillInWorkbench: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlyphColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeSubTab by remember { mutableStateOf(initialSubTab) }

    val mcpPluginManager = remember { GlyphMcpPluginManager.getInstance(context) }
    val skillsManager = remember { GlyphSkillsManager.getInstance(context) }

    val mcpServers by mcpPluginManager.mcpServers.collectAsState()
    val plugins by mcpPluginManager.plugins.collectAsState()
    val skills by skillsManager.skills.collectAsState()

    var selectedMcpServerForTools by remember { mutableStateOf<McpServerInfo?>(null) }
    var selectedSkillForSpec by remember { mutableStateOf<Pair<GlyphSkillInfo, String>?>(null) }
    var showCreateSkillDialog by remember { mutableStateOf(false) }
    var showAddMcpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        skillsManager.refreshSkills()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgObsidian)
    ) {
        // Top Segmented SubTab Bar
        Surface(
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HubSubTab.values().forEach { subTab ->
                    val isSelected = activeSubTab == subTab
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) colors.surfaceElevated else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) colors.accentPrimary else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeSubTab = subTab }
                    ) {
                        Text(
                            text = subTab.label,
                            color = if (isSelected) colors.accentPrimary else colors.textMuted,
                            fontSize = 8.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // SubTab Content Crossfade
        Crossfade(targetState = activeSubTab, label = "hubSubTabCrossfade") { subTab ->
            when (subTab) {
                HubSubTab.RUNTIME -> RuntimeSection(
                    daemonManager = daemonManager,
                    workspaceDir = workspaceDir
                )
                HubSubTab.MCP -> McpSection(
                    servers = mcpServers,
                    onToggleServer = { mcpPluginManager.toggleMcpServer(it) },
                    onViewTools = { selectedMcpServerForTools = it },
                    onAddServer = { showAddMcpDialog = true }
                )
                HubSubTab.PLUGINS -> PluginsSection(
                    plugins = plugins,
                    onTogglePlugin = { mcpPluginManager.togglePlugin(it) },
                    onInstallPlugin = { mcpPluginManager.installPlugin(it) },
                    onUninstallPlugin = { mcpPluginManager.uninstallPlugin(it) }
                )
                HubSubTab.SKILLS -> SkillsSection(
                    skills = skills,
                    onViewSpec = { skill ->
                        coroutineScope.launch {
                            val md = skillsManager.readSkillMarkdown(skill)
                            selectedSkillForSpec = Pair(skill, md)
                        }
                    },
                    onRunInWorkbench = { skill ->
                        val prompt = "${skill.command} "
                        onRunSkillInWorkbench?.invoke(prompt)
                    },
                    onCreateSkill = { showCreateSkillDialog = true }
                )
            }
        }
    }

    // Modal: Tools List Dialog
    selectedMcpServerForTools?.let { server ->
        AlertDialog(
            onDismissRequest = { selectedMcpServerForTools = null },
            title = {
                Text(
                    text = "${server.name} Tools",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Exposed tool endpoints callable by Antigravity agent:",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    server.tools.forEach { tool ->
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = colors.surfaceTerminal,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "> $tool",
                                color = colors.accentPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMcpServerForTools = null }) {
                    Text("CLOSE", color = colors.accentPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.surfaceCard
        )
    }

    // Modal: Skill Spec Viewer
    selectedSkillForSpec?.let { (skill, mdText) ->
        AlertDialog(
            onDismissRequest = { selectedSkillForSpec = null },
            title = {
                Text(
                    text = "${skill.name} // SPEC",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceTerminal,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = mdText,
                            color = colors.textPrimary,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prompt = "${skill.command} "
                        selectedSkillForSpec = null
                        onRunSkillInWorkbench?.invoke(prompt)
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = colors.accentOnPrimary
                    )
                ) {
                    Text("RUN IN WORKBENCH", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSkillForSpec = null }) {
                    Text("DISMISS", color = colors.textMuted, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = colors.surfaceCard
        )
    }

    // Modal: Create Custom Skill Dialog
    if (showCreateSkillDialog) {
        var newSkillName by remember { mutableStateOf("") }
        var newSkillCategory by remember { mutableStateOf("GENERAL") }
        var newSkillDesc by remember { mutableStateOf("") }
        var newSkillInstructions by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateSkillDialog = false },
            title = {
                Text(
                    text = "CREATE CUSTOM SKILL",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Name:", color = colors.textSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = newSkillName,
                        onValueChange = { newSkillName = it },
                        placeholder = { Text("e.g. database-architect", color = colors.textMuted, fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Description:", color = colors.textSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = newSkillDesc,
                        onValueChange = { newSkillDesc = it },
                        placeholder = { Text("What does this skill do?", color = colors.textMuted, fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Instructions (SKILL.md):", color = colors.textSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = newSkillInstructions,
                        onValueChange = { newSkillInstructions = it },
                        placeholder = { Text("Guidelines, steps, and persona instructions...", color = colors.textMuted, fontSize = 10.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSkillName.isNotBlank() && newSkillInstructions.isNotBlank()) {
                            coroutineScope.launch {
                                skillsManager.createCustomSkill(
                                    name = newSkillName,
                                    category = newSkillCategory,
                                    description = newSkillDesc,
                                    instructions = newSkillInstructions
                                )
                                Toast.makeText(context, "Skill created in storage", Toast.LENGTH_SHORT).show()
                                showCreateSkillDialog = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("SAVE SKILL", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSkillDialog = false }) {
                    Text("CANCEL", color = colors.textMuted, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = colors.surfaceCard
        )
    }

    // Modal: Add Custom MCP Server
    if (showAddMcpDialog) {
        var mcpName by remember { mutableStateOf("") }
        var mcpCategory by remember { mutableStateOf("CUSTOM") }
        var mcpToolsInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMcpDialog = false },
            title = {
                Text(
                    text = "REGISTER MCP SERVER",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text("Server Name:", color = colors.textSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = mcpName,
                        onValueChange = { mcpName = it },
                        placeholder = { Text("e.g. database_mcp", color = colors.textMuted, fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Exposed Tools (comma-separated):", color = colors.textSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = mcpToolsInput,
                        onValueChange = { mcpToolsInput = it },
                        placeholder = { Text("query, mutate, schema", color = colors.textMuted, fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mcpName.isNotBlank()) {
                            val tools = mcpToolsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            mcpPluginManager.addCustomMcpServer(mcpName, mcpCategory, tools)
                            Toast.makeText(context, "MCP server registered", Toast.LENGTH_SHORT).show()
                            showAddMcpDialog = false
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("REGISTER", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMcpDialog = false }) {
                    Text("CANCEL", color = colors.textMuted, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = colors.surfaceCard
        )
    }
}

@Composable
private fun RuntimeSection(
    daemonManager: AgyDaemonManager,
    workspaceDir: String
) {
    val colors = LocalGlyphColors.current
    var selectedModelId by remember { mutableStateOf("gemini-2.5-flash") }

    var port1236Open by remember { mutableStateOf(false) }
    var port1237Open by remember { mutableStateOf(false) }

    fun refreshTelemetry() {
        port1236Open = daemonManager.isPortOpen(1236, 400)
        port1237Open = daemonManager.isPortOpen(1237, 400)
    }

    LaunchedEffect(Unit) {
        refreshTelemetry()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // Engine Runtime Card
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Engine",
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GLYPH ENGINE RUNTIME",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.08.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = {
                            refreshTelemetry()
                            daemonManager.startOrConnectDaemon(workspaceDir)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticChip(
                        label = "PORT 1236 [BRIDGE]",
                        isOpen = port1236Open,
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticChip(
                        label = "PORT 1237 [HUB gRPC]",
                        isOpen = port1237Open,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { daemonManager.startOrConnectDaemon(workspaceDir) },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = colors.accentOnPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "START / RESTART",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = { daemonManager.stopDaemon() },
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "STOP DAEMON",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Model Registry
        Text(
            text = "AI MODEL REGISTRY",
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        DEFAULT_MODELS.forEach { model ->
            val isSelected = model.id == selectedModelId
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isSelected) colors.surfaceElevated else colors.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) colors.accentPrimary else colors.borderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { selectedModelId = model.id }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = model.name,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = colors.accentGlow
                            ) {
                                Text(
                                    text = model.provider.uppercase(),
                                    color = colors.accentPrimary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = model.desc,
                            color = colors.textMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quota Telemetry Cards
        Text(
            text = "USAGE & QUOTA LIMITS",
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = colors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                QuotaRow(label = "5-Hour Burst Limit", usage = "12%", resetIn = "Resets in 3h 14m")
                Spacer(modifier = Modifier.height(8.dp))
                QuotaRow(label = "Weekly Generation Quota", usage = "28%", resetIn = "Resets Sunday")
                Spacer(modifier = Modifier.height(8.dp))
                QuotaRow(label = "Prompt Credits", usage = "UNLIMITED", resetIn = "Active Developer Tier")
            }
        }
    }
}

@Composable
private fun McpSection(
    servers: List<McpServerInfo>,
    onToggleServer: (String) -> Unit,
    onViewTools: (McpServerInfo) -> Unit,
    onAddServer: () -> Unit
) {
    val colors = LocalGlyphColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "MODEL CONTEXT PROTOCOL",
                    color = colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Extensible client/server protocol for tools, context, & prompt synthesis.",
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(onClick = onAddServer, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add MCP", tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        servers.forEach { server ->
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = colors.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (server.isEnabled) colors.borderActive else colors.borderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (server.isEnabled) colors.statusEmerald else colors.textMuted)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = server.name,
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = colors.surfaceElevated
                            ) {
                                Text(
                                    text = server.category,
                                    color = colors.accentPrimary,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Switch(
                            checked = server.isEnabled,
                            onCheckedChange = { onToggleServer(server.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentOnPrimary,
                                checkedTrackColor = colors.accentPrimary,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.surfaceTerminal
                            ),
                            modifier = Modifier.scale(0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = server.description,
                        color = colors.textSecondary,
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${server.tools.size} tools active",
                            color = colors.textMuted,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        TextButton(
                            onClick = { onViewTools(server) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "VIEW TOOLS >",
                                color = colors.accentPrimary,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
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
private fun PluginsSection(
    plugins: List<GlyphPluginInfo>,
    onTogglePlugin: (String) -> Unit,
    onInstallPlugin: (String) -> Unit,
    onUninstallPlugin: (String) -> Unit
) {
    val colors = LocalGlyphColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            text = "IDE EXTENSIONS & PLUGINS",
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Native developer plugins augmenting UI, diffing, syntax engines, and terminals.",
            color = colors.textMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        plugins.forEach { plugin ->
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = colors.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = plugin.name,
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "v${plugin.version}",
                                    color = colors.accentPrimary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "by ${plugin.author}",
                                color = colors.textMuted,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (plugin.isInstalled) {
                            Switch(
                                checked = plugin.isEnabled,
                                onCheckedChange = { onTogglePlugin(plugin.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.accentOnPrimary,
                                    checkedTrackColor = colors.accentPrimary
                                ),
                                modifier = Modifier.scale(0.7f)
                            )
                        } else {
                            Button(
                                onClick = { onInstallPlugin(plugin.id) },
                                shape = RoundedCornerShape(3.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("INSTALL", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = plugin.description,
                        color = colors.textSecondary,
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillsSection(
    skills: List<GlyphSkillInfo>,
    onViewSpec: (GlyphSkillInfo) -> Unit,
    onRunInWorkbench: (GlyphSkillInfo) -> Unit,
    onCreateSkill: () -> Unit
) {
    val colors = LocalGlyphColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "GLYPH SKILLS STUDIO",
                    color = colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Specialized agent capabilities, multi-agent pipelines, and slash shortcuts.",
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = onCreateSkill,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.accentOnPrimary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("NEW SKILL", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        skills.forEach { skill ->
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = colors.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = skill.name,
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = colors.accentGlow
                                ) {
                                    Text(
                                        text = skill.category,
                                        color = colors.accentPrimary,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = skill.command,
                                color = colors.accentPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = skill.description,
                        color = colors.textSecondary,
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onViewSpec(skill) },
                            shape = RoundedCornerShape(3.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderActive),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("VIEW SPEC (SKILL.MD)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onRunInWorkbench(skill) },
                            shape = RoundedCornerShape(3.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentPrimary,
                                contentColor = colors.accentOnPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("RUN IN WORKBENCH", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticChip(label: String, isOpen: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalGlyphColors.current
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = colors.surfaceTerminal,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOpen) colors.statusEmerald else colors.statusCrimson),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isOpen) colors.statusEmerald else colors.statusCrimson)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = label,
                    color = colors.textSecondary,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isOpen) "ONLINE" else "OFFLINE",
                    color = if (isOpen) colors.statusEmerald else colors.statusCrimson,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun QuotaRow(label: String, usage: String, resetIn: String) {
    val colors = LocalGlyphColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = resetIn,
                color = colors.textMuted,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = usage,
            color = colors.accentPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

fun Modifier.scale(scale: Float): Modifier = this.then(
    androidx.compose.ui.draw.scale(scale)
)
