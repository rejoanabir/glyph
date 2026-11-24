package com.veltusstudio.glyph.agent.core

import android.util.Log
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * High-level Chat Service managing Antigravity agent interactions, streaming trajectories,
 * tool approvals, and question dialogs.
 */
class AgyChatService private constructor() {

    companion object {
        private const val TAG = "AgyChatService"
        val instance: AgyChatService by lazy { AgyChatService() }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

    private val _agentStatus = MutableStateFlow(AgentStatus.IDLE)
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val _currentThinking = MutableStateFlow<String?>(null)
    val currentThinking: StateFlow<String?> = _currentThinking.asStateFlow()

    private val _pendingApproval = MutableStateFlow<PendingToolApproval?>(null)
    val pendingApproval: StateFlow<PendingToolApproval?> = _pendingApproval.asStateFlow()

    private val _pendingQuestion = MutableStateFlow<PendingUserQuestion?>(null)
    val pendingQuestion: StateFlow<PendingUserQuestion?> = _pendingQuestion.asStateFlow()

    private var activeConversationId: String = UUID.randomUUID().toString()
    private var streamingJob: Job? = null

    fun newSession() {
        activeConversationId = UUID.randomUUID().toString()
        _messages.value = emptyList()
        _currentThinking.value = null
        _pendingApproval.value = null
        _pendingQuestion.value = null
        _agentStatus.value = AgentStatus.IDLE
    }

    /**
     * Sends a user prompt to the Antigravity agent.
     */
    fun sendPrompt(promptText: String, workspaceDir: String) {
        if (promptText.isBlank()) return

        val userMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            text = promptText
        )
        _messages.value = _messages.value + userMsg
        _agentStatus.value = AgentStatus.THINKING
        _currentThinking.value = "Analyzing workspace and planning..."

        streamingJob?.cancel()
        streamingJob = scope.launch {
            try {
                // Post prompt to the local IDE bridge
                val url = "${AgyDaemonManager.DEFAULT_BRIDGE_URL}/api/instances/default/prompt"
                val json = JSONObject().apply {
                    put("conversation_id", activeConversationId)
                    put("prompt", promptText)
                    put("workspace_dir", workspaceDir)
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val client = AgyOkHttpClient.client
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Bridge prompt dispatch response: ${response.code}")
                    }
                }

                // Poll / stream trajectory updates from the local instance
                startTrajectoryPolling()

            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching prompt", e)
                _agentStatus.value = AgentStatus.ERROR
                _currentThinking.value = null
                _messages.value = _messages.value + AgentMessage(
                    id = UUID.randomUUID().toString(),
                    role = Role.ASSISTANT,
                    text = "⚠️ Failed to reach Antigravity daemon: ${e.localizedMessage}"
                )
            }
        }
    }

    private suspend fun startTrajectoryPolling() = withContext(Dispatchers.IO) {
        val client = AgyOkHttpClient.client
        var iterations = 0
        var isThinking = true

        while (isActive && iterations < 120 && isThinking) {
            delay(800)
            iterations++

            try {
                val pollUrl = "${AgyDaemonManager.DEFAULT_BRIDGE_URL}/api/instances/default/trajectory?conversation_id=$activeConversationId"
                val req = Request.Builder().url(pollUrl).get().build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: return@use
                        val parsed = TrajectoryParser.parseTrajectoryJson(bodyStr)
                        if (parsed.isNotEmpty()) {
                            // Update UI messages
                            val currentList = _messages.value.filter { it.role == Role.USER }.toMutableList()
                            currentList.addAll(parsed)
                            _messages.value = currentList

                            val lastAssistant = parsed.lastOrNull { it.role == Role.ASSISTANT }
                            if (lastAssistant != null) {
                                _currentThinking.value = lastAssistant.thinking

                                // Check for pending tool calls needing approval
                                val pendingTool = lastAssistant.toolCalls.firstOrNull { it.status == ToolStatus.PENDING }
                                if (pendingTool != null) {
                                    _agentStatus.value = AgentStatus.AWAITING_APPROVAL
                                    _pendingApproval.value = PendingToolApproval(
                                        toolCallId = pendingTool.id,
                                        toolName = pendingTool.name,
                                        actionSummary = "Execute ${pendingTool.name}",
                                        commandOrDiff = pendingTool.displayCommand,
                                        onDecision = { approved, remember ->
                                            submitToolApproval(pendingTool.id, approved)
                                        }
                                    )
                                } else {
                                    _pendingApproval.value = null
                                    if (lastAssistant.text.isNotBlank()) {
                                        _agentStatus.value = AgentStatus.IDLE
                                        _currentThinking.value = null
                                        isThinking = false
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Trajectory poll: ${e.message}")
            }
        }

        if (isThinking) {
            _agentStatus.value = AgentStatus.IDLE
            _currentThinking.value = null
        }
    }

    fun submitToolApproval(toolCallId: String, approved: Boolean) {
        _pendingApproval.value = null
        _agentStatus.value = AgentStatus.EXECUTING_TOOL
        scope.launch {
            try {
                val url = "${AgyDaemonManager.DEFAULT_BRIDGE_URL}/api/instances/default/approval"
                val json = JSONObject().apply {
                    put("tool_call_id", toolCallId)
                    put("approved", approved)
                    put("conversation_id", activeConversationId)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(url).post(body).build()
                AgyOkHttpClient.client.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Approval submit error", e)
            }
        }
    }

    fun submitQuestionAnswer(selectedAnswers: List<String>) {
        _pendingQuestion.value = null
        scope.launch {
            try {
                val url = "${AgyDaemonManager.DEFAULT_BRIDGE_URL}/api/instances/default/answer"
                val json = JSONObject().apply {
                    put("answers", JSONArray(selectedAnswers))
                    put("conversation_id", activeConversationId)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(url).post(body).build()
                AgyOkHttpClient.client.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Answer submit error", e)
            }
        }
    }
}
