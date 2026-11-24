package com.veltusstudio.glyph.agent.model

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class ToolStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

enum class AgentStatus {
    IDLE,
    CONNECTING,
    THINKING,
    EXECUTING_TOOL,
    AWAITING_APPROVAL,
    AWAITING_QUESTION,
    ERROR
}

data class AgentToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String = "",
    val displayCommand: String = "",
    val status: ToolStatus = ToolStatus.PENDING,
    val output: String? = null,
    val diffBefore: String? = null,
    val diffAfter: String? = null,
    val filePath: String? = null
)

data class AgentMessage(
    val id: String,
    val role: Role,
    val text: String,
    val thinking: String? = null,
    val isThinkingExpanded: Boolean = true,
    val toolCalls: List<AgentToolCall> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class PendingToolApproval(
    val toolCallId: String,
    val toolName: String,
    val actionSummary: String,
    val commandOrDiff: String,
    val onDecision: (approved: Boolean, rememberChoice: Boolean) -> Unit
)

data class PendingUserQuestion(
    val questionId: String,
    val questionText: String,
    val options: List<String>,
    val isMultiSelect: Boolean,
    val onAnswer: (List<String>) -> Unit
)

data class AiModelInfo(
    val id: String,
    val displayName: String,
    val provider: String,
    val quotaResetTime: String? = null
)
