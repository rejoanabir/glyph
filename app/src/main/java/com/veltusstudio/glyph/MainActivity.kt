package com.veltusstudio.glyph

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val bgObsidian = Color.parseColor("#080B10")
    private val surfaceCard = Color.parseColor("#0F151F")
    private val surfaceElevated = Color.parseColor("#141D2B")
    private val codeBlockBg = Color.parseColor("#06090E")
    private val userBubbleBg = Color.parseColor("#132033")
    private val borderSubtle = Color.parseColor("#1C2636")
    private val accentCyan = Color.parseColor("#00D2FF")
    private val textPrimary = Color.parseColor("#F1F5F9")
    private val textMuted = Color.parseColor("#94A3B8")
    private val dangerRed = Color.parseColor("#EF4444")

    private val pickModelRequestCode = 9001
    private val micPermissionRequestCode = 9002
    private val pickAttachmentRequestCode = 9003

    private var attachedFileName: String? = null
    private var attachedFileContent: String? = null
    private var webSearchEnabled: Boolean = false

    private lateinit var attachmentBanner: LinearLayout
    private lateinit var attachmentLabel: TextView
    private lateinit var webToggleBtn: TextView

    private data class CatalogModel(
        val title: String,
        val fileName: String,
        val sizeLabel: String,
        val badge: String,
        val url: String
    )

    private val modelCatalog = listOf(
        CatalogModel(
            title = "Llama 3.2 1B Instruct",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeLabel = "808 MB",
            badge = "Ultra-Fast • 2GB+ RAM",
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        ),
        CatalogModel(
            title = "Qwen 2.5 1.5B Instruct",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeLabel = "1.12 GB",
            badge = "Balanced • 3GB+ RAM",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            title = "Qwen 2.5 Coder 1.5B",
            fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            sizeLabel = "1.12 GB",
            badge = "Code & Scripts • 3GB+ RAM",
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            title = "Qwen 2.5 3B Instruct",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            sizeLabel = "2.10 GB",
            badge = "High Reasoning • 6GB+ RAM",
            url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf"
        )
    )

    private val personaPresets = listOf(
        "General" to "You are Glyph, a standalone local-first AI assistant crafted by Veltus Studio, running offline on a local language model. Never claim to be made by Anthropic, OpenAI, or Google. Be concise, helpful, and direct.",
        "Coder" to "You are Glyph, an expert senior software engineer crafted by Veltus Studio. Provide clean, production-ready code with brief explanations and edge-case handling. Be direct and skip filler.",
        "Editor" to "You are Glyph, a sharp technical editor crafted by Veltus Studio. Improve clarity, grammar, and structure while preserving the user's voice. Output the improved text cleanly."
    )

    private data class ChatSession(
        val id: String,
        var title: String,
        var updatedAt: Long,
        val messages: MutableList<Pair<String, String>>
    )

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val llamaBridge = LlamaBridge()

    private val savedSessions = mutableListOf<ChatSession>()
    private var activeSessionId: String = UUID.randomUUID().toString()
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    private lateinit var subtitleText: TextView
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var emptySubLabel: TextView
    private lateinit var downloadBtn: TextView
    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var emptyState: LinearLayout
    private lateinit var inputField: EditText
    private lateinit var micButton: TextView
    private lateinit var sendButton: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isGenerating = false
    private var isBusyModel = false

    private fun prefs() = getSharedPreferences("glyph_prefs", Context.MODE_PRIVATE)
    private fun isNativeMode(): Boolean = prefs().getBoolean("native_mode", true)
    private fun getSavedModelPath(): String = prefs().getString("model_path", "") ?: ""
    private fun getSavedModelName(): String = prefs().getString("model_name", "") ?: ""
    private fun getServerUrl(): String = prefs().getString("server_url", "http://127.0.0.1:8080") ?: "http://127.0.0.1:8080"
    private fun getSystemPrompt(): String = prefs().getString("system_prompt", personaPresets[0].second) ?: personaPresets[0].second
    private fun getTemperature(): Float = prefs().getFloat("temperature", 0.7f)
    private fun getMaxTokens(): Int = prefs().getInt("max_tokens", 1024)
    private fun getCtxSize(): Int = prefs().getInt("n_ctx", 2048)
    private fun getCpuThreads(): Int {
        val defaultThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        return prefs().getInt("n_threads", defaultThreads)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            @Suppress("DEPRECATION")
            window.statusBarColor = bgObsidian
            @Suppress("DEPRECATION")
            window.navigationBarColor = bgObsidian

            val rootView = buildUi()
            setContentView(rootView)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                rootView.setOnApplyWindowInsetsListener { v, insets ->
                    val sysBars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                    v.setPadding(
                        dp(16) + sysBars.left,
                        dp(16) + sysBars.top,
                        dp(16) + sysBars.right,
                        dp(16) + sysBars.bottom
                    )
                    insets
                }
            }

            loadSessionsFromDisk()
            initializeEngine()
        } catch (e: Throwable) {
            val errorText = TextView(this).apply {
                text = "Startup Error:\n\n${e.stackTraceToString()}"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#7F1D1D"))
                setPadding(32, 64, 32, 32)
                textSize = 12f
                setTextIsSelectable(true)
            }
            setContentView(ScrollView(this).apply { addView(errorText) })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        llamaBridge.unload()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedBox(fillColor: Int, strokeColor: Int, radiusDp: Int, strokeDp: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }

    private fun setStatus(label: String, colorHex: String) {
        statusDot.background = roundedBox(Color.parseColor(colorHex), Color.TRANSPARENT, 50, 0)
        statusLabel.text = label
    }

    // --- Voice Input (SpeechRecognizer) ---

    private fun toggleVoiceInput() {
        if (isGenerating) return
        if (isListening) {
            speechRecognizer?.stopListening()
            setListeningState(false)
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionRequestCode)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition unavailable on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = setListeningState(true)
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() = setListeningState(false)
                    override fun onError(error: Int) {
                        setListeningState(false)
                        Toast.makeText(this@MainActivity, "Voice input stopped ($error)", Toast.LENGTH_SHORT).show()
                    }
                    override fun onResults(results: Bundle?) {
                        setListeningState(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()?.trim().orEmpty()
                        if (spoken.isNotEmpty()) {
                            val current = inputField.text.toString()
                            val updated = if (current.isBlank()) spoken else "$current$spoken"
                            inputField.setText(updated)
                            inputField.setSelection(updated.length)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            inputField.hint = "Hearing: $partial..."
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
        setListeningState(true)
    }

    private fun setListeningState(listening: Boolean) {
        isListening = listening
        if (listening) {
            micButton.text = "●"
            micButton.setTextColor(bgObsidian)
            micButton.background = roundedBox(dangerRed, Color.TRANSPARENT, 12, 0)
            inputField.hint = "Listening..."
        } else {
            micButton.text = "Mic"
            micButton.setTextColor(accentCyan)
            micButton.background = roundedBox(surfaceCard, borderSubtle, 12)
            inputField.hint = "Ask Glyph anything..."
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micPermissionRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleVoiceInput()
        }
    }

    // --- Persistent Chat Storage & Markdown Export ---

    private fun openAttachmentPicker() {
        if (isGenerating) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, pickAttachmentRequestCode)
    }

    private fun loadAttachmentFromUri(uri: Uri) {
        val name = queryDisplayName(uri)
        executor.execute {
            try {
                val maxChars = (getCtxSize() * 2).coerceIn(2000, 12000)
                val rawText = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val buf = CharArray(maxChars)
                    val read = reader.read(buf)
                    if (read > 0) String(buf, 0, read) else ""
                }.orEmpty()

                mainHandler.post {
                    if (rawText.isBlank()) {
                        Toast.makeText(this, "File is empty or unreadable", Toast.LENGTH_SHORT).show()
                    } else {
                        attachedFileName = name
                        attachedFileContent = rawText
                        attachmentLabel.text = "Attached: $name (${rawText.length} chars)"
                        attachmentBanner.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearAttachment() {
        attachedFileName = null
        attachedFileContent = null
        attachmentBanner.visibility = View.GONE
    }

    private fun fetchWebSnippets(query: String): String {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val conn = (URL("https://lite.duckduckgo.com/lite/?q=$encoded").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                connectTimeout = 5000
                readTimeout = 6000
            }
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            val snippetRegex = Regex("<td class=['\"]result-snippet['\"]>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val snippets = snippetRegex.findAll(html)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), " ").replace("&quot;", """).replace("&amp;", "&").replace("&#x27;", "'").replace(Regex("\s+"), " ").trim() }
                .filter { it.length > 20 }
                .take(3)
                .toList()
            if (snippets.isEmpty()) "" else snippets.mapIndexed { idx, s -> "[${idx + 1}] $s" }.joinToString("
")
        } catch (_: Exception) {
            ""
        }
    }

    private fun setKeepAliveService(active: Boolean) {
        try {
            val svcIntent = Intent(this, GlyphKeepAliveService::class.java)
            if (active) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svcIntent)
                } else {
                    startService(svcIntent)
                }
            } else {
                stopService(svcIntent)
            }
        } catch (_: Exception) { }
    }

    private fun sessionsFile(): File = File(filesDir, "chats.json")

    private fun loadSessionsFromDisk() {
        savedSessions.clear()
        val file = sessionsFile()
        if (!file.exists()) return
        try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                val msgsJson = obj.getJSONArray("messages")
                val msgs = mutableListOf<Pair<String, String>>()
                for (j in 0 until msgsJson.length()) {
                    val m = msgsJson.getJSONObject(j)
                    msgs.add(m.getString("role") to m.getString("content"))
                }
                if (msgs.isNotEmpty()) {
                    savedSessions.add(ChatSession(id, title, updatedAt, msgs))
                }
            }
            savedSessions.sortByDescending { it.updatedAt }
            val lastId = prefs().getString("active_session_id", null)
            val target = savedSessions.firstOrNull { it.id == lastId } ?: savedSessions.firstOrNull()
            if (target != null) {
                switchToSession(target)
            }
        } catch (_: Exception) { }
    }

    private fun persistActiveSession() {
        if (conversationHistory.isEmpty()) return
        val firstUserPrompt = conversationHistory.firstOrNull { it.first == "user" }?.second ?: "Untitled Chat"
        val cleanTitle = firstUserPrompt.replace('\n', ' ').trim().take(42).let {
            if (firstUserPrompt.length > 42) "$it..." else it
        }
        val now = System.currentTimeMillis()
        val existingIdx = savedSessions.indexOfFirst { it.id == activeSessionId }
        val session = ChatSession(
            id = activeSessionId,
            title = cleanTitle,
            updatedAt = now,
            messages = conversationHistory.toMutableList()
        )
        if (existingIdx >= 0) {
            savedSessions[existingIdx] = session
        } else {
            savedSessions.add(0, session)
        }
        savedSessions.sortByDescending { it.updatedAt }
        prefs().edit().putString("active_session_id", activeSessionId).apply()
        writeSessionsToDisk()
    }

    private fun writeSessionsToDisk() {
        try {
            val arr = JSONArray()
            for (s in savedSessions.take(40)) {
                val msgsArr = JSONArray()
                for ((role, content) in s.messages) {
                    msgsArr.put(JSONObject().put("role", role).put("content", content))
                }
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("title", s.title)
                        .put("updatedAt", s.updatedAt)
                        .put("messages", msgsArr)
                )
            }
            sessionsFile().writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    private fun shareSessionAsMarkdown(title: String, messages: List<Pair<String, String>>) {
        if (messages.isEmpty()) {
            Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val md = buildString {
            append("# $title\n\n")
            for ((role, content) in messages) {
                val speaker = if (role == "user") "**You**" else "**Glyph**"
                append("### $speaker\n$content\n\n---\n\n")
            }
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Glyph Chat: $title")
            putExtra(Intent.EXTRA_TEXT, md)
        }
        startActivity(Intent.createChooser(sendIntent, "Export Markdown Chat"))
    }

    private fun switchToSession(session: ChatSession) {
        activeSessionId = session.id
        conversationHistory.clear()
        conversationHistory.addAll(session.messages)
        prefs().edit().putString("active_session_id", activeSessionId).apply()

        messageContainer.removeAllViews()
        if (conversationHistory.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            messageContainer.addView(emptyState)
        } else {
            emptyState.visibility = View.GONE
            for ((role, content) in conversationHistory) {
                val holder = addMessageBubble(isUser = (role == "user"), initialText = content)
                if (role != "user") {
                    renderFormattedContent(holder.contentBox, content)
                }
            }
            scrollToBottom()
        }
    }

    private fun openHistoryDialog() {
        if (isGenerating) {
            Toast.makeText(this, "Wait for current generation to finish", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgObsidian)
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }

        var dialogRef: AlertDialog? = null

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        val newSessionBtn = TextView(this).apply {
            text = "+ New Chat"
            setTextColor(bgObsidian)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBox(accentCyan, Color.TRANSPARENT, 10, 0)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                dialogRef?.dismiss()
                startNewChat()
            }
        }

        val exportCurrentBtn = TextView(this).apply {
            text = "Share .md"
            setTextColor(textPrimary)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBox(surfaceCard, borderSubtle, 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                val currentTitle = savedSessions.firstOrNull { it.id == activeSessionId }?.title ?: "Glyph Conversation"
                shareSessionAsMarkdown(currentTitle, conversationHistory)
            }
        }

        actionRow.addView(newSessionBtn)
        actionRow.addView(exportCurrentBtn)
        container.addView(actionRow)

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.US)

        fun renderSessionList() {
            listBox.removeAllViews()
            if (savedSessions.isEmpty()) {
                listBox.addView(TextView(this).apply {
                    text = "No saved conversations yet."
                    setTextColor(textMuted)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(16), 0, dp(16))
                })
                return
            }

            for (session in savedSessions) {
                val isCurrent = session.id == activeSessionId
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundedBox(
                        if (isCurrent) surfaceElevated else surfaceCard,
                        if (isCurrent) accentCyan else borderSubtle,
                        10
                    )
                    setPadding(dp(12), dp(10), dp(10), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                }

                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        dialogRef?.dismiss()
                        switchToSession(session)
                    }
                }

                infoCol.addView(TextView(this).apply {
                    text = session.title
                    setTextColor(if (isCurrent) accentCyan else textPrimary)
                    textSize = 13f
                    typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    maxLines = 1
                })

                infoCol.addView(TextView(this).apply {
                    text = "${session.messages.size} msgs • ${fmt.format(Date(session.updatedAt))}"
                    setTextColor(textMuted)
                    textSize = 10f
                })

                val shareBtn = TextView(this).apply {
                    text = "Share"
                    setTextColor(accentCyan)
                    textSize = 11f
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    setOnClickListener { shareSessionAsMarkdown(session.title, session.messages) }
                }

                val delBtn = TextView(this).apply {
                    text = "Del"
                    setTextColor(dangerRed)
                    textSize = 11f
                    setPadding(dp(8), dp(6), dp(4), dp(6))
                    setOnClickListener {
                        savedSessions.removeAll { it.id == session.id }
                        writeSessionsToDisk()
                        if (session.id == activeSessionId) {
                            startNewChat(silent = true)
                        }
                        renderSessionList()
                    }
                }

                row.addView(infoCol)
                row.addView(shareBtn)
                row.addView(delBtn)
                listBox.addView(row)
            }
        }

        renderSessionList()
        container.addView(listBox)

        val scroll = ScrollView(this).apply { addView(container) }
        dialogRef = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Chat History & Export")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()

        dialogRef.show()
        dialogRef.window?.setBackgroundDrawable(roundedBox(bgObsidian, borderSubtle, 16))
    }

    // --- Engine & Model Management ---

    private fun initializeEngine(forceReload: Boolean = false) {
        if (!isNativeMode()) {
            subtitleText.text = "Hybrid Server Mode"
            checkServerHealth()
            return
        }

        val modelsDir = File(filesDir, "models").apply { mkdirs() }
        val savedPath = getSavedModelPath()
        val savedName = getSavedModelName()

        val targetFile = when {
            savedPath.isNotEmpty() && File(savedPath).exists() -> File(savedPath)
            else -> modelsDir.listFiles()?.firstOrNull { it.name.endsWith(".gguf") && it.length() > 10_000_000L }
        }

        if (targetFile != null && targetFile.exists()) {
            val targetName = if (targetFile.absolutePath == savedPath && savedName.isNotEmpty()) savedName else targetFile.name
            val desiredThreads = getCpuThreads()
            val desiredCtx = getCtxSize()
            if (!forceReload &&
                llamaBridge.isLoaded &&
                llamaBridge.loadedModelPath == targetFile.absolutePath &&
                llamaBridge.loadedThreads == desiredThreads &&
                llamaBridge.loadedCtxSize == desiredCtx
            ) {
                setStatus("Ready", "#10B981")
                subtitleText.text = "$targetName • $desiredCtx ctx"
                return
            }
            loadNativeModelAsync(targetFile.absolutePath, targetName)
        } else {
            setStatus("No Model", "#F59E0B")
            subtitleText.text = "by Veltus Studio • Offline"
            emptySubLabel.text = "Standalone On-Device Mode\nPick a .gguf file or download Qwen 1.5B below to begin"
        }
    }

    private fun loadNativeModelAsync(path: String, displayName: String) {
        if (isBusyModel) return
        isBusyModel = true
        val threads = getCpuThreads()
        val ctxSize = getCtxSize()

        setStatus("Loading...", "#F59E0B")
        subtitleText.text = "Loading $displayName..."
        emptySubLabel.text = "Loading $displayName into RAM ($threads threads, $ctxSize ctx)..."

        executor.execute {
            val ok = llamaBridge.loadModel(path, displayName, threads = threads, ctxSize = ctxSize)
            mainHandler.post {
                isBusyModel = false
                if (ok) {
                    prefs().edit()
                        .putString("model_path", path)
                        .putString("model_name", displayName)
                        .apply()
                    setStatus("Ready", "#10B981")
                    subtitleText.text = "$displayName • $ctxSize ctx"
                    emptySubLabel.text = "Loaded: $displayName\n100% Offline • $threads CPU threads • $ctxSize context"
                } else {
                    setStatus("Load Error", "#EF4444")
                    subtitleText.text = "Model load failed"
                    emptySubLabel.text = "Failed to load $displayName.\nTry lowering Context Size in ⚙ or select another GGUF."
                }
            }
        }
    }

    private fun openModelFilePicker() {
        if (isBusyModel || isGenerating) {
            Toast.makeText(this, "Wait for current operation to finish", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, pickModelRequestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickModelRequestCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importUriAndLoadModel(uri)
        }
        if (requestCode == pickAttachmentRequestCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            loadAttachmentFromUri(uri)
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIdx)
            }
        }
        return "custom_model.gguf"
    }

    private fun importUriAndLoadModel(uri: Uri) {
        val fileName = queryDisplayName(uri)
        if (!fileName.lowercase().endsWith(".gguf")) {
            Toast.makeText(this, "Please select a .gguf model file", Toast.LENGTH_LONG).show()
            return
        }

        isBusyModel = true
        setStatus("Importing...", "#F59E0B")
        emptySubLabel.text = "Importing $fileName into app storage..."

        executor.execute {
            try {
                val modelsDir = File(filesDir, "models").apply { mkdirs() }
                val destFile = File(modelsDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                mainHandler.post {
                    isBusyModel = false
                    loadNativeModelAsync(destFile.absolutePath, fileName)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isBusyModel = false
                    setStatus("Import Error", "#EF4444")
                    emptySubLabel.text = "Import failed: ${e.message}"
                }
            }
        }
    }

    private fun downloadCatalogModel(model: CatalogModel) {
        if (isBusyModel || isGenerating) {
            Toast.makeText(this, "Another download or generation is active", Toast.LENGTH_SHORT).show()
            return
        }
        isBusyModel = true
        setStatus("0%", "#F59E0B")
        subtitleText.text = "Downloading ${model.title}..."
        downloadBtn.isEnabled = false
        Toast.makeText(this, "Downloading ${model.title} (${model.sizeLabel})...", Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                val modelsDir = File(filesDir, "models").apply { mkdirs() }
                val destFile = File(modelsDir, model.fileName)
                val tempFile = File(modelsDir, "${model.fileName}.part")

                val conn = (URL(model.url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val totalBytes = conn.contentLengthLong
                var downloaded = 0L
                var lastPct = -1

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100) / totalBytes).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    mainHandler.post {
                                        statusLabel.text = "$pct%"
                                        subtitleText.text = "Downloading ${model.title} ($pct%)"
                                        downloadBtn.text = "Downloading... $pct%"
                                        emptySubLabel.text = "Downloading ${model.title} ($pct%)..."
                                    }
                                }
                            }
                        }
                    }
                }
                conn.disconnect()

                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)

                mainHandler.post {
                    isBusyModel = false
                    downloadBtn.isEnabled = true
                    downloadBtn.text = "Download Qwen 1.5B (1.12 GB)"
                    loadNativeModelAsync(destFile.absolutePath, model.fileName)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isBusyModel = false
                    downloadBtn.isEnabled = true
                    downloadBtn.text = "Retry Download Qwen 1.5B"
                    setStatus("Download Failed", "#EF4444")
                    subtitleText.text = "Download failed"
                    emptySubLabel.text = "Download failed: ${e.message}"
                }
            }
        }
    }

    private fun copyToClipboard(label: String, text: String, feedbackView: TextView? = null) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        if (feedbackView != null) {
            val prev = feedbackView.text
            feedbackView.text = "Copied"
            feedbackView.setTextColor(accentCyan)
            mainHandler.postDelayed({
                feedbackView.text = prev
                feedbackView.setTextColor(textMuted)
            }, 1400)
        } else {
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgObsidian)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // 1. Top Studio Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleText = TextView(this).apply {
            text = "GLYPH"
            setTextColor(textPrimary)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }

        subtitleText = TextView(this).apply {
            text = "by Veltus Studio"
            setTextColor(textMuted)
            textSize = 10.5f
            maxLines = 1
        }

        titleCol.addView(titleText)
        titleCol.addView(subtitleText)

        // History Pill
        val historyBtn = TextView(this).apply {
            text = "History"
            setTextColor(textPrimary)
            textSize = 11f
            background = roundedBox(surfaceCard, borderSubtle, 50)
            setPadding(dp(9), dp(6), dp(9), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(5)
            }
            setOnClickListener { openHistoryDialog() }
        }

        // New Chat Pill
        val newChatBtn = TextView(this).apply {
            text = "+"
            setTextColor(textPrimary)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox(surfaceCard, borderSubtle, 50)
            setPadding(dp(9), dp(4), dp(9), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(5)
            }
            setOnClickListener { startNewChat() }
        }

        // Settings & Model Manager Pill
        val settingsBtn = TextView(this).apply {
            text = "⚙"
            setTextColor(textPrimary)
            textSize = 12f
            background = roundedBox(surfaceCard, borderSubtle, 50)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(5)
            }
            setOnClickListener { openSettingsDialog() }
        }

        // Status Pill
        val statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox(surfaceCard, borderSubtle, 50)
            setPadding(dp(9), dp(6), dp(9), dp(6))
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                marginEnd = dp(5)
            }
            background = roundedBox(Color.parseColor("#F59E0B"), Color.TRANSPARENT, 50, 0)
        }

        statusLabel = TextView(this).apply {
            text = "Init"
            setTextColor(textMuted)
            textSize = 10.5f
        }

        statusPill.addView(statusDot)
        statusPill.addView(statusLabel)
        statusPill.setOnClickListener {
            if (isNativeMode()) openSettingsDialog() else checkServerHealth()
        }

        header.addView(titleCol)
        header.addView(historyBtn)
        header.addView(newChatBtn)
        header.addView(settingsBtn)
        header.addView(statusPill)
        root.addView(header)

        // 2. Scrollable Message Feed
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(10)
            }
        }

        messageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Empty State Placeholder
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(36), dp(16), dp(16))
        }

        val emptyTitle = TextView(this).apply {
            text = "Local-First Intelligence"
            setTextColor(accentCyan)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        emptySubLabel = TextView(this).apply {
            text = "Standalone On-Device Mode\nPick a .gguf file or download Qwen 1.5B below to begin"
            setTextColor(textMuted)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(18))
        }

        val pickBtn = TextView(this).apply {
            text = "Select Model (.gguf)"
            setTextColor(bgObsidian)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBox(accentCyan, Color.TRANSPARENT, 12, 0)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener { openModelFilePicker() }
        }

        downloadBtn = TextView(this).apply {
            text = "Download Qwen 1.5B (1.12 GB)"
            setTextColor(textPrimary)
            textSize = 12f
            gravity = Gravity.CENTER
            background = roundedBox(surfaceCard, borderSubtle, 12)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener { downloadCatalogModel(modelCatalog[1]) }
        }

        val catalogShortcut = TextView(this).apply {
            text = "Browse All Models in ⚙"
            setTextColor(accentCyan)
            textSize = 11.5f
            setOnClickListener { openSettingsDialog() }
        }

        emptyState.addView(emptyTitle)
        emptyState.addView(emptySubLabel)
        emptyState.addView(pickBtn)
        emptyState.addView(downloadBtn)
        emptyState.addView(catalogShortcut)
        messageContainer.addView(emptyState)
        scrollView.addView(messageContainer)
        root.addView(scrollView)

        // 3. Quick Prompt Chips Row
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val presets = listOf(
            "Summarize" to "Summarize cleanly:\n",
            "Fix Grammar" to "Fix grammar and improve clarity:\n",
            "Explain Code" to "Explain this code step by step:\n",
            "Bullet Points" to "Convert this into concise bullet points:\n"
        )

        presets.forEach { (label, promptPrefix) ->
            val chip = TextView(this).apply {
                text = label
                setTextColor(textPrimary)
                textSize = 12f
                background = roundedBox(surfaceCard, borderSubtle, 20)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(8)
                }
                setOnClickListener {
                    inputField.setText(promptPrefix)
                    inputField.setSelection(inputField.text.length)
                }
            }
            chipRow.addView(chip)
        }

        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        // Attachment Preview Banner
        attachmentBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = roundedBox(surfaceElevated, accentCyan, 10)
            setPadding(dp(12), dp(7), dp(10), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }
        attachmentLabel = TextView(this).apply {
            text = ""
            setTextColor(accentCyan)
            textSize = 11.5f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val clearAttachBtn = TextView(this).apply {
            text = "✕"
            setTextColor(dangerRed)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(2), dp(4), dp(2))
            setOnClickListener { clearAttachment() }
        }
        attachmentBanner.addView(attachmentLabel)
        attachmentBanner.addView(clearAttachBtn)
        root.addView(attachmentBanner)

        // 4. Input Bar (+ Attach + Web + Input + Mic + Send)
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val attachBtn = TextView(this).apply {
            text = "+"
            setTextColor(accentCyan)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBox(surfaceCard, borderSubtle, 12)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(5)
            }
            setOnClickListener { openAttachmentPicker() }
        }

        webToggleBtn = TextView(this).apply {
            text = "Web"
            setTextColor(textMuted)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBox(surfaceCard, borderSubtle, 12)
            setPadding(dp(9), dp(12), dp(9), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(5)
            }
            setOnClickListener {
                webSearchEnabled = !webSearchEnabled
                setTextColor(if (webSearchEnabled) bgObsidian else textMuted)
                background = if (webSearchEnabled) roundedBox(accentCyan, Color.TRANSPARENT, 12, 0) else roundedBox(surfaceCard, borderSubtle, 12)
                Toast.makeText(this@MainActivity, if (webSearchEnabled) "Live Web Grounding ON" else "Offline Mode ON", Toast.LENGTH_SHORT).show()
            }
        }

        inputField = EditText(this).apply {
            hint = "Ask Glyph anything..."
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            background = roundedBox(surfaceCard, borderSubtle, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(6)
            }
        }

        micButton = TextView(this).apply {
            text = "Mic"
            setTextColor(accentCyan)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBox(surfaceCard, borderSubtle, 12)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { toggleVoiceInput() }
        }

        sendButton = Button(this).apply {
            text = "Send"
            setTextColor(bgObsidian)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = roundedBox(accentCyan, Color.TRANSPARENT, 12, 0)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                if (isGenerating) {
                    llamaBridge.stop()
                } else {
                    sendMessage()
                }
            }
        }

        inputRow.addView(attachBtn)
        inputRow.addView(webToggleBtn)
        inputRow.addView(inputField)
        inputRow.addView(micButton)
        inputRow.addView(sendButton)
        root.addView(inputRow)

        return root
    }

    private fun startNewChat(silent: Boolean = false) {
        if (isGenerating) {
            llamaBridge.stop()
            return
        }
        activeSessionId = UUID.randomUUID().toString()
        conversationHistory.clear()
        prefs().edit().putString("active_session_id", activeSessionId).apply()
        messageContainer.removeAllViews()
        emptyState.visibility = View.VISIBLE
        messageContainer.addView(emptyState)
        if (!silent) {
            Toast.makeText(this, "New conversation started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettingsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgObsidian)
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        fun makeLabel(title: String): TextView = TextView(this).apply {
            text = title
            setTextColor(accentCyan)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(6))
        }

        fun makeInput(value: String, multiline: Boolean = false, numeric: Boolean = false): EditText = EditText(this).apply {
            setText(value)
            setTextColor(textPrimary)
            textSize = 13f
            background = roundedBox(surfaceCard, borderSubtle, 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            if (multiline) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3
                maxLines = 5
            } else if (numeric) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }

        var dialogRef: AlertDialog? = null

        val nativeSwitch = Switch(this).apply {
            text = "Standalone On-Device Engine"
            setTextColor(textPrimary)
            textSize = 13f
            isChecked = isNativeMode()
            setPadding(0, dp(4), 0, dp(8))
        }
        container.addView(nativeSwitch)

        // Installed GGUF Models List
        container.addView(makeLabel("INSTALLED GGUF MODELS"))
        val modelsListBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun refreshModelList() {
            modelsListBox.removeAllViews()
            val modelsDir = File(filesDir, "models").apply { mkdirs() }
            val ggufFiles = modelsDir.listFiles()?.filter { it.name.endsWith(".gguf") }?.sortedBy { it.name } ?: emptyList()

            if (ggufFiles.isEmpty()) {
                modelsListBox.addView(TextView(this).apply {
                    text = "No GGUF models installed yet."
                    setTextColor(textMuted)
                    textSize = 12f
                    setPadding(0, dp(4), 0, dp(6))
                })
            } else {
                for (file in ggufFiles) {
                    val isActive = llamaBridge.isLoaded && llamaBridge.loadedModelPath == file.absolutePath
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = roundedBox(
                            if (isActive) surfaceElevated else surfaceCard,
                            if (isActive) accentCyan else borderSubtle,
                            10
                        )
                        setPadding(dp(12), dp(10), dp(10), dp(10))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = dp(6)
                        }
                    }

                    val infoCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            dialogRef?.dismiss()
                            loadNativeModelAsync(file.absolutePath, file.name)
                        }
                    }

                    infoCol.addView(TextView(this).apply {
                        text = if (isActive) "● ${file.name}" else file.name
                        setTextColor(if (isActive) accentCyan else textPrimary)
                        textSize = 12f
                        typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    })

                    infoCol.addView(TextView(this).apply {
                        text = "${formatFileSize(file.length())} • ${if (isActive) "Active in RAM" else "Tap to load"}"
                        setTextColor(textMuted)
                        textSize = 10f
                    })

                    val deleteBtn = TextView(this).apply {
                        text = "Delete"
                        setTextColor(dangerRed)
                        textSize = 11f
                        setPadding(dp(10), dp(6), dp(6), dp(6))
                        setOnClickListener {
                            if (isActive) llamaBridge.unload()
                            file.delete()
                            if (getSavedModelPath() == file.absolutePath) {
                                prefs().edit().remove("model_path").remove("model_name").apply()
                            }
                            refreshModelList()
                            initializeEngine()
                        }
                    }

                    row.addView(infoCol)
                    row.addView(deleteBtn)
                    modelsListBox.addView(row)
                }
            }

            val importRowBtn = TextView(this).apply {
                text = "+ Import .gguf File from Phone Storage"
                setTextColor(accentCyan)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = roundedBox(surfaceCard, borderSubtle, 10)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
                setOnClickListener {
                    dialogRef?.dismiss()
                    openModelFilePicker()
                }
            }
            modelsListBox.addView(importRowBtn)
        }

        refreshModelList()
        container.addView(modelsListBox)

        // Curated 1-Tap GGUF Model Catalog
        container.addView(makeLabel("1-TAP GGUF MODEL CATALOG"))
        val modelsDir = File(filesDir, "models")
        for (cat in modelCatalog) {
            val alreadyDownloaded = File(modelsDir, cat.fileName).exists()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBox(surfaceCard, borderSubtle, 10)
                setPadding(dp(12), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(6)
                }
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            col.addView(TextView(this).apply {
                text = cat.title
                setTextColor(textPrimary)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
            })

            col.addView(TextView(this).apply {
                text = "${cat.sizeLabel} • ${cat.badge}"
                setTextColor(textMuted)
                textSize = 10f
            })

            val actionBtn = TextView(this).apply {
                text = if (alreadyDownloaded) "Installed" else "Download"
                setTextColor(if (alreadyDownloaded) textMuted else bgObsidian)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                background = roundedBox(
                    if (alreadyDownloaded) surfaceElevated else accentCyan,
                    borderSubtle,
                    8,
                    if (alreadyDownloaded) 1 else 0
                )
                setPadding(dp(10), dp(6), dp(10), dp(6))
                if (!alreadyDownloaded) {
                    setOnClickListener {
                        dialogRef?.dismiss()
                        downloadCatalogModel(cat)
                    }
                }
            }

            card.addView(col)
            card.addView(actionBtn)
            container.addView(card)
        }

        // Context Window Selector Chips
        container.addView(makeLabel("CONTEXT WINDOW (N_CTX)"))
        var selectedCtx = getCtxSize()
        val ctxOptions = listOf(1024, 2048, 4096)
        val ctxRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val ctxChips = mutableMapOf<Int, TextView>()

        fun updateCtxChips() {
            ctxChips.forEach { (size, view) ->
                val selected = size == selectedCtx
                view.setTextColor(if (selected) bgObsidian else textPrimary)
                view.background = roundedBox(
                    if (selected) accentCyan else surfaceCard,
                    if (selected) accentCyan else borderSubtle,
                    10
                )
            }
        }

        ctxOptions.forEach { size ->
            val chip = TextView(this).apply {
                text = "$size"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    selectedCtx = size
                    updateCtxChips()
                }
            }
            ctxChips[size] = chip
            ctxRow.addView(chip)
        }
        updateCtxChips()
        container.addView(ctxRow)

        // CPU Threads Slider
        val maxCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        var selectedThreads = getCpuThreads().coerceIn(1, maxCores)
        val threadLabel = makeLabel("CPU THREADS: $selectedThreads / $maxCores")
        container.addView(threadLabel)

        val threadSeek = SeekBar(this).apply {
            max = maxCores - 1
            progress = selectedThreads - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedThreads = progress + 1
                    threadLabel.text = "CPU THREADS: $selectedThreads / $maxCores"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(threadSeek)

        // 1-Tap System Prompt Personas
        container.addView(makeLabel("SYSTEM PERSONA PRESETS"))
        val promptInput = makeInput(getSystemPrompt(), multiline = true)
        val personaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        personaPresets.forEach { (label, presetPrompt) ->
            val pChip = TextView(this).apply {
                text = label
                setTextColor(textPrimary)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = roundedBox(surfaceCard, borderSubtle, 10)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    promptInput.setText(presetPrompt)
                }
            }
            personaRow.addView(pChip)
        }
        container.addView(personaRow)
        container.addView(promptInput)

        val urlInput = makeInput(getServerUrl())
        val tempInput = makeInput(getTemperature().toString(), numeric = true)
        val tokensInput = makeInput(getMaxTokens().toString(), numeric = true)

        container.addView(makeLabel("TEMPERATURE (0.0 – 1.5)"))
        container.addView(tempInput)
        container.addView(makeLabel("MAX RESPONSE TOKENS"))
        container.addView(tokensInput)
        container.addView(makeLabel("EXTERNAL SERVER URL (HYBRID FALLBACK)"))
        container.addView(urlInput)

        val scroll = ScrollView(this).apply { addView(container) }

        dialogRef = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Glyph Studio Settings")
            .setView(scroll)
            .setPositiveButton("Save & Apply") { _, _ ->
                val useNative = nativeSwitch.isChecked
                val cleanUrl = urlInput.text.toString().trim().removeSuffix("/").ifEmpty { "http://127.0.0.1:8080" }
                val cleanPrompt = promptInput.text.toString().trim().ifEmpty { personaPresets[0].second }
                val cleanTemp = tempInput.text.toString().toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f
                val cleanTokens = tokensInput.text.toString().toIntOrNull()?.coerceIn(64, 4096) ?: 1024

                prefs().edit()
                    .putBoolean("native_mode", useNative)
                    .putInt("n_ctx", selectedCtx)
                    .putInt("n_threads", selectedThreads)
                    .putString("server_url", cleanUrl)
                    .putString("system_prompt", cleanPrompt)
                    .putFloat("temperature", cleanTemp)
                    .putInt("max_tokens", cleanTokens)
                    .apply()

                initializeEngine(forceReload = false)
                Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialogRef.show()
        dialogRef.window?.setBackgroundDrawable(roundedBox(bgObsidian, borderSubtle, 16))
    }

    private fun checkServerHealth() {
        val currentUrl = getServerUrl()
        setStatus("Checking...", "#F59E0B")
        executor.execute {
            val online = try {
                val conn = (URL("$currentUrl/health").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 1500
                    readTimeout = 1500
                }
                conn.responseCode == 200
            } catch (_: Exception) {
                false
            }

            mainHandler.post {
                if (online) {
                    setStatus("Online", "#10B981")
                    emptySubLabel.text = "Connected to $currentUrl"
                } else {
                    setStatus("Offline", "#EF4444")
                    emptySubLabel.text = "External Server offline ($currentUrl)\nSwitch to Standalone Mode in ⚙"
                }
            }
        }
    }

    private fun formatInlineMarkdown(raw: String): CharSequence {
        val builder = SpannableStringBuilder()
        var i = 0
        while (i < raw.length) {
            if (raw.startsWith("**", i)) {
                val end = raw.indexOf("**", i + 2)
                if (end != -1) {
                    val startSpan = builder.length
                    builder.append(raw.substring(i + 2, end))
                    builder.setSpan(StyleSpan(Typeface.BOLD), startSpan, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(ForegroundColorSpan(Color.WHITE), startSpan, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 2
                    continue
                }
            } else if (raw[i] == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end != -1) {
                    val startSpan = builder.length
                    builder.append(raw.substring(i + 1, end))
                    builder.setSpan(TypefaceSpan("monospace"), startSpan, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(BackgroundColorSpan(codeBlockBg), startSpan, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(ForegroundColorSpan(accentCyan), startSpan, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 1
                    continue
                }
            }
            builder.append(raw[i])
            i++
        }
        return builder
    }

    private fun renderFormattedContent(container: LinearLayout, rawText: String) {
        container.removeAllViews()

        // Extract optional <think>...</think> reasoning block (Qwen3.5 / DeepSeek-R1)
        var displayBody = rawText
        val thinkStart = rawText.indexOf("<think>")
        if (thinkStart != -1) {
            val thinkEnd = rawText.indexOf("</think>", thinkStart + 7)
            val thoughtText = if (thinkEnd != -1) {
                displayBody = (rawText.substring(0, thinkStart) + rawText.substring(thinkEnd + 8)).trim()
                rawText.substring(thinkStart + 7, thinkEnd).trim()
            } else {
                displayBody = rawText.substring(0, thinkStart).trim()
                rawText.substring(thinkStart + 7).trim()
            }

            if (thoughtText.isNotEmpty()) {
                val thinkCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBox(surfaceElevated, borderSubtle, 10)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(4)
                        bottomMargin = dp(6)
                    }
                }

                val thinkBodyView = TextView(this).apply {
                    text = thoughtText
                    setTextColor(textMuted)
                    textSize = 11.5f
                    visibility = View.GONE
                    setPadding(0, dp(6), 0, 0)
                    setTextIsSelectable(true)
                }

                val thinkToggle = TextView(this).apply {
                    text = "▸ Thought Process (${thoughtText.length} chars) — Tap to expand"
                    setTextColor(accentCyan)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        val expanding = thinkBodyView.visibility == View.GONE
                        thinkBodyView.visibility = if (expanding) View.VISIBLE else View.GONE
                        text = if (expanding) "▾ Thought Process — Tap to collapse" else "▸ Thought Process (${thoughtText.length} chars) — Tap to expand"
                    }
                }

                thinkCard.addView(thinkToggle)
                thinkCard.addView(thinkBodyView)
                container.addView(thinkCard)
            }
        }

        val parts = displayBody.split("```")
        for (index in parts.indices) {
            val segment = parts[index]
            if (index % 2 == 0) {
                val trimmed = segment.trim('\n')
                if (trimmed.isNotEmpty()) {
                    val tv = TextView(this).apply {
                        text = formatInlineMarkdown(trimmed)
                        setTextColor(textPrimary)
                        textSize = 14f
                        setLineSpacing(dp(3).toFloat(), 1f)
                        setTextIsSelectable(true)
                        setPadding(0, dp(4), 0, dp(4))
                    }
                    container.addView(tv)
                }
            } else {
                val firstNewline = segment.indexOf('\n')
                val lang = if (firstNewline != -1) segment.substring(0, firstNewline).trim() else ""
                val codeBody = (if (firstNewline != -1) segment.substring(firstNewline + 1) else segment).trimEnd('\n')

                val codeCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBox(codeBlockBg, borderSubtle, 10)
                    setPadding(dp(12), dp(8), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(6)
                        bottomMargin = dp(6)
                    }
                }

                val codeHeader = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dp(6))
                }

                val langLabel = TextView(this).apply {
                    text = if (lang.isNotEmpty()) lang.uppercase() else "CODE"
                    setTextColor(accentCyan)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val copyCodeBtn = TextView(this).apply {
                    text = "Copy"
                    setTextColor(textMuted)
                    textSize = 11f
                    setPadding(dp(8), dp(2), dp(4), dp(2))
                    setOnClickListener { copyToClipboard("Code", codeBody, this) }
                }

                codeHeader.addView(langLabel)
                codeHeader.addView(copyCodeBtn)

                val codeScroll = HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = true
                }

                val codeText = TextView(this).apply {
                    text = codeBody
                    setTextColor(Color.parseColor("#E2E8F0"))
                    textSize = 12.5f
                    typeface = Typeface.MONOSPACE
                    setTextIsSelectable(true)
                }

                codeScroll.addView(codeText)
                codeCard.addView(codeHeader)
                codeCard.addView(codeScroll)
                container.addView(codeCard)
            }
        }
    }

    private data class BubbleHolder(
        val cardView: LinearLayout,
        val senderTag: TextView,
        val contentBox: LinearLayout,
        val streamTextView: TextView,
        var latestRawText: String
    )

    private fun addMessageBubble(isUser: Boolean, initialText: String): BubbleHolder {
        if (emptyState.visibility == View.VISIBLE) {
            emptyState.visibility = View.GONE
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox(
                fillColor = if (isUser) userBubbleBg else surfaceCard,
                strokeColor = if (isUser) Color.parseColor("#3300D2FF") else borderSubtle,
                radiusDp = 14
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val senderTag = TextView(this).apply {
            text = if (isUser) "YOU" else "GLYPH"
            setTextColor(if (isUser) accentCyan else textMuted)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val streamTextView = TextView(this).apply {
            text = initialText
            setTextColor(textPrimary)
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
            setPadding(0, dp(4), 0, 0)
        }

        contentBox.addView(streamTextView)
        val holder = BubbleHolder(card, senderTag, contentBox, streamTextView, initialText)

        headerRow.addView(senderTag)

        if (!isUser) {
            val retryBtn = TextView(this).apply {
                text = "Retry"
                setTextColor(textMuted)
                textSize = 10f
                setPadding(dp(6), dp(2), dp(8), dp(2))
                setOnClickListener { regenerateLastResponse() }
            }
            headerRow.addView(retryBtn)
        }

        val copyMsgBtn = TextView(this).apply {
            text = "Copy"
            setTextColor(textMuted)
            textSize = 10f
            setPadding(dp(6), dp(2), dp(2), dp(2))
            setOnClickListener { copyToClipboard("Message", holder.latestRawText, this) }
        }

        headerRow.addView(copyMsgBtn)

        card.addView(headerRow)
        card.addView(contentBox)
        messageContainer.addView(card)

        if (isUser) {
            renderFormattedContent(contentBox, initialText)
        }

        scrollToBottom()
        return holder
    }

    private fun regenerateLastResponse() {
        if (isGenerating || conversationHistory.isEmpty()) return
        if (conversationHistory.last().first == "assistant") {
            conversationHistory.removeAt(conversationHistory.lastIndex)
            if (messageContainer.childCount > 0) {
                messageContainer.removeViewAt(messageContainer.childCount - 1)
            }
        }
        if (conversationHistory.isEmpty() || conversationHistory.last().first != "user") return
        runAssistantCompletion()
    }

    private fun scrollToBottom() {
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun buildContextWindowMessages(systemPrompt: String): List<Pair<String, String>> {
        val charBudget = (getCtxSize() * 2.2).toInt().coerceAtLeast(1500)
        val selected = mutableListOf<Pair<String, String>>()
        var usedChars = systemPrompt.length

        for (i in conversationHistory.indices.reversed()) {
            val turn = conversationHistory[i]
            if (usedChars + turn.second.length > charBudget && selected.isNotEmpty()) break
            selected.add(0, turn)
            usedChars += turn.second.length
        }

        return listOf("system" to systemPrompt) + selected
    }

    private fun sendMessage() {
        val prompt = inputField.text.toString().trim()
        if (prompt.isEmpty() || isGenerating) return

        val nativeMode = isNativeMode()
        if (nativeMode && !llamaBridge.isLoaded) {
            Toast.makeText(this, "Please select or download a .gguf model first", Toast.LENGTH_LONG).show()
            return
        }

        val fileSnapName = attachedFileName
        val fileSnapBody = attachedFileContent
        val displayPrompt = if (fileSnapName != null) "[File: $fileSnapName]\n$prompt" else prompt
        val modelPrompt = if (fileSnapName != null && fileSnapBody != null) {
            "Attached file ($fileSnapName):\n```\n$fileSnapBody\n```\n\nUser request: $prompt"
        } else {
            prompt
        }

        clearAttachment()
        inputField.setText("")
        addMessageBubble(isUser = true, initialText = displayPrompt)
        conversationHistory.add("user" to modelPrompt)
        persistActiveSession()

        runAssistantCompletion()
    }

    private fun runAssistantCompletion() {
        val nativeMode = isNativeMode()
        if (nativeMode && !llamaBridge.isLoaded) return

        val activeUrl = getServerUrl()
        val activeSystemPrompt = getSystemPrompt()
        val activeTemp = getTemperature()
        val activeMaxTokens = getMaxTokens()

        isGenerating = true
        sendButton.text = "Stop"
        setKeepAliveService(true)
        val useWebNow = webSearchEnabled

        val assistantBubble = addMessageBubble(isUser = false, initialText = if (useWebNow) "Searching web..." else "...")
        val fullReply = StringBuilder()
        var tokenCount = 0
        val startTimeMs = SystemClock.elapsedRealtime()

        executor.execute {
            try {
                var effectiveSystem = activeSystemPrompt
                if (useWebNow) {
                    val lastUserQ = conversationHistory.lastOrNull { it.first == "user" }?.second?.take(200).orEmpty()
                    val snippets = fetchWebSnippets(lastUserQ)
                    if (snippets.isNotEmpty()) {
                        effectiveSystem += "\n\nLive Web Search Context (2026):\n$snippets\nUse these facts if relevant."
                    }
                }
                val contextMessages = buildContextWindowMessages(effectiveSystem)

                if (nativeMode) {
                    llamaBridge.generate(
                        messages = contextMessages,
                        temperature = activeTemp,
                        maxTokens = activeMaxTokens
                    ) { token ->
                        tokenCount++
                        fullReply.append(token)
                        val snapshot = fullReply.toString()
                        val elapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000.0).coerceAtLeast(0.1)
                        val tps = String.format(Locale.US, "%.1f", tokenCount / elapsedSec)
                        mainHandler.post {
                            assistantBubble.senderTag.text = "GLYPH • $tps tok/s"
                            assistantBubble.latestRawText = snapshot
                            assistantBubble.streamTextView.text = snapshot
                            scrollToBottom()
                        }
                    }
                } else {
                    val conn = (URL("$activeUrl/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "text/event-stream")
                        doOutput = true
                        connectTimeout = 5000
                        readTimeout = 0
                    }

                    val messagesArray = JSONArray().apply {
                        for ((role, content) in contextMessages) {
                            put(JSONObject().put("role", role).put("content", content))
                        }
                    }

                    val payload = JSONObject().apply {
                        put("stream", true)
                        put("temperature", activeTemp.toDouble())
                        put("max_tokens", activeMaxTokens)
                        put("messages", messagesArray)
                    }

                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                        it.write(payload.toString())
                        it.flush()
                    }

                    BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val raw = line?.trim() ?: continue
                            if (!raw.startsWith("data:")) continue
                            val data = raw.removePrefix("data:").trim()
                            if (data == "[DONE]") break

                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices") ?: continue
                                if (choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    val token = if (delta != null && !delta.isNull("content")) {
                                        delta.optString("content", "")
                                    } else {
                                        ""
                                    }
                                    if (token.isNotEmpty()) {
                                        tokenCount++
                                        fullReply.append(token)
                                        val snapshot = fullReply.toString()
                                        val elapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000.0).coerceAtLeast(0.1)
                                        val tps = String.format(Locale.US, "%.1f", tokenCount / elapsedSec)
                                        mainHandler.post {
                                            assistantBubble.senderTag.text = "GLYPH • $tps tok/s"
                                            assistantBubble.latestRawText = snapshot
                                            assistantBubble.streamTextView.text = snapshot
                                            scrollToBottom()
                                        }
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                    }
                    conn.disconnect()
                }

                val finalResponse = fullReply.toString()
                val totalSec = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000.0).coerceAtLeast(0.1)
                val finalTps = String.format(Locale.US, "%.1f", tokenCount / totalSec)

                if (finalResponse.isNotEmpty()) {
                    conversationHistory.add("assistant" to finalResponse)
                    persistActiveSession()
                    mainHandler.post {
                        assistantBubble.senderTag.text = "GLYPH • $finalTps tok/s ($tokenCount tok)"
                        assistantBubble.latestRawText = finalResponse
                        renderFormattedContent(assistantBubble.contentBox, finalResponse)
                        scrollToBottom()
                    }
                }
            } catch (e: Exception) {
                val errMsg = if (nativeMode) {
                    "Inference error: ${e.message}"
                } else {
                    "Connection failed. Make sure llama-server is running at $activeUrl."
                }
                mainHandler.post {
                    assistantBubble.latestRawText = errMsg
                    assistantBubble.streamTextView.text = errMsg
                }
            } finally {
                setKeepAliveService(false)
                mainHandler.post {
                    isGenerating = false
                    sendButton.text = "Send"
                    scrollToBottom()
                }
            }
        }
    }
}
