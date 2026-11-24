package com.veltusstudio.glyph.agent.daemon

import android.content.Context
import android.util.Log
import com.veltusstudio.glyph.agent.core.AgyCsrfManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class DaemonState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

class AgyDaemonManager(private val context: Context) {

    companion object {
        private const val TAG = "AgyDaemonManager"
        const val DEFAULT_BRIDGE_PORT = 1236
        const val DEFAULT_HUB_PORT = 1237
        const val DEFAULT_BRIDGE_URL = "http://127.0.0.1:$DEFAULT_BRIDGE_PORT"
        const val DEFAULT_HUB_URL = "http://127.0.0.1:$DEFAULT_HUB_PORT"

        @Volatile
        private var instance: AgyDaemonManager? = null

        fun getInstance(context: Context): AgyDaemonManager {
            return instance ?: synchronized(this) {
                instance ?: AgyDaemonManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _daemonState = MutableStateFlow(DaemonState.STOPPED)
    val daemonState: StateFlow<DaemonState> = _daemonState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Agent daemon idle")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var bridgeProcess: Process? = null

    /**
     * Finds the candidate path for the `agy` ARM64 binary.
     */
    fun findAgyBinaryPath(): String? {
        val candidates = listOf(
            File(context.filesDir, "bin/agy").absolutePath,
            File(context.filesDir, "home/.local/bin/agy").absolutePath,
            "/data/user/0/ai.antigem/files/home/.local/bin/agy",
            "/data/data/com.termux/files/home/.local/bin/agy",
            System.getenv("HOME")?.let { "$it/.local/bin/agy" }
        ).filterNotNull()

        for (path in candidates) {
            val f = File(path)
            if (f.exists() && (f.canExecute() || f.setExecutable(true))) {
                return f.absolutePath
            }
        }
        return null
    }

    /**
     * Checks if a daemon is already alive on the local port.
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$DEFAULT_BRIDGE_URL/api/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts or connects to the local Antigravity daemon & bridge.
     */
    fun startOrConnectDaemon(workspaceDir: String = context.filesDir.absolutePath) {
        scope.launch {
            _daemonState.value = DaemonState.STARTING
            _statusMessage.value = "Checking daemon connection..."

            if (checkHealth()) {
                Log.d(TAG, "Local bridge is already active on port $DEFAULT_BRIDGE_PORT")
                _daemonState.value = DaemonState.RUNNING
                _statusMessage.value = "Connected to Antigravity Hub"
                syncTokenFromBridge()
                return@launch
            }

            // Ensure bin directory exists
            val binDir = File(context.filesDir, "bin").apply { mkdirs() }
            val bridgeBin = File(binDir, "agy_ide_bridge")

            // If bridge binary not yet installed, check shared locations
            if (!bridgeBin.exists()) {
                val sharedBridge = File("/data/user/0/ai.antigem/files/usr/bin/agy_ide_bridge")
                if (sharedBridge.exists()) {
                    try {
                        sharedBridge.copyTo(bridgeBin, overwrite = true)
                        bridgeBin.setExecutable(true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not copy shared bridge: ${e.message}")
                    }
                }
            }

            val agyPath = findAgyBinaryPath()
            if (agyPath == null && !bridgeBin.exists()) {
                _daemonState.value = DaemonState.ERROR
                _statusMessage.value = "Antigravity binary (agy) not found. Please install it via Termux."
                return@launch
            }

            val token = AgyCsrfManager.generateRandomToken()
            AgyCsrfManager.instance.setToken(token)

            val cmd = mutableListOf<String>()
            val execBin = if (bridgeBin.exists()) bridgeBin.absolutePath else "/data/user/0/ai.antigem/files/usr/bin/agy_ide_bridge"
            cmd.add(execBin)
            cmd.add("-f")
            cmd.add("--token")
            cmd.add(token)
            cmd.add("-p")
            cmd.add(DEFAULT_BRIDGE_PORT.toString())
            cmd.add("--hub-port")
            cmd.add(DEFAULT_HUB_PORT.toString())
            cmd.add("-d")
            cmd.add(workspaceDir)

            agyPath?.let {
                cmd.add("--bin")
                cmd.add(it)
            }

            cmd.add("--no-claude")
            cmd.add("--no-opencode")
            cmd.add("--no-codex")

            try {
                Log.d(TAG, "Spawning bridge daemon: ${cmd.joinToString(" ")}")
                val pb = ProcessBuilder(cmd)
                pb.directory(context.filesDir)
                pb.environment()["HOME"] = File(context.filesDir, "home").apply { mkdirs() }.absolutePath
                pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
                pb.redirectErrorStream(true)

                bridgeProcess = pb.start()

                // Poll health for 10 seconds
                var online = false
                for (i in 1..20) {
                    delay(500)
                    if (checkHealth()) {
                        online = true
                        break
                    }
                }

                if (online) {
                    _daemonState.value = DaemonState.RUNNING
                    _statusMessage.value = "Antigravity Engine Online (:1237)"
                    Log.i(TAG, "Bridge and Antigravity Hub successfully started!")
                } else {
                    _daemonState.value = DaemonState.ERROR
                    _statusMessage.value = "Daemon startup timed out"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start daemon", e)
                _daemonState.value = DaemonState.ERROR
                _statusMessage.value = "Daemon error: ${e.localizedMessage}"
            }
        }
    }

    private suspend fun syncTokenFromBridge() = withContext(Dispatchers.IO) {
        try {
            val tokenFile = File("/data/user/0/ai.antigem/files/home/.antigem_bridge/csrf_token.txt")
            if (tokenFile.exists()) {
                val t = tokenFile.readText().trim()
                if (t.isNotBlank()) {
                    AgyCsrfManager.instance.setToken(t)
                }
            }
        } catch (_: Exception) {}
    }

    fun stopDaemon() {
        bridgeProcess?.destroy()
        bridgeProcess = null
        _daemonState.value = DaemonState.STOPPED
        _statusMessage.value = "Daemon stopped"
    }
}
