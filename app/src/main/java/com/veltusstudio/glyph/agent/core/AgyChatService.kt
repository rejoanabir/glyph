package com.veltusstudio.glyph.agent.core

import android.util.Log
import com.veltusstudio.glyph.agent.daemon.AgyDaemonManager
import com.veltusstudio.glyph.agent.daemon.DaemonState
import com.veltusstudio.glyph.agent.model.AgentMessage
import com.veltusstudio.glyph.agent.model.AgentStatus
import com.veltusstudio.glyph.agent.model.AgentToolCall
import com.veltusstudio.glyph.agent.model.PendingToolApproval
import com.veltusstudio.glyph.agent.model.PendingUserQuestion
import com.veltusstudio.glyph.agent.model.Role
import com.veltusstudio.glyph.agent.model.ToolStatus
import exa.language_server_pb.AgentStateUpdate
import exa.language_server_pb.AskQuestionEntry
import exa.language_server_pb.AskQuestionInteraction
import exa.language_server_pb.AutoCommandConfig
import exa.language_server_pb.BuiltinAgentConfig
import exa.language_server_pb.CancelCascadeInvocationRequest
import exa.language_server_pb.CascadeCommandsAutoExecution
import exa.language_server_pb.CascadeConfig
import exa.language_server_pb.CascadeExecutorConfig
import exa.language_server_pb.CascadePlannerConfig
import exa.language_server_pb.CascadeRunStatus
import exa.language_server_pb.CascadeToolConfig
import exa.language_server_pb.CascadeUserInteraction
import exa.language_server_pb.ClientTrajectoryVerbosity
import exa.language_server_pb.ConversationHistoryConfig
import exa.language_server_pb.CortexStepStatus
import exa.language_server_pb.CortexStepType
import exa.language_server_pb.CortexTrajectorySource
import exa.language_server_pb.CustomAgentSpec
import exa.language_server_pb.DefaultAgentConfig
import exa.language_server_pb.HandleCascadeUserInteractionRequest
import exa.language_server_pb.MessageDeliveryStrategy
import exa.language_server_pb.Model
import exa.language_server_pb.ModelOrAlias
import exa.language_server_pb.NotifyUserConfig
import exa.language_server_pb.PermissionInteraction
import exa.language_server_pb.PermissionScope
import exa.language_server_pb.RunCommandToolConfig
import exa.language_server_pb.SendUserCascadeMessageRequest
import exa.language_server_pb.StartCascadeRequest
import exa.language_server_pb.StreamAgentStateUpdatesRequest
import exa.language_server_pb.TextOrScopeItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * High-level Chat Service managing Antigravity agent interactions, streaming trajectories,
 * tool approvals, and question dialogs via typed Wire gRPC.
 */
class AgyChatService private constructor() {

    companion object {
        private const val TAG = "AgyChatService"
        val instance: AgyChatService by lazy { AgyChatService() }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var authManager: GlyphAuthManager? = null
    private var daemonManager: AgyDaemonManager? = null

    fun initialize(context: android.content.Context) {
        authManager = GlyphAuthManager.getInstance(context)
        daemonManager = AgyDaemonManager.getInstance(context)
    }

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
    private var isConversationStartedOnDaemon: Boolean = false
    private var streamingJob: Job? = null

    fun newSession() {
        cancelCurrentTask()
        activeConversationId = UUID.randomUUID().toString()
        isConversationStartedOnDaemon = false
        _messages.value = emptyList()
        _currentThinking.value = null
        _pendingApproval.value = null
        _pendingQuestion.value = null
        _agentStatus.value = AgentStatus.IDLE
    }

    fun resetConversation() = newSession()

    /**
     * Cancels active generator run or tool execution.
     */
    fun cancelCurrentTask() {
        streamingJob?.cancel()
        streamingJob = null
        if (isConversationStartedOnDaemon) {
            scope.launch {
                try {
                    AgyLanguageServiceHolder.service.CancelCascadeInvocation().executeSafely(
                        CancelCascadeInvocationRequest(cascade_id = activeConversationId)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Cancel invocation: ${e.message}")
                }
            }
        }
        _agentStatus.value = AgentStatus.IDLE
        _currentThinking.value = null
        _pendingApproval.value = null
    }

    /**
     * Sends a user prompt to the Antigravity agent or autonomous Gemini direct cloud.
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
        _currentThinking.value = "Analyzing workspace and planning execution..."

        val engineMode = authManager?.authState?.value?.engineMode ?: EngineMode.AUTO
        val hubRunning = daemonManager?.daemonState?.value == DaemonState.RUNNING ||
                (daemonManager?.isPortOpen(AgyDaemonManager.DEFAULT_HUB_PORT, 300) == true)

        streamingJob?.cancel()
        streamingJob = scope.launch {
            // Case 1: Pure Cloud Direct mode explicitly chosen
            if (engineMode == EngineMode.CLOUD_DIRECT) {
                runGeminiDirect(promptText, workspaceDir)
                return@launch
            }

            // Case 2: Auto mode, but Hub is not currently running
            if (engineMode == EngineMode.AUTO && !hubRunning) {
                Log.i(TAG, "Hub port 1237 is offline, routing prompt to Gemini Cloud Direct Engine")
                runGeminiDirect(promptText, workspaceDir)
                return@launch
            }

            // Case 3: Hub mode or Auto with Hub detected online -> attempt gRPC to Hub
            try {
                // Ensure Hub CSRF token is available
                AgyCsrfManager.instance.getEffectiveCsrfToken()

                // 1. Start cascade on daemon if first message
                if (!isConversationStartedOnDaemon) {
                    val normalizedUri = if (workspaceDir.startsWith("file://")) workspaceDir else "file://$workspaceDir"
                    val startReq = StartCascadeRequest(
                        cascade_id = activeConversationId,
                        source = CortexTrajectorySource.CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT,
                        requested_model = Model.MODEL_GOOGLE_GEMINI_2_5_FLASH,
                        workspace_uris = listOf(normalizedUri),
                        override_workspace_uris = listOf(normalizedUri)
                    )

                    var startRes = AgyLanguageServiceHolder.service.StartCascade().executeSafely(startReq)
                    if (startRes.isFailure) {
                        Log.w(TAG, "StartCascade initial attempt failed, re-fetching CSRF and retrying once...")
                        AgyCsrfManager.instance.clearTokens()
                        AgyCsrfManager.instance.refreshFromHubHtml()
                        AgyCsrfManager.instance.refreshFromBridge()
                        startRes = AgyLanguageServiceHolder.service.StartCascade().executeSafely(startReq)
                    }

                    if (startRes.isSuccess) {
                        isConversationStartedOnDaemon = true
                    } else {
                        val errMsg = startRes.exceptionOrNull()?.message ?: "Failed to initialize conversation on daemon"
                        Log.w(TAG, "StartCascade note: $errMsg (proceeding to send message)")
                        isConversationStartedOnDaemon = true
                    }
                }

                // 2. Send the message payload
                val sendReq = SendUserCascadeMessageRequest(
                    cascade_id = activeConversationId,
                    items = listOf(TextOrScopeItem(text = promptText)),
                    cascade_config = CascadeConfig(
                        planner_config = CascadePlannerConfig(
                            tool_config = CascadeToolConfig(
                                run_command = RunCommandToolConfig(
                                    auto_command_config = AutoCommandConfig(
                                        auto_execution_policy = CascadeCommandsAutoExecution.CASCADE_COMMANDS_AUTO_EXECUTION_EAGER
                                    )
                                ),
                                notify_user = NotifyUserConfig()
                            ),
                            requested_model = ModelOrAlias(model = Model.MODEL_GOOGLE_GEMINI_2_5_FLASH),
                            use_ai_credits = false,
                            supports_latex_rendering = true
                        ),
                        executor_config = CascadeExecutorConfig(use_core_direct = true),
                        conversation_history_config = ConversationHistoryConfig()
                    ),
                    custom_agent_spec = CustomAgentSpec(
                        builtin_agent = BuiltinAgentConfig(
                            default_agent = DefaultAgentConfig(
                                is_google = false,
                                is_interactive = true
                            )
                        )
                    ),
                    delivery_strategy = MessageDeliveryStrategy.MESSAGE_DELIVERY_STRATEGY_WHEN_IDLE
                )

                var sendRes = AgyLanguageServiceHolder.service.SendUserCascadeMessage().executeSafely(sendReq)
                if (sendRes.isFailure) {
                    Log.w(TAG, "SendUserCascadeMessage initial attempt failed, re-fetching CSRF and retrying once...")
                    AgyCsrfManager.instance.clearTokens()
                    AgyCsrfManager.instance.refreshFromHubHtml()
                    AgyCsrfManager.instance.refreshFromBridge()
                    sendRes = AgyLanguageServiceHolder.service.SendUserCascadeMessage().executeSafely(sendReq)
                }

                if (sendRes.isFailure) {
                    val err = sendRes.exceptionOrNull()?.message ?: "Daemon communication failed"
                    Log.w(TAG, "Hub RPC failed: $err")
                    AgyCsrfManager.instance.clearTokens()

                    if (engineMode == EngineMode.AUTO) {
                        Log.i(TAG, "Failing over to Gemini Direct Cloud Engine due to: $err")
                        runGeminiDirect(promptText, workspaceDir, "Antigravity Hub unreachable ($err)")
                    } else {
                        _agentStatus.value = AgentStatus.ERROR
                        _currentThinking.value = null
                        _messages.value = _messages.value + AgentMessage(
                            id = UUID.randomUUID().toString(),
                            role = Role.ASSISTANT,
                            text = "[Error] Failed to reach Antigravity daemon: $err\n\nRun in Termux: test_bridge -f -p 1236 --hub-port 1237\nOr switch Engine Mode to 'GEMINI CLOUD DIRECT' in Hub Setup."
                        )
                    }
                    return@launch
                }

                // 3. Stream trajectory updates
                val streamReq = StreamAgentStateUpdatesRequest(
                    conversation_id = activeConversationId,
                    subscriber_id = UUID.randomUUID().toString(),
                    trajectory_verbosity = ClientTrajectoryVerbosity.CLIENTTRAJECTORYVERBOSITY_CLIENT_TRAJECTORY_VERBOSITY_PROD_UI
                )

                AgyLanguageServiceHolder.service.StreamAgentStateUpdates().asFlowSafely(streamReq).collect { resp ->
                    val update = resp.update ?: return@collect
                    processAgentStateUpdate(update)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception during Hub communication: ${e.message}", e)
                AgyCsrfManager.instance.clearTokens()

                if (engineMode == EngineMode.AUTO) {
                    Log.i(TAG, "Failing over to Gemini Direct Cloud Engine after exception: ${e.message}")
                    runGeminiDirect(promptText, workspaceDir, "Hub connection reset (${e.localizedMessage ?: e.message})")
                } else {
                    _agentStatus.value = AgentStatus.ERROR
                    _currentThinking.value = null
                    _messages.value = _messages.value + AgentMessage(
                        id = UUID.randomUUID().toString(),
                        role = Role.ASSISTANT,
                        text = "[Error] Failed to reach Antigravity daemon: ${e.localizedMessage ?: e.message}\n\nRun in Termux: test_bridge -f -p 1236 --hub-port 1237\nOr switch Engine Mode to 'GEMINI CLOUD DIRECT' in Hub Setup."
                    )
                }
            }
        }
    }

    /**
     * Executes prompt via GeminiDirectService with live streaming.
     */
    private suspend fun runGeminiDirect(
        promptText: String,
        workspaceDir: String,
        fallbackNotice: String? = null
    ) {
        val apiKey = authManager?.authState?.value?.apiKey ?: ""
        val model = authManager?.authState?.value?.selectedModel ?: GeminiDirectService.DEFAULT_MODEL

        _agentStatus.value = AgentStatus.THINKING
        _currentThinking.value = if (fallbackNotice != null) {
            "Failing over to Gemini Cloud ($fallbackNotice)..."
        } else {
            "Autonomous Gemini Cloud Engine processing prompt..."
        }

        val assistantMsgId = UUID.randomUUID().toString()
        var fullAssistantText = ""

        val assistantMsg = AgentMessage(
            id = assistantMsgId,
            role = Role.ASSISTANT,
            text = fullAssistantText
        )
        _messages.value = _messages.value + assistantMsg

        val res = GeminiDirectService.instance.streamGenerate(
            prompt = promptText,
            workspaceDir = workspaceDir,
            modelName = model,
            apiKey = apiKey,
            onThought = { thought ->
                _currentThinking.value = thought
            },
            onChunk = { chunk ->
                fullAssistantText += chunk
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(text = fullAssistantText)
                    } else msg
                }
            }
        )

        _agentStatus.value = AgentStatus.IDLE
        _currentThinking.value = null

        if (res.isFailure) {
            val err = res.exceptionOrNull()?.message ?: "Unknown error"
            _agentStatus.value = AgentStatus.ERROR
            _messages.value = _messages.value.map { msg ->
                if (msg.id == assistantMsgId && fullAssistantText.isBlank()) {
                    msg.copy(text = "[Error] Gemini Cloud request failed: $err")
                } else msg
            }
        }
    }

    private fun processAgentStateUpdate(update: AgentStateUpdate) {
        val steps = update.main_trajectory_update?.steps_update?.steps ?: emptyList()
        if (steps.isEmpty()) return

        val turnTools = mutableListOf<AgentToolCall>()
        val turnThoughts = mutableListOf<String>()
        val turnTexts = mutableListOf<String>()
        var pendingApprovalFound: PendingToolApproval? = null
        var pendingQuestionFound: PendingUserQuestion? = null

        steps.forEachIndexed { index, step ->
            when (step.type) {
                CortexStepType.CORTEX_STEP_TYPE_PLANNER_RESPONSE -> {
                    step.planner_response?.thinking?.takeIf { it.isNotBlank() }?.let {
                        turnThoughts.add(it)
                    }
                    step.planner_response?.response?.takeIf { it.isNotBlank() }?.let {
                        turnTexts.add(it)
                    }
                }
                CortexStepType.CORTEX_STEP_TYPE_RUN_COMMAND -> {
                    val cmd = step.run_command?.command.orEmpty()
                    val stdout = step.run_command?.stdout.orEmpty()
                    val stderr = step.run_command?.stderr.orEmpty()
                    val outputText = (stdout + if (stderr.isNotBlank()) "\n$stderr" else "").trim().takeIf { it.isNotBlank() }
                    val status = when (step.status) {
                        CortexStepStatus.CORTEX_STEP_STATUS_DONE -> ToolStatus.COMPLETED
                        CortexStepStatus.CORTEX_STEP_STATUS_RUNNING -> ToolStatus.RUNNING
                        CortexStepStatus.CORTEX_STEP_STATUS_ERROR -> ToolStatus.FAILED
                        CortexStepStatus.CORTEX_STEP_STATUS_WAITING -> ToolStatus.PENDING
                        else -> ToolStatus.COMPLETED
                    }
                    val tc = AgentToolCall(
                        id = "run_command_$index",
                        name = "run_command",
                        displayCommand = cmd,
                        status = status,
                        output = outputText,
                        stepIndex = index
                    )
                    turnTools.add(tc)

                    if (step.status == CortexStepStatus.CORTEX_STEP_STATUS_WAITING) {
                        pendingApprovalFound = PendingToolApproval(
                            toolCallId = tc.id,
                            toolName = "Terminal Command",
                            actionSummary = "Execute bash command",
                            commandOrDiff = cmd,
                            stepIndex = index,
                            onDecision = { approved, _ ->
                                submitToolApproval(index, approved)
                            }
                        )
                    }
                }
                CortexStepType.CORTEX_STEP_TYPE_VIEW_FILE -> {
                    val path = step.view_file?.absolute_path_uri.orEmpty()
                    turnTools.add(
                        AgentToolCall(
                            id = "view_file_$index",
                            name = "read_file",
                            displayCommand = path,
                            status = ToolStatus.COMPLETED,
                            filePath = path,
                            stepIndex = index
                        )
                    )
                }
                CortexStepType.CORTEX_STEP_TYPE_SEARCH_WEB -> {
                    val q = step.search_web?.query.orEmpty()
                    turnTools.add(
                        AgentToolCall(
                            id = "search_web_$index",
                            name = "web_search",
                            displayCommand = q,
                            status = ToolStatus.COMPLETED,
                            stepIndex = index
                        )
                    )
                }
                else -> {
                    // Check write_to_file
                    step.write_to_file?.let { wtf ->
                        val path = wtf.target_file_uri
                        val status = when (step.status) {
                            CortexStepStatus.CORTEX_STEP_STATUS_DONE -> ToolStatus.COMPLETED
                            CortexStepStatus.CORTEX_STEP_STATUS_RUNNING -> ToolStatus.RUNNING
                            CortexStepStatus.CORTEX_STEP_STATUS_ERROR -> ToolStatus.FAILED
                            CortexStepStatus.CORTEX_STEP_STATUS_WAITING -> ToolStatus.PENDING
                            else -> ToolStatus.COMPLETED
                        }
                        val tc = AgentToolCall(
                            id = "write_file_$index",
                            name = "edit_file",
                            displayCommand = path,
                            status = status,
                            filePath = path,
                            stepIndex = index
                        )
                        turnTools.add(tc)

                        if (step.status == CortexStepStatus.CORTEX_STEP_STATUS_WAITING) {
                            pendingApprovalFound = PendingToolApproval(
                                toolCallId = tc.id,
                                toolName = "File Modification",
                                actionSummary = "Modify file",
                                commandOrDiff = path,
                                stepIndex = index,
                                onDecision = { approved, _ ->
                                    submitToolApproval(index, approved)
                                }
                            )
                        }
                    }

                    // Check ask_question steps
                    val qEntry = step.ask_question?.questions?.firstOrNull()
                    if (qEntry != null) {
                        val options = qEntry.options.map { it.text.ifBlank { it.id } }
                        pendingQuestionFound = PendingUserQuestion(
                            questionId = "q_$index",
                            questionText = qEntry.question,
                            options = options,
                            isMultiSelect = qEntry.is_multi_select,
                            stepIndex = index,
                            onAnswer = { selected ->
                                submitQuestionAnswer(index, qEntry, selected)
                            }
                        )
                    }
                }
            }
        }

        // Update active assistant message
        val finalThought = turnThoughts.joinToString("\n\n").takeIf { it.isNotBlank() }
        val finalText = turnTexts.joinToString("\n\n").takeIf { it.isNotBlank() } ?: ""

        val userMessages = _messages.value.filter { it.role == Role.USER }
        val assistantMsg = AgentMessage(
            id = "assistant_$activeConversationId",
            role = Role.ASSISTANT,
            text = finalText,
            thinking = finalThought,
            toolCalls = turnTools
        )

        _messages.value = userMessages + assistantMsg
        _currentThinking.value = finalThought

        _pendingApproval.value = pendingApprovalFound
        _pendingQuestion.value = pendingQuestionFound

        if (pendingApprovalFound != null) {
            _agentStatus.value = AgentStatus.AWAITING_APPROVAL
        } else if (pendingQuestionFound != null) {
            _agentStatus.value = AgentStatus.AWAITING_QUESTION
        } else if (update.fully_idle || update.status == CascadeRunStatus.CASCADE_RUN_STATUS_IDLE) {
            _agentStatus.value = AgentStatus.IDLE
            _currentThinking.value = null
        } else {
            _agentStatus.value = AgentStatus.THINKING
        }
    }

    fun submitToolApproval(stepIndex: Int, approved: Boolean) {
        _pendingApproval.value = null
        _agentStatus.value = AgentStatus.EXECUTING_TOOL
        scope.launch {
            try {
                val interaction = CascadeUserInteraction(
                    step_index = stepIndex,
                    permission = PermissionInteraction(
                        allow = approved,
                        scope = PermissionScope.PERMISSION_SCOPE_ONCE
                    )
                )
                val req = HandleCascadeUserInteractionRequest(
                    cascade_id = activeConversationId,
                    interaction = interaction
                )
                AgyLanguageServiceHolder.service.HandleCascadeUserInteraction().executeSafely(req)
            } catch (e: Exception) {
                Log.e(TAG, "Approval submit error", e)
            }
        }
    }

    fun submitQuestionAnswer(stepIndex: Int, qEntry: AskQuestionEntry, selectedAnswers: List<String>) {
        _pendingQuestion.value = null
        _agentStatus.value = AgentStatus.THINKING
        scope.launch {
            try {
                val selectedIds = qEntry.options.filter { it.text in selectedAnswers || it.id in selectedAnswers }.map { it.id }
                val answeredEntry = qEntry.copy(
                    selected_option_ids = if (selectedIds.isNotEmpty()) selectedIds else selectedAnswers
                )
                val interaction = CascadeUserInteraction(
                    step_index = stepIndex,
                    ask_question = AskQuestionInteraction(
                        responses = listOf(answeredEntry)
                    )
                )
                val req = HandleCascadeUserInteractionRequest(
                    cascade_id = activeConversationId,
                    interaction = interaction
                )
                AgyLanguageServiceHolder.service.HandleCascadeUserInteraction().executeSafely(req)
            } catch (e: Exception) {
                Log.e(TAG, "Question submit error", e)
            }
        }
    }
}
