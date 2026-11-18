package com.veltusstudio.glyph

class LlamaBridge {

    interface TokenCallback {
        fun onToken(token: String)
    }

    var isLoaded: Boolean = false
        private set

    var loadedModelPath: String = ""
        private set

    var loadedModelName: String = ""
        private set

    var loadedThreads: Int = 4
        private set

    var loadedCtxSize: Int = 2048
        private set

    companion object {
        init {
            System.loadLibrary("llama_jni")
        }
    }

    private external fun nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int): Boolean
    private external fun nativeGenerate(
        roles: Array<String>,
        contents: Array<String>,
        temperature: Float,
        maxTokens: Int,
        callback: TokenCallback
    )
    private external fun nativeStop()
    private external fun nativeUnload()

    fun loadModel(path: String, displayName: String, threads: Int = 4, ctxSize: Int = 2048): Boolean {
        val ok = nativeLoadModel(path, threads, ctxSize)
        isLoaded = ok
        if (ok) {
            loadedModelPath = path
            loadedModelName = displayName
            loadedThreads = threads
            loadedCtxSize = ctxSize
        } else {
            loadedModelPath = ""
            loadedModelName = ""
        }
        return ok
    }

    fun generate(
        messages: List<Pair<String, String>>,
        temperature: Float,
        maxTokens: Int,
        onTokenReceived: (String) -> Unit
    ) {
        if (!isLoaded) return
        val roles = messages.map { it.first }.toTypedArray()
        val contents = messages.map { it.second }.toTypedArray()
        nativeGenerate(roles, contents, temperature, maxTokens, object : TokenCallback {
            override fun onToken(token: String) {
                onTokenReceived(token)
            }
        })
    }

    fun stop() = nativeStop()

    fun unload() {
        nativeUnload()
        isLoaded = false
        loadedModelPath = ""
        loadedModelName = ""
    }
}
