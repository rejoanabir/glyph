package com.veltusstudio.glyph

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import java.util.Locale
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
    private val starterModelName = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    private val starterModelUrl =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"

    private val defaultSystemPrompt =
        "You are Glyph, a standalone local-first AI assistant crafted by Veltus Studio, running offline on a local Qwen model. Never claim to be made by Anthropic, OpenAI, or Google. Be concise, helpful, and direct."

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val llamaBridge = LlamaBridge()

    private lateinit var subtitleText: TextView
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var emptySubLabel: TextView
    private lateinit var downloadBtn: TextView
    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var emptyState: LinearLayout
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button

    private var isGenerating = false
    private var isBusyModel = false

    private fun prefs() = getSharedPreferences("glyph_prefs", Context.MODE_PRIVATE)
    private fun isNativeMode(): Boolean = prefs().getBoolean("native_mode", true)
    private fun getSavedModelPath(): String = prefs().getString("model_path", "") ?: ""
    private fun getSavedModelName(): String = prefs().getString("model_name", "") ?: ""
    private fun getServerUrl(): String = prefs().getString("server_url", "http://127.0.0.1:8080") ?: "http://127.0.0.1:8080"
    private fun getSystemPrompt(): String = prefs().getString("system_prompt", defaultSystemPrompt) ?: defaultSystemPrompt
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
                subtitleText.text = "$targetName • ${desiredCtx} ctx"
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

    private fun downloadStarterModel() {
        if (isBusyModel || isGenerating) return
        isBusyModel = true
        setStatus("Downloading...", "#F59E0B")
        downloadBtn.isEnabled = false

        executor.execute {
            try {
                val modelsDir = File(filesDir, "models").apply { mkdirs() }
                val destFile = File(modelsDir, starterModelName)
                val tempFile = File(modelsDir, "$starterModelName.part")

                val conn = (URL(starterModelUrl).openConnection() as HttpURLConnection).apply {
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
                                        downloadBtn.text = "Downloading... $pct%"
                                        emptySubLabel.text = "Downloading Qwen 2.5 1.5B ($pct%)..."
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
                    downloadBtn.text = "Download Qwen 1.5B (1.1 GB)"
                    loadNativeModelAsync(destFile.absolutePath, starterModelName)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isBusyModel = false
                    downloadBtn.isEnabled = true
                    downloadBtn.text = "Retry Download Qwen 1.5B"
                    setStatus("Download Failed", "#EF4444")
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
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }

        subtitleText = TextView(this).apply {
            text = "by Veltus Studio"
            setTextColor(textMuted)
            textSize = 11f
            maxLines = 1
        }

        titleCol.addView(titleText)
        titleCol.addView(subtitleText)

        // New Chat Pill
        val newChatBtn = TextView(this).apply {
            text = "+ New"
            setTextColor(textPrimary)
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

        // Settings & Model Manager Pill
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
            background = roundedBox(Color.parseColor("#F59E0B"), Color.TRANSPARENT, 50, 0)
        }

        statusLabel = TextView(this).apply {
            text = "Init..."
            setTextColor(textMuted)
            textSize = 11f
        }

        statusPill.addView(statusDot)
        statusPill.addView(statusLabel)
        statusPill.setOnClickListener {
            if (isNativeMode()) openSettingsDialog() else checkServerHealth()
        }

        header.addView(titleCol)
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
            setPadding(dp(16), dp(38), dp(16), dp(16))
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
            text = "Download Qwen 1.5B (1.1 GB)"
            setTextColor(textPrimary)
            textSize = 12f
            gravity = Gravity.CENTER
            background = roundedBox(surfaceCard, borderSubtle, 12)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { downloadStarterModel() }
        }

        emptyState.addView(emptyTitle)
        emptyState.addView(emptySubLabel)
        emptyState.addView(pickBtn)
        emptyState.addView(downloadBtn)
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
            setOnClickListener {
                if (isGenerating) {
                    llamaBridge.stop()
                } else {
                    sendMessage()
                }
            }
        }

        inputRow.addView(inputField)
        inputRow.addView(sendButton)
        root.addView(inputRow)

        return root
    }

    private fun clearChat() {
        if (isGenerating) {
            llamaBridge.stop()
            return
        }
        conversationHistory.clear()
        messageContainer.removeAllViews()
        emptyState.visibility = View.VISIBLE
        messageContainer.addView(emptyState)
        Toast.makeText(this, "New conversation started", Toast.LENGTH_SHORT).show()
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
            setPadding(0, dp(12), 0, dp(6))
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
        val modelsListBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun refreshModelList() {
            modelsListBox.removeAllViews()
            val modelsDir = File(filesDir, "models").apply { mkdirs() }
            val ggufFiles = modelsDir.listFiles()?.filter { it.name.endsWith(".gguf") }?.sortedBy { it.name } ?: emptyList()

            if (ggufFiles.isEmpty()) {
                modelsListBox.addView(TextView(this).apply {
                    text = "No GGUF models imported yet."
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
                text = "+ Import .gguf File from Storage"
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

        val urlInput = makeInput(getServerUrl())
        val promptInput = makeInput(getSystemPrompt(), multiline = true)
        val tempInput = makeInput(getTemperature().toString(), numeric = true)
        val tokensInput = makeInput(getMaxTokens().toString(), numeric = true)

        container.addView(makeLabel("SYSTEM PROMPT"))
        container.addView(promptInput)
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
                val cleanPrompt = promptInput.text.toString().trim().ifEmpty { defaultSystemPrompt }
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
                    setStatus("Server Online", "#10B981")
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
        val holder = BubbleHolder(senderTag, contentBox, streamTextView, initialText)

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

    private fun buildContextWindowMessages(systemPrompt: String): List<Pair<String, String>> {
        // Reserve ~40% of context budget for prompt history (~3.5 chars/token) so KV cache never overflows
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

        val activeUrl = getServerUrl()
        val activeSystemPrompt = getSystemPrompt()
        val activeTemp = getTemperature()
        val activeMaxTokens = getMaxTokens()

        inputField.setText("")
        isGenerating = true
        sendButton.text = "Stop"

        addMessageBubble(isUser = true, initialText = prompt)
        conversationHistory.add("user" to prompt)

        val assistantBubble = addMessageBubble(isUser = false, initialText = "...")
        val fullReply = StringBuilder()
        var tokenCount = 0
        val startTimeMs = SystemClock.elapsedRealtime()

        executor.execute {
            try {
                val contextMessages = buildContextWindowMessages(activeSystemPrompt)

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
                mainHandler.post {
                    isGenerating = false
                    sendButton.text = "Send"
                    scrollToBottom()
                }
            }
        }
    }
}
