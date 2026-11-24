package com.veltusstudio.glyph.agent.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Autonomous Google Gemini Direct Cloud Engine.
 * Operates natively without requiring local Termux / Antigravity daemon processes.
 */
class GeminiDirectService private constructor() {

    companion object {
        private const val TAG = "GeminiDirectService"
        val instance: GeminiDirectService by lazy { GeminiDirectService() }
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Streams response chunks and thinking updates from Google Gemini API.
     */
    suspend fun streamGenerate(
        prompt: String,
        workspaceDir: String,
        modelName: String = DEFAULT_MODEL,
        apiKey: String,
        onThought: (String) -> Unit,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            val guidance = """
                [ GLYPH AUTONOMOUS CLOUD ENGINE ]
                
                Antigravity Hub (:1237) is currently offline or unreachable.
                To communicate directly with Google Gemini without running Termux:
                
                1. Open Drawer -> Tap Profile Header -> Select 'DIRECT API KEY'
                2. Enter your free Gemini API Key from Google AI Studio (aistudio.google.com)
                3. Tap 'SAVE & ACTIVATE KEY'
                
                Alternatively, to use the local Antigravity daemon on this device:
                Run in Termux:
                test_bridge -f -p 1236 --hub-port 1237
            """.trimIndent()
            onChunk(guidance)
            return@withContext Result.success(guidance)
        }

        val effectiveModel = when (modelName.lowercase()) {
            "gemini 3.8 flash", "gemini-3.8-flash" -> "gemini-2.5-flash" // Route to currently active flash endpoint
            "gemini 2.5 pro", "gemini-2.5-pro" -> "gemini-2.5-pro"
            else -> "gemini-2.5-flash"
        }

        onThought("Contextualizing workspace at $workspaceDir...")

        // Build workspace context snapshot (top-level files)
        val workspaceSnapshot = try {
            val dir = File(workspaceDir)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()?.take(25)?.joinToString(", ") { f ->
                    if (f.isDirectory) "${f.name}/" else f.name
                } ?: "empty"
                "Current workspace: ${dir.name} ($workspaceDir)\nContents: $files"
            } else {
                "Workspace path: $workspaceDir"
            }
        } catch (_: Exception) {
            "Workspace path: $workspaceDir"
        }

        val systemPrompt = """
            You are Antigravity, an expert agentic software engineer and coding assistant integrated into Glyph Mobile IDE on Android.
            Provide concise, accurate code solutions, diffs, and terminal instructions.
            Format code with markdown backticks and language identifiers.
            Strictly NEVER use any emojis in your response.
            Maintain a clean, professional, high-tech cybernetic tone.
            
            $workspaceSnapshot
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:streamGenerateContent?alt=sse&key=$apiKey"

        val rootJson = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemPrompt))
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 8192)
            })
        }

        val req = Request.Builder()
            .url(url)
            .post(rootJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", "Glyph-Mobile-IDE/2.3")
            .build()

        onThought("Awaiting stream response from Gemini Cloud...")

        val fullResponse = StringBuilder()

        try {
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: "HTTP ${response.code}"
                    val cleanErr = try {
                        val errJson = JSONObject(errBody)
                        errJson.optJSONObject("error")?.optString("message") ?: errBody
                    } catch (_: Exception) {
                        errBody
                    }
                    Log.e(TAG, "Gemini API error: $cleanErr")
                    return@withContext Result.failure(Exception("Gemini API Error (${response.code}): $cleanErr"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body from Gemini"))
                val reader = BufferedReader(InputStreamReader(body.byteStream()))

                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.startsWith("data: ")) {
                        val dataStr = line.removePrefix("data: ").trim()
                        if (dataStr.isNotBlank() && dataStr != "[DONE]") {
                            try {
                                val chunkJson = JSONObject(dataStr)
                                val candidates = chunkJson.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val firstCand = candidates.getJSONObject(0)
                                    val content = firstCand.optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    if (parts != null) {
                                        for (i in 0 until parts.length()) {
                                            val part = parts.getJSONObject(i)
                                            val text = part.optString("text", "")
                                            if (text.isNotEmpty()) {
                                                fullResponse.append(text)
                                                onChunk(text)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing SSE JSON chunk: ${e.message}")
                            }
                        }
                    }
                    line = reader.readLine()
                }
            }

            Result.success(fullResponse.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Network exception during Gemini stream", e)
            Result.failure(e)
        }
    }
}
