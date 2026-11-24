package com.veltusstudio.glyph.agent.core

import com.veltusstudio.glyph.agent.model.AgentMessage
import com.veltusstudio.glyph.agent.model.AgentToolCall
import com.veltusstudio.glyph.agent.model.Role
import com.veltusstudio.glyph.agent.model.ToolStatus
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TrajectoryParser {

    fun normalizeToolName(rawName: String): String = when (rawName.lowercase().trim()) {
        "run_command", "terminal", "bash", "execute_command" -> "run_command"
        "edit_file", "modify_file", "write_file", "patch_file", "write_to_file" -> "edit_file"
        "read_file", "view_file", "cat_file" -> "read_file"
        "list_dir", "list_directory", "ls" -> "list_dir"
        "ask_question", "ask_user_question" -> "ask_question"
        "search_web", "web_search" -> "search_web"
        "invoke_subagent" -> "invoke_subagent"
        else -> rawName.lowercase().trim()
    }

    fun parseStep(stepJson: JSONObject): AgentMessage? {
        val type = stepJson.optString("type", "")
        val content = stepJson.optString("content", "")
        val thinking = stepJson.optString("thinking", "").takeIf { it.isNotBlank() }
            ?: stepJson.optString("thought", "").takeIf { it.isNotBlank() }

        if (type == "CORTEX_STEP_TYPE_USER_INPUT" || type == "USER_INPUT") {
            return AgentMessage(
                id = stepJson.optString("id", UUID.randomUUID().toString()),
                role = Role.USER,
                text = content
            )
        }

        if (type == "CORTEX_STEP_TYPE_PLANNER_RESPONSE" || type == "PLANNER_RESPONSE" || type.contains("ASSISTANT")) {
            val toolCallsList = mutableListOf<AgentToolCall>()
            val rawTools = stepJson.optJSONArray("tool_calls") ?: stepJson.optJSONArray("toolCalls")

            if (rawTools != null) {
                for (i in 0 until rawTools.length()) {
                    val toolObj = rawTools.optJSONObject(i) ?: continue
                    val toolId = toolObj.optString("id", "call_${i}_${System.currentTimeMillis()}")
                    val rawName = toolObj.optString("name", toolObj.optString("toolName", "unknown"))
                    val normalizedName = normalizeToolName(rawName)
                    val argsObj = toolObj.optJSONObject("arguments") ?: JSONObject()
                    val argsJson = argsObj.toString()

                    var displayCommand = ""
                    var filePath: String? = null
                    var diffBefore: String? = null
                    var diffAfter: String? = null

                    when (normalizedName) {
                        "run_command" -> {
                            displayCommand = argsObj.optString("CommandLine", argsObj.optString("command", ""))
                        }
                        "edit_file", "write_file" -> {
                            filePath = argsObj.optString("Path", argsObj.optString("path", ""))
                            diffBefore = argsObj.optString("Target", "")
                            diffAfter = argsObj.optString("Replacement", argsObj.optString("Content", ""))
                            displayCommand = "Edit $filePath"
                        }
                        "read_file" -> {
                            filePath = argsObj.optString("Path", argsObj.optString("path", ""))
                            displayCommand = "Read $filePath"
                        }
                        "ask_question" -> {
                            displayCommand = "Question for User"
                        }
                        else -> {
                            displayCommand = normalizedName
                        }
                    }

                    val rawStatus = toolObj.optString("status", "PENDING").uppercase()
                    val status = when {
                        rawStatus.contains("DONE") || rawStatus.contains("SUCCESS") || rawStatus.contains("COMPLETED") -> ToolStatus.COMPLETED
                        rawStatus.contains("RUNNING") -> ToolStatus.RUNNING
                        rawStatus.contains("ERROR") || rawStatus.contains("FAIL") -> ToolStatus.FAILED
                        else -> ToolStatus.PENDING
                    }

                    val output = toolObj.optString("output", null)

                    toolCallsList.add(
                        AgentToolCall(
                            id = toolId,
                            name = normalizedName,
                            argumentsJson = argsJson,
                            displayCommand = displayCommand,
                            status = status,
                            output = output,
                            diffBefore = diffBefore,
                            diffAfter = diffAfter,
                            filePath = filePath
                        )
                    )
                }
            }

            return AgentMessage(
                id = stepJson.optString("id", UUID.randomUUID().toString()),
                role = Role.ASSISTANT,
                text = content,
                thinking = thinking,
                isThinkingExpanded = true,
                toolCalls = toolCallsList
            )
        }

        return null
    }

    fun parseTrajectoryJson(trajectoryJsonStr: String): List<AgentMessage> {
        val messages = mutableListOf<AgentMessage>()
        try {
            val root = JSONObject(trajectoryJsonStr)
            val steps = root.optJSONArray("steps") ?: JSONArray()
            for (i in 0 until steps.length()) {
                val stepObj = steps.optJSONObject(i) ?: continue
                parseStep(stepObj)?.let { messages.add(it) }
            }
        } catch (_: Exception) {}
        return messages
    }
}
