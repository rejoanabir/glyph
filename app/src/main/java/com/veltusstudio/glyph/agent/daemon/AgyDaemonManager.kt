package com.veltusstudio.glyph.agent.daemon

import android.content.Context
import android.util.Log
import com.veltusstudio.glyph.agent.core.AgyCsrfManager
import com.veltusstudio.glyph.agent.core.AgyOkHttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import java.io.File

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
     * Checks if a candidate path for the `agy` ARM64 binary exists.
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
     * Checks if the bridge supervisor is alive on port 1236 using HTTP/1.1.
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$DEFAULT_BRIDGE_URL/api/health")
                .get()
                .build()
            AgyOkHttpClient.bridgeClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
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
            _statusMessage.value = "CONNECTING TO HUB"

            if (checkHealth()) {
                Log.d(TAG, "Bridge is active on port $DEFAULT_BRIDGE_PORT")
                val csrf = AgyCsrfManager.instance.refreshFromBridge(DEFAULT_BRIDGE_URL)
                _daemonState.value = DaemonState.RUNNING
                _statusMessage.value = "CONNECTED · HUB :$DEFAULT_HUB_PORT"
                return@launch
            }

            // Ensure bin directory exists
            val binDir = File(context.filesDir, "bin").apply { mkdirs() }
            val bridgeBin = File(binDir, "agy_ide_bridge")

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
                _statusMessage.value = "ANTIGRAVITY BINARY NOT FOUND"
                return@launch
            }

            val token = AgyCsrfManager.instance.securityToken.ifBlank {
                val gen = AgyCsrfManager.generateRandomToken()
                AgyCsrfManager.instance.setSecurityToken(gen)
                gen
            }

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
                Log.d(TAG, "Starting bridge process: ${cmd.joinToString(" ")}")
                val pb = ProcessBuilder(cmd)
                pb.directory(context.filesDir)
                pb.environment()["HOME"] = File(context.filesDir, "home").apply { mkdirs() }.absolutePath
                pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
                pb.redirectErrorStream(true)

                bridgeProcess = pb.start()

                var online = false
                for (i in 1..25) {
                    delay(400)
                    if (checkHealth()) {
                        online = true
                        break
                    }
                }

                if (online) {
                    AgyCsrfManager.instance.refreshFromBridge(DEFAULT_BRIDGE_URL)
                    _daemonState.value = DaemonState.RUNNING
                    _statusMessage.value = "ONLINE · HUB :$DEFAULT_HUB_PORT"
                } else {
                    _daemonState.value = DaemonState.ERROR
                    _statusMessage.value = "STARTUP TIMEOUT"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start bridge daemon", e)
                _daemonState.value = DaemonState.ERROR
                _statusMessage.value = "ERROR: ${e.localizedMessage}"
            }
        }
    }

    fun stopDaemon() {
        bridgeProcess?.destroy()
        bridgeProcess = null
        _daemonState.value = DaemonState.STOPPED
        _statusMessage.value = "DAEMON STOPPED"
    }
}
