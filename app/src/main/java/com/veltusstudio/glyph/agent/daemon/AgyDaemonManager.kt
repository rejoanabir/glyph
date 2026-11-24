package com.veltusstudio.glyph.agent.daemon

import android.content.Context
import android.util.Log
import com.veltusstudio.glyph.agent.core.AgyCsrfManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

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

    private val _statusMessage = MutableStateFlow("CHECKING HUB STATUS")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var bridgeProcess: Process? = null

    /**
     * Non-blocking socket probe to check if a TCP port is open.
     */
    fun isPortOpen(port: Int, timeoutMs: Int = 800): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves and extracts native ARM64 binaries (agy and agy_ide_bridge) into app files.
     */
    private fun ensureBinariesInstalled(): Pair<File?, File?> {
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val agyBin = File(binDir, "agy")
        val bridgeBin = File(binDir, "agy_ide_bridge")

        val agyCandidates = listOf(
            File("/storage/emulated/0/.antigravity_bin/agy"),
            File("/storage/emulated/0/Download/agy"),
            File("/storage/emulated/0/CodeOnTheGoProjects/agy"),
            File("/data/user/0/ai.antigem/files/home/.local/bin/agy"),
            File("/data/data/com.termux/files/home/.local/bin/agy")
        )

        val bridgeCandidates = listOf(
            File("/storage/emulated/0/.antigravity_bin/agy_ide_bridge"),
            File("/storage/emulated/0/Download/agy_ide_bridge"),
            File("/data/user/0/ai.antigem/files/usr/bin/agy_ide_bridge")
        )

        if (!agyBin.exists() || agyBin.length() == 0L) {
            for (candidate in agyCandidates) {
                if (candidate.exists() && candidate.length() > 0) {
                    try {
                        copyFile(candidate, agyBin)
                        makeExecutable(agyBin)
                        Log.i(TAG, "Copied agy binary from ${candidate.absolutePath}")
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not copy ${candidate.absolutePath}: ${e.message}")
                    }
                }
            }
        } else {
            makeExecutable(agyBin)
        }

        if (!bridgeBin.exists() || bridgeBin.length() == 0L) {
            for (candidate in bridgeCandidates) {
                if (candidate.exists() && candidate.length() > 0) {
                    try {
                        copyFile(candidate, bridgeBin)
                        makeExecutable(bridgeBin)
                        Log.i(TAG, "Copied bridge binary from ${candidate.absolutePath}")
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not copy ${candidate.absolutePath}: ${e.message}")
                    }
                }
            }
        } else {
            makeExecutable(bridgeBin)
        }

        return Pair(
            if (agyBin.exists() && agyBin.canExecute()) agyBin else null,
            if (bridgeBin.exists() && bridgeBin.canExecute()) bridgeBin else null
        )
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { inStream ->
            FileOutputStream(dst).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun makeExecutable(file: File) {
        file.setReadable(true, false)
        file.setExecutable(true, false)
        try {
            Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor()
        } catch (_: Exception) {}
    }

    /**
     * Starts or connects to the local Antigravity daemon & bridge.
     */
    fun startOrConnectDaemon(workspaceDir: String = context.filesDir.absolutePath) {
        scope.launch {
            _daemonState.value = DaemonState.STARTING
            _statusMessage.value = "CONNECTING TO HUB"

            // 1. Check if ports are already listening
            val hubOpen = isPortOpen(DEFAULT_HUB_PORT, 600)
            val bridgeOpen = isPortOpen(DEFAULT_BRIDGE_PORT, 600)

            if (hubOpen) {
                Log.d(TAG, "Hub port $DEFAULT_HUB_PORT is open and listening")
                if (bridgeOpen) {
                    AgyCsrfManager.instance.refreshFromBridge(DEFAULT_BRIDGE_URL)
                }
                _daemonState.value = DaemonState.RUNNING
                _statusMessage.value = "ONLINE · HUB :$DEFAULT_HUB_PORT"
                return@launch
            }

            // 2. Prepare binaries
            val (agyBin, bridgeBin) = ensureBinariesInstalled()

            if (bridgeBin == null || agyBin == null) {
                _daemonState.value = DaemonState.ERROR
                _statusMessage.value = "DAEMON OFFLINE · TAP TO START"
                return@launch
            }

            // 3. Spawn bridge daemon
            val token = AgyCsrfManager.instance.securityToken.ifBlank {
                val gen = AgyCsrfManager.generateRandomToken()
                AgyCsrfManager.instance.setSecurityToken(gen)
                gen
            }

            val cmd = listOf(
                bridgeBin.absolutePath,
                "-f",
                "--token", token,
                "-p", DEFAULT_BRIDGE_PORT.toString(),
                "--hub-port", DEFAULT_HUB_PORT.toString(),
                "-d", workspaceDir,
                "--bin", agyBin.absolutePath,
                "--no-claude",
                "--no-opencode",
                "--no-codex"
            )

            try {
                Log.d(TAG, "Spawning daemon: ${cmd.joinToString(" ")}")
                val pb = ProcessBuilder(cmd)
                pb.directory(context.filesDir)
                pb.environment()["HOME"] = File(context.filesDir, "home").apply { mkdirs() }.absolutePath
                pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
                pb.redirectErrorStream(true)

                bridgeProcess = pb.start()

                var online = false
                for (i in 1..25) {
                    delay(500)
                    if (isPortOpen(DEFAULT_HUB_PORT, 500)) {
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
                    _statusMessage.value = "STARTUP TIMED OUT"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Bridge spawn failure", e)
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
