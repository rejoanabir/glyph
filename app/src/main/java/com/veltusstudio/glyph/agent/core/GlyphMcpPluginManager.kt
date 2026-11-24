package com.veltusstudio.glyph.agent.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class McpServerInfo(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val isEnabled: Boolean,
    val status: String,
    val tools: List<String>
)

data class GlyphPluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val isEnabled: Boolean,
    val isInstalled: Boolean,
    val category: String
)

class GlyphMcpPluginManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "glyph_mcp_plugin_prefs"

        @Volatile
        private var instance: GlyphMcpPluginManager? = null

        fun getInstance(context: Context): GlyphMcpPluginManager {
            return instance ?: synchronized(this) {
                instance ?: GlyphMcpPluginManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val defaultMcpServers = listOf(
        McpServerInfo(
            id = "browser_mcp",
            name = "Browser Automation MCP",
            description = "High-precision Chromium browser navigation, tab handling, DOM inspection, and automated input.",
            category = "AUTOMATION",
            isEnabled = true,
            status = "ONLINE",
            tools = listOf(
                "browser_open_url",
                "browser_list_tabs",
                "browser_switch_tab",
                "browser_close_tab",
                "browser_read_page",
                "browser_computer",
                "browser_form_input",
                "browser_eval_js",
                "browser_get_console_logs",
                "browser_read_network"
            )
        ),
        McpServerInfo(
            id = "jarvis_mcp",
            name = "Jarvis Agent Supervisor",
            description = "Multi-agent autonomous coordination, speech synthesis, task hierarchy, mission updating, and project indexing.",
            category = "SUPERVISOR",
            isEnabled = true,
            status = "ONLINE",
            tools = listOf(
                "jarvis_speak",
                "jarvis_ask",
                "jarvis_task_start",
                "jarvis_task_list",
                "jarvis_mission_set",
                "jarvis_mission_update",
                "jarvis_team_create",
                "jarvis_memory_read",
                "jarvis_memory_write",
                "jarvis_ide_search_code"
            )
        ),
        McpServerInfo(
            id = "custom_plugin_developer_mcp",
            name = "Plugin Developer MCP",
            description = "Runtime creation, hot-reloading, packaging, and validation of webview extensions and custom IDE tools.",
            category = "DEVELOPER",
            isEnabled = true,
            status = "ONLINE",
            tools = listOf(
                "webview_extension_docs",
                "webview_extension_list",
                "webview_extension_create",
                "webview_extension_read_file",
                "webview_extension_write_files",
                "webview_extension_package",
                "webview_extension_install",
                "webview_extension_reload"
            )
        ),
        McpServerInfo(
            id = "filesystem_mcp",
            name = "Filesystem & Workspace MCP",
            description = "Direct POSIX filesystem access, tree exploration, recursive file search, and diff application.",
            category = "STORAGE",
            isEnabled = true,
            status = "ONLINE",
            tools = listOf(
                "fs_read_file",
                "fs_write_file",
                "fs_list_directory",
                "fs_file_search",
                "fs_directory_tree",
                "fs_apply_patch"
            )
        ),
        McpServerInfo(
            id = "terminal_mcp",
            name = "Terminal & Process Runner",
            description = "PTY terminal emulation, command execution in Termux/Android userspace, and job streaming.",
            category = "SYSTEM",
            isEnabled = true,
            status = "ONLINE",
            tools = listOf(
                "terminal_execute_command",
                "terminal_spawn_process",
                "terminal_send_input",
                "terminal_kill_process",
                "terminal_read_logs"
            )
        ),
        McpServerInfo(
            id = "github_mcp",
            name = "GitHub & Git SCM Engine",
            description = "Repository cloning, branch management, issue tracking, pull requests, and automated release deployment.",
            category = "SCM",
            isEnabled = false,
            status = "STANDBY",
            tools = listOf(
                "github_clone_repo",
                "github_create_repo",
                "github_list_repos",
                "github_create_pull_request",
                "github_get_issues",
                "github_status"
            )
        )
    )

    private val defaultPlugins = listOf(
        GlyphPluginInfo(
            id = "theme_noir_suite",
            name = "Antigravity Noir Theme Suite",
            version = "2.4.0",
            author = "Veltus Studio",
            description = "High-contrast cybernetic color palettes including Noir Violet, Carbon Lime, Graphite Amber, OLED Stealth, and Spec Cyan.",
            isEnabled = true,
            isInstalled = true,
            category = "THEME"
        ),
        GlyphPluginInfo(
            id = "compose_inspector",
            name = "Jetpack Compose Live Inspector",
            version = "1.8.2",
            author = "Google Android Dev",
            description = "Real-time hierarchy tree visualization, recomposition count tracker, and layout bounds rendering for Compose.",
            isEnabled = true,
            isInstalled = true,
            category = "DEVELOPER"
        ),
        GlyphPluginInfo(
            id = "git_graph_diff",
            name = "Git Visual Graph & Diff Styler",
            version = "3.1.0",
            author = "Glyph Core Team",
            description = "Interactive branch topological graph, unified side-by-side patch diffing, and unstaged change reviewer.",
            isEnabled = true,
            isInstalled = true,
            category = "SCM"
        ),
        GlyphPluginInfo(
            id = "katex_math",
            name = "KaTeX LaTeX Math Renderer",
            version = "0.16.8",
            author = "Khan Academy / KaTeX",
            description = "Fast TeX and LaTeX mathematical typesetting in agent thoughts, reasoning trajectories, and markdown cards.",
            isEnabled = true,
            isInstalled = true,
            category = "TYPOGRAPHY"
        ),
        GlyphPluginInfo(
            id = "tmux_multiplexer",
            name = "Terminal Session Multiplexer",
            version = "1.2.4",
            author = "Veltus Studio",
            description = "Background task persistence, multi-tab terminal multiplexing, and notification listeners for finished scripts.",
            isEnabled = false,
            isInstalled = true,
            category = "SYSTEM"
        )
    )

    private val _mcpServers = MutableStateFlow(loadMcpServers())
    val mcpServers: StateFlow<List<McpServerInfo>> = _mcpServers.asStateFlow()

    private val _plugins = MutableStateFlow(loadPlugins())
    val plugins: StateFlow<List<GlyphPluginInfo>> = _plugins.asStateFlow()

    private fun loadMcpServers(): List<McpServerInfo> {
        return defaultMcpServers.map { server ->
            val isEnabled = prefs.getBoolean("mcp_${server.id}_enabled", server.isEnabled)
            server.copy(isEnabled = isEnabled, status = if (isEnabled) "ONLINE" else "DISABLED")
        }
    }

    private fun loadPlugins(): List<GlyphPluginInfo> {
        return defaultPlugins.map { plugin ->
            val isEnabled = prefs.getBoolean("plugin_${plugin.id}_enabled", plugin.isEnabled)
            val isInstalled = prefs.getBoolean("plugin_${plugin.id}_installed", plugin.isInstalled)
            plugin.copy(isEnabled = isEnabled, isInstalled = isInstalled)
        }
    }

    fun toggleMcpServer(serverId: String) {
        val current = _mcpServers.value
        val updated = current.map { server ->
            if (server.id == serverId) {
                val nextState = !server.isEnabled
                prefs.edit().putBoolean("mcp_${server.id}_enabled", nextState).apply()
                server.copy(isEnabled = nextState, status = if (nextState) "ONLINE" else "DISABLED")
            } else {
                server
            }
        }
        _mcpServers.value = updated
    }

    fun addCustomMcpServer(name: String, category: String, tools: List<String>) {
        val id = name.lowercase().replace(" ", "_").replace("-", "_")
        val newServer = McpServerInfo(
            id = id,
            name = name,
            description = "User-defined Model Context Protocol server integrated into Glyph agent environment.",
            category = category.uppercase(),
            isEnabled = true,
            status = "ONLINE",
            tools = tools.ifEmpty { listOf("${id}_exec", "${id}_query") }
        )
        prefs.edit().putBoolean("mcp_${id}_enabled", true).apply()
        _mcpServers.value = _mcpServers.value + newServer
    }

    fun togglePlugin(pluginId: String) {
        val current = _plugins.value
        val updated = current.map { plugin ->
            if (plugin.id == pluginId) {
                val next = !plugin.isEnabled
                prefs.edit().putBoolean("plugin_${plugin.id}_enabled", next).apply()
                plugin.copy(isEnabled = next)
            } else {
                plugin
            }
        }
        _plugins.value = updated
    }

    fun uninstallPlugin(pluginId: String) {
        val current = _plugins.value
        val updated = current.map { plugin ->
            if (plugin.id == pluginId) {
                prefs.edit().putBoolean("plugin_${plugin.id}_installed", false).apply()
                plugin.copy(isInstalled = false, isEnabled = false)
            } else {
                plugin
            }
        }
        _plugins.value = updated
    }

    fun installPlugin(pluginId: String) {
        val current = _plugins.value
        val updated = current.map { plugin ->
            if (plugin.id == pluginId) {
                prefs.edit().putBoolean("plugin_${plugin.id}_installed", true).apply()
                plugin.copy(isInstalled = true, isEnabled = true)
            } else {
                plugin
            }
        }
        _plugins.value = updated
    }
}
