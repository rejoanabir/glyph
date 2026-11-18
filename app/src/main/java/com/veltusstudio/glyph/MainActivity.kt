package com.veltusstudio.glyph

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val bgObsidian = Color.parseColor("#080B10")
    private val surfaceCard = Color.parseColor("#0F151F")
    private val codeBlockBg = Color.parseColor("#06090E")
    private val userBubbleBg = Color.parseColor("#132033")
    private val borderSubtle = Color.parseColor("#1C2636")
    private val accentCyan = Color.parseColor("#00D2FF")
    private val textPrimary = Color.parseColor("#F1F5F9")
    private val textMuted = Color.parseColor("#94A3B8")

    private val defaultSystemPrompt =
        "You are Glyph, a local-first AI assistant crafted by Veltus Studio, running offline on a local Qwen model. Never claim to be made by Anthropic, OpenAI, or Google. Be concise, helpful, and direct."

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var emptySubLabel: TextView
    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var emptyState: LinearLayout
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button

    private var isGenerating = false

    private fun prefs() = getSharedPreferences("glyph_prefs", Context.MODE_PRIVATE)
    private fun getServerUrl(): String = prefs().getString("server_url", "http://127.0.0.1:8080") ?: "http://127.0.0.1:8080"
    private fun getSystemPrompt(): String = prefs().getString("system_prompt", defaultSystemPrompt) ?: defaultSystemPrompt
    private fun getTemperature(): Float = prefs().getFloat("temperature", 0.7f)
    private fun getMaxTokens(): Int = prefs().getInt("max_tokens", 1024)

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

            checkServerHealth()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedBox(fillColor: Int, strokeColor: Int, radiusDp: Int, strokeDp: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
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
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }

        val subtitleText = TextView(this).apply {
            text = "by Veltus Studio"
            setTextColor(textMuted)
            textSize = 12f
        }

        titleCol.addView(titleText)
        titleCol.addView(subtitleText)

        // Reset Chat Pill
        val resetBtn = TextView(this).apply {
            text = "Reset"
            setTextColor(textMuted)
            textSize = 11f
            background = roundedBox(surfaceCard, borderSubtle, 50)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { clearChat() }
        }

        // Settings Pill
        val settingsBtn = TextView(this).apply {
            text = "⚙"
            setTextColor(textPrimary)
            textSize = 12f
            background = roundedBox(surfaceCard, borderSubtle, 50)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { openSettingsDialog() }
        }

        // Status Pill
        val statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox(surfaceCard, borderSubtle, 50)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(6)
            }
            background = roundedBox(Color.parseColor("#EF4444"), Color.TRANSPARENT, 50, 0)
        }

        statusLabel = TextView(this).apply {
            text = "Checking..."
            setTextColor(textMuted)
            textSize = 11f
        }

        statusPill.addView(statusDot)
        statusPill.addView(statusLabel)
        statusPill.setOnClickListener { checkServerHealth() }

        header.addView(titleCol)
        header.addView(resetBtn)
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
                topMargin = dp(16)
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
            setPadding(dp(16), dp(64), dp(16), dp(16))
        }

        val emptyTitle = TextView(this).apply {
            text = "Local-First Intelligence"
            setTextColor(accentCyan)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        emptySubLabel = TextView(this).apply {
            text = "Endpoint: ${getServerUrl()}\nMulti-turn memory active • Tap ⚙ to configure"
            setTextColor(textMuted)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        emptyState.addView(emptyTitle)
        emptyState.addView(emptySubLabel)
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

        // 4. Input Bar
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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
                marginEnd = dp(8)
            }
        }

        sendButton = Button(this).apply {
            text = "Send"
            setTextColor(bgObsidian)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = roundedBox(accentCyan, Color.TRANSPARENT, 12, 0)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener { sendMessage() }
        }

        inputRow.addView(inputField)
        inputRow.addView(sendButton)
        root.addView(inputRow)

        return root
    }

    private fun clearChat() {
        if (isGenerating) {
            Toast.makeText(this, "Wait for response to finish", Toast.LENGTH_SHORT).show()
            return
        }
        conversationHistory.clear()
        messageContainer.removeAllViews()
        emptySubLabel.text = "Endpoint: ${getServerUrl()}\nMulti-turn memory active • Tap ⚙ to configure"
        emptyState.visibility = View.VISIBLE
        messageContainer.addView(emptyState)
        Toast.makeText(this, "Conversation cleared", Toast.LENGTH_SHORT).show()
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
            setPadding(0, dp(10), 0, dp(4))
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

        val urlInput = makeInput(getServerUrl())
        val promptInput = makeInput(getSystemPrompt(), multiline = true)
        val tempInput = makeInput(getTemperature().toString(), numeric = true)
        val tokensInput = makeInput(getMaxTokens().toString(), numeric = true)

        container.addView(makeLabel("LLAMA-SERVER ENDPOINT"))
        container.addView(urlInput)
        container.addView(makeLabel("SYSTEM PROMPT"))
        container.addView(promptInput)
        container.addView(makeLabel("TEMPERATURE (0.0 – 1.5)"))
        container.addView(tempInput)
        container.addView(makeLabel("MAX TOKENS"))
        container.addView(tokensInput)

        val scroll = ScrollView(this).apply { addView(container) }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Glyph Settings")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val cleanUrl = urlInput.text.toString().trim().removeSuffix("/").ifEmpty { "http://127.0.0.1:8080" }
                val cleanPrompt = promptInput.text.toString().trim().ifEmpty { defaultSystemPrompt }
                val cleanTemp = tempInput.text.toString().toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f
                val cleanTokens = tokensInput.text.toString().toIntOrNull()?.coerceIn(64, 8192) ?: 1024

                prefs().edit()
                    .putString("server_url", cleanUrl)
                    .putString("system_prompt", cleanPrompt)
                    .putFloat("temperature", cleanTemp)
                    .putInt("max_tokens", cleanTokens)
                    .apply()

                emptySubLabel.text = "Endpoint: $cleanUrl\nMulti-turn memory active • Tap ⚙ to configure"
                checkServerHealth()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Defaults") { _, _ ->
                prefs().edit().clear().apply()
                emptySubLabel.text = "Endpoint: ${getServerUrl()}\nMulti-turn memory active • Tap ⚙ to configure"
                checkServerHealth()
                Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawable(roundedBox(bgObsidian, borderSubtle, 16))
    }

    private fun checkServerHealth() {
        val currentUrl = getServerUrl()
        statusLabel.text = "Checking..."
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
                val dotColor = if (online) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
                statusDot.background = roundedBox(dotColor, Color.TRANSPARENT, 50, 0)
                statusLabel.text = if (online) "Online" else "Offline"
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
        val parts = rawText.split("```")
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

        val holder = BubbleHolder(contentBox, streamTextView, initialText)

        val copyMsgBtn = TextView(this).apply {
            text = "Copy"
            setTextColor(textMuted)
            textSize = 10f
            setPadding(dp(8), dp(2), dp(2), dp(2))
            setOnClickListener { copyToClipboard("Message", holder.latestRawText, this) }
        }

        headerRow.addView(senderTag)
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

    private fun scrollToBottom() {
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val prompt = inputField.text.toString().trim()
        if (prompt.isEmpty() || isGenerating) return

        val activeUrl = getServerUrl()
        val activeSystemPrompt = getSystemPrompt()
        val activeTemp = getTemperature().toDouble()
        val activeMaxTokens = getMaxTokens()

        inputField.setText("")
        isGenerating = true
        sendButton.text = "..."
        sendButton.isEnabled = false

        addMessageBubble(isUser = true, initialText = prompt)
        conversationHistory.add("user" to prompt)

        val assistantBubble = addMessageBubble(isUser = false, initialText = "...")
        val fullReply = StringBuilder()

        executor.execute {
            try {
                val conn = (URL("$activeUrl/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "text/event-stream")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 0
                }

                val messagesArray = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", activeSystemPrompt))
                    // Keep the most recent 16 turns so local context stays fast and within window limits
                    val recentTurns = conversationHistory.takeLast(16)
                    for ((role, content) in recentTurns) {
                        put(JSONObject().put("role", role).put("content", content))
                    }
                }

                val payload = JSONObject().apply {
                    put("stream", true)
                    put("temperature", activeTemp)
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
                                    fullReply.append(token)
                                    val snapshot = fullReply.toString()
                                    mainHandler.post {
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

                val finalResponse = fullReply.toString()
                if (finalResponse.isNotEmpty()) {
                    conversationHistory.add("assistant" to finalResponse)
                    mainHandler.post {
                        assistantBubble.latestRawText = finalResponse
                        renderFormattedContent(assistantBubble.contentBox, finalResponse)
                        scrollToBottom()
                    }
                }
            } catch (_: Exception) {
                val errMsg = "Connection failed. Make sure llama-server is running at $activeUrl."
                mainHandler.post {
                    assistantBubble.latestRawText = errMsg
                    assistantBubble.streamTextView.text = errMsg
                }
            } finally {
                mainHandler.post {
                    isGenerating = false
                    sendButton.text = "Send"
                    sendButton.isEnabled = true
                    scrollToBottom()
                }
            }
        }
    }
}
