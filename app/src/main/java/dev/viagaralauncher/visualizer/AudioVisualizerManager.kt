// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.visualizer

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow


/**
 * Visualizer spectrum frame containing 14 smooth frequency bands and floating peak positions.
 */
data class VisualizerFrame(
    val bands: FloatArray = FloatArray(14),
    val peaks: FloatArray = FloatArray(14),
    val rms: Float = 0f,
    val hasAudio: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VisualizerFrame
        if (!bands.contentEquals(other.bands)) return false
        if (!peaks.contentEquals(other.peaks)) return false
        if (rms != other.rms) return false
        if (hasAudio != other.hasAudio) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bands.contentHashCode()
        result = 31 * result + peaks.contentHashCode()
        result = 31 * result + rms.hashCode()
        result = 31 * result + hasAudio.hashCode()
        return result
    }
}

private enum class VisualizerState {
    STOPPED,
    STARTING,
    RUNNING,
    PAUSED,
    STOPPING,
}

/**
 * Manages the lifecycle of the root audio visualizer daemon:
 * - State machine: STOPPED -> STARTING -> RUNNING <-> PAUSED -> STOPPING -> STOPPED
 * - Mutex-guarded lifecycle to eliminate race conditions between start() and stop()
 * - Sends 'P' (Pause) / 'R' (Resume) over LocalSocket to prevent CPU & IPC queue accumulation
 * - Sends 'S' (Stop) for graceful termination
 * - Specifically tracks daemon PID and kills only that PID with SIGTERM (fallback SIGKILL)
 * - Zero CPU / memory consumption when disabled.
 */
object AudioVisualizerManager {

    private const val SOCKET_NAME = "viagara_audio_viz"
    private const val NUM_BANDS = 14

    private val _frameFlow = MutableStateFlow(VisualizerFrame())
    val frameFlow: StateFlow<VisualizerFrame> = _frameFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val lifecycleMutex = Mutex()
    private var currentState = VisualizerState.STOPPED
    private var currentGeneration = 0L

    private var supervisorJob: Job? = null
    private var readerJob: Job? = null

    private var serverSocket: LocalServerSocket? = null
    private var clientSocket: LocalSocket? = null
    private var clientOut: OutputStream? = null

    private var daemonProcess: Process? = null
    private var daemonPid: Int = -1

    // Ballistic physics state ported from SDWMP3_CN
    private val currentLevels = FloatArray(NUM_BANDS)
    private val currentPeaks = FloatArray(NUM_BANDS)

    // Equal-loudness spectral tilt compensation (Pink noise 1/f acoustic curve)
    // Organic, gentle acoustic profile avoiding hypersensitivity across mids, highs and sub-bass
    private val SPECTRAL_WEIGHTS = floatArrayOf(
        0.80f, // Band 0:  25 - 65 Hz   (Deep Sub / Subgrave)
        0.82f, // Band 1:  65 - 115 Hz  (Kick Drum / Bumbo)
        0.85f, // Band 2:  115 - 175 Hz (Punch / Ataque)
        0.88f, // Band 3:  175 - 260 Hz (Bassline / Contrabaixo)
        0.90f, // Band 4:  260 - 390 Hz (Corpo / Warmth)
        0.93f, // Band 5:  390 - 580 Hz (Caixa / Voz baixa)
        0.96f, // Band 6:  580 - 870 Hz (Medios / Harmônicos vocais)
        0.99f, // Band 7:  870 - 1300 Hz (Clareza vocal / Sintetizadores)
        1.02f, // Band 8:  1300 - 2000 Hz (Presença vocal / Ataque melódico)
        1.05f, // Band 9:  2000 - 3200 Hz (Articulação vocal / Estalo)
        1.08f, // Band 10: 3200 - 5000 Hz (Agudos / Pratos de ataque)
        1.10f, // Band 11: 5000 - 8000 Hz (Pratos de condução / Ride)
        1.12f, // Band 12: 8000 - 12500 Hz (Chimbal / Hi-hats)
        1.12f, // Band 13: 12500 - 18500 Hz (Ar / Ambiência cristalina)
    )
    @Volatile
    private var currentSpeed: Float = 0.50f

    @Volatile
    private var isLauncherForeground = true
    @Volatile
    private var lastEnabledSetting = false
    @Volatile
    private var lastHasSession = false
    @Volatile
    private var lastIsPlaying = false
    @Volatile
    private var appContext: Context? = null
    private var stopJob: Job? = null

    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.10f, 1.00f)
    }

    fun updatePlaybackState(context: Context, enabled: Boolean, isPlaying: Boolean, hasSession: Boolean) {
        appContext = context.applicationContext
        lastEnabledSetting = enabled
        lastHasSession = hasSession
        lastIsPlaying = isPlaying

        scope.launch {
            evaluateStateAndAct()
        }
    }

    private suspend fun evaluateStateAndAct() {
        val shouldBeRunning = isLauncherForeground && lastEnabledSetting && lastHasSession && lastIsPlaying
        val isPausedSession = isLauncherForeground && lastEnabledSetting && lastHasSession && !lastIsPlaying

        if (shouldBeRunning) {
            stopJob?.cancel()
            stopJob = null
            if (currentState == VisualizerState.PAUSED) {
                resumeInternal()
            } else if (currentState == VisualizerState.STOPPED) {
                appContext?.let { startInternal(it) }
            }
        } else if (isPausedSession) {
            // Pausa a captura do visualizador imediatamente com zero CPU, aguardando até 30s antes de encerrar o daemon
            if (currentState == VisualizerState.RUNNING) {
                pauseInternal()
            }
            if (currentState != VisualizerState.STOPPED && stopJob == null) {
                stopJob = scope.launch {
                    delay(30_000L)
                    stopInternal()
                    stopJob = null
                }
            }
        } else {
            stopJob?.cancel()
            stopJob = null
            if (!isLauncherForeground && currentState == VisualizerState.RUNNING) {
                pauseInternal()
            } else if (!lastEnabledSetting || !lastHasSession) {
                stopInternal()
            }
        }
    }

    private suspend fun pauseInternal() {
        lifecycleMutex.withLock {
            if (currentState == VisualizerState.RUNNING) {
                currentState = VisualizerState.PAUSED
                sendControlCommand('P')
                withContext(Dispatchers.Default) {
                    currentLevels.fill(0f)
                    currentPeaks.fill(0f)
                    _frameFlow.value = VisualizerFrame()
                }
            }
        }
    }

    private suspend fun resumeInternal() {
        lifecycleMutex.withLock {
            if (currentState == VisualizerState.PAUSED) {
                currentState = VisualizerState.RUNNING
                sendControlCommand('R')
            }
        }
    }

    fun start(context: Context) {
        scope.launch {
            startInternal(context)
        }
    }

    private suspend fun startInternal(context: Context) {
        lifecycleMutex.withLock {
            if (currentState != VisualizerState.STOPPED) {
                return@withLock
            }
            currentState = VisualizerState.STARTING
            val gen = ++currentGeneration

            supervisorJob = scope.launch {
                runSupervisor(context.applicationContext, gen)
            }
        }
    }

    fun stop() {
        scope.launch {
            stopInternal()
        }
    }

    private suspend fun stopInternal() {
        lifecycleMutex.withLock {
            if (currentState == VisualizerState.STOPPED || currentState == VisualizerState.STOPPING) {
                return@withLock
            }
            currentState = VisualizerState.STOPPING
            val gen = currentGeneration

            supervisorJob?.cancel()
            supervisorJob = null

            cleanupDaemonLocked(gen)

            withContext(Dispatchers.Default) {
                currentLevels.fill(0f)
                currentPeaks.fill(0f)
                _frameFlow.value = VisualizerFrame()
            }

            currentState = VisualizerState.STOPPED
        }
    }

    fun onLauncherResume(context: Context? = null) {
        isLauncherForeground = true
        if (context != null) appContext = context.applicationContext
        scope.launch {
            evaluateStateAndAct()
        }
    }

    fun onLauncherPause() {
        isLauncherForeground = false
        scope.launch {
            evaluateStateAndAct()
        }
    }

    fun killAnyOrphanDaemon() {
        scope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f dev.viagaralauncher.visualizer.AudioVisualizerDaemon || true")).waitFor()
            } catch (_: Throwable) {}
        }
    }

    private fun sendControlCommand(cmd: Char) {
        try {
            clientOut?.let {
                it.write(cmd.code)
                it.flush()
            }
        } catch (_: Throwable) {}
    }

    private suspend fun runSupervisor(context: Context, generation: Long) = withContext(Dispatchers.IO) {
        var localDaemonPid = -1
        var localProcess: Process? = null
        var localClient: LocalSocket? = null
        try {
            // 1. Open LocalServerSocket
            try {
                serverSocket = LocalServerSocket(SOCKET_NAME)
            } catch (_: Throwable) {
                serverSocket?.close()
                serverSocket = LocalServerSocket(SOCKET_NAME)
            }

            // Check generation before heavy work
            lifecycleMutex.withLock {
                if (generation != currentGeneration) {
                    return@withContext
                }
            }

            // 2. Prepare standalone DEX asset in filesDir
            val dexPath = extractOrPrepareDex(context) ?: run {
                lifecycleMutex.withLock {
                    if (generation == currentGeneration) {
                        cleanupDaemonLocked(generation)
                        currentState = VisualizerState.STOPPED
                    }
                }
                return@withContext
            }

            // 3. Start daemon via su and extract PID
            val launchScript = """
                export CLASSPATH="$dexPath"
                /system/bin/app_process64 /data/local/tmp dev.viagaralauncher.visualizer.AudioVisualizerDaemon &
                echo "PID:${'$'}!"
                wait
            """.trimIndent()

            val process = ProcessBuilder("su", "-c", launchScript).start()
            localProcess = process

            // Read the PID line emitted by the shell script
            val reader = process.inputStream.bufferedReader()
            var extractedPid = -1
            val timeoutMillis = System.currentTimeMillis() + 2000L
            while (System.currentTimeMillis() < timeoutMillis) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("PID:")) {
                        extractedPid = line.substring(4).trim().toIntOrNull() ?: -1
                        break
                    }
                } else {
                    delay(40)
                }
            }
            localDaemonPid = extractedPid

            lifecycleMutex.withLock {
                if (generation != currentGeneration || currentState != VisualizerState.STARTING) {
                    // Stale supervisor: terminate spawned process immediately without touching current state
                    terminateProcessAndPid(process, localDaemonPid)
                    return@withContext
                }
                daemonProcess = process
                daemonPid = extractedPid
            }

            // 4. Accept client socket connection
            val client = serverSocket?.accept() ?: run {
                lifecycleMutex.withLock {
                    if (generation == currentGeneration) {
                        cleanupDaemonLocked(generation)
                        currentState = VisualizerState.STOPPED
                    }
                }
                return@withContext
            }
            localClient = client

            lifecycleMutex.withLock {
                if (generation != currentGeneration || currentState != VisualizerState.STARTING) {
                    try { client.close() } catch (_: Throwable) {}
                    if (generation == currentGeneration) {
                        cleanupDaemonLocked(generation)
                    } else {
                        terminateProcessAndPid(process, localDaemonPid)
                    }
                    return@withLock
                }
                clientSocket = client
                clientOut = client.outputStream
                currentState = VisualizerState.RUNNING
            }

            startReaderLoop(client.inputStream)
        } catch (_: Throwable) {
            lifecycleMutex.withLock {
                if (generation == currentGeneration) {
                    cleanupDaemonLocked(generation)
                    currentState = VisualizerState.STOPPED
                } else {
                    // Stale supervisor received exception (e.g. socket closed during accept):
                    // Clean up only our local orphan process/socket if it wasn't adopted
                    try { localClient?.close() } catch (_: Throwable) {}
                    if (localDaemonPid > 0 && localDaemonPid != daemonPid) {
                        terminateProcessAndPid(localProcess, localDaemonPid)
                    }
                }
            }
        }
    }

    private fun startReaderLoop(inputStream: InputStream) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.Default) {
            val packetSize = 4 + 1 + 4 + (NUM_BANDS * 4) // 65 bytes
            val rawBytes = ByteArray(packetSize)
            val byteBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
            val incomingBands = FloatArray(NUM_BANDS)

            while (isActive && (currentState == VisualizerState.RUNNING || currentState == VisualizerState.PAUSED)) {
                if (currentState == VisualizerState.PAUSED) {
                    delay(100)
                    continue
                }

                // Read full packet
                var totalRead = 0
                while (totalRead < packetSize && isActive) {
                    val read = inputStream.read(rawBytes, totalRead, packetSize - totalRead)
                    if (read == -1) break
                    totalRead += read
                }

                if (totalRead < packetSize) {
                    break
                }

                // Verify magic "VIZ1"
                if (rawBytes[0] != 'V'.code.toByte() ||
                    rawBytes[1] != 'I'.code.toByte() ||
                    rawBytes[2] != 'Z'.code.toByte() ||
                    rawBytes[3] != '1'.code.toByte()
                ) {
                    continue
                }

                byteBuffer.position(5) // Skip magic + band count
                val rms = byteBuffer.float
                for (b in 0 until NUM_BANDS) {
                    incomingBands[b] = byteBuffer.float
                }

                // Directly consume incoming 14 linear frequency strips from daemon
                var activeSignal = false
                val bandsOut = FloatArray(NUM_BANDS)
                val peaksOut = FloatArray(NUM_BANDS)

                val normSpeed = ((currentSpeed - 0.10f) / 0.90f).coerceIn(0f, 1f)
                val speedCurve = normSpeed.pow(1.4f)

                // Truly silky, fluid and liquid at low speeds; agile and crisp at high speeds
                val baseRiseAlpha = (0.045f + 0.455f * speedCurve).coerceIn(0.035f, 0.60f)
                val baseFallAlpha = (0.010f + 0.150f * speedCurve).coerceIn(0.008f, 0.20f)
                val peakFallAlpha = (0.0018f + 0.012f * speedCurve).coerceIn(0.0015f, 0.018f)

                // All 14 bars move with unified ballistics and timing so bass bars never feel accelerated
                val activeRiseAlpha = baseRiseAlpha
                val activeFallAlpha = baseFallAlpha
                val activePeakFallAlpha = peakFallAlpha

                for (i in 0 until NUM_BANDS) {
                    val rawTarget = incomingBands[i].coerceIn(0f, 1f)
                    if (rawTarget > 0.01f) activeSignal = true

                    val target = when (i) {
                        0 -> {
                            // Band 0 (Subgrave - 25-65 Hz): Clean sustain floor subtraction with natural curve.
                            // Drops cleanly between rapid bass kicks without getting stuck or feeling hypersensitive.
                            val cleaned = ((rawTarget - 0.16f) / 0.84f).coerceIn(0f, 1f)
                            (cleaned.pow(1.35f) * 0.78f).coerceIn(0f, 1f)
                        }
                        1 -> {
                            // Band 1 (Bumbo / Kick - 65-115 Hz): Punchy beat response with controlled headroom.
                            val cleaned = ((rawTarget - 0.10f) / 0.90f).coerceIn(0f, 1f)
                            (cleaned.pow(1.25f) * 0.80f).coerceIn(0f, 1f)
                        }
                        2 -> {
                            // Band 2 (Punch / Ataque - 115-175 Hz): Smooth transition.
                            val cleaned = ((rawTarget - 0.08f) / 0.92f).coerceIn(0f, 1f)
                            (cleaned.pow(1.20f) * 0.82f).coerceIn(0f, 1f)
                        }
                        else -> {
                            // Bands 3 to 13: Mids and highs with pleasant acoustic headroom, avoiding hypersensitivity.
                            val weight = SPECTRAL_WEIGHTS[i]
                            val cleaned = ((rawTarget - 0.05f) / 0.95f).coerceIn(0f, 1f)
                            (cleaned * weight * 0.78f).coerceIn(0f, 1f)
                        }
                    }

                    // 1. Asymmetric ballistics: smooth rise and shared silky-smooth fall
                    val current = currentLevels[i]
                    val alpha = if (target >= current) activeRiseAlpha else activeFallAlpha
                    currentLevels[i] = current + (target - current) * alpha
                    if (currentLevels[i] < 0.002f) currentLevels[i] = 0f
                    bandsOut[i] = currentLevels[i]

                    // 2. Peak physics: instant rise, continuous asymptotic fall
                    val v = currentLevels[i]
                    if (v >= currentPeaks[i]) {
                        currentPeaks[i] = v
                    } else {
                        currentPeaks[i] += (v - currentPeaks[i]) * activePeakFallAlpha
                    }
                    if (currentPeaks[i] < 0.002f) currentPeaks[i] = 0f
                    peaksOut[i] = currentPeaks[i]
                }
                _frameFlow.value = VisualizerFrame(
                    bands = bandsOut,
                    peaks = peaksOut,
                    rms = rms,
                    hasAudio = activeSignal || rms > 0.015f,
                )
            }
        }
    }

    private suspend fun cleanupDaemonLocked(expectedGeneration: Long) = withContext(Dispatchers.IO) {
        if (expectedGeneration != currentGeneration) {
            return@withContext
        }

        readerJob?.cancel()
        readerJob = null

        // 1. Graceful exit: send 'S' command to daemon socket
        try {
            clientOut?.write('S'.code)
            clientOut?.flush()
        } catch (_: Throwable) {}

        try {
            clientSocket?.close()
        } catch (_: Throwable) {}
        clientSocket = null
        clientOut = null

        try {
            serverSocket?.close()
        } catch (_: Throwable) {}
        serverSocket = null

        val targetPid = daemonPid
        daemonPid = -1

        val proc = daemonProcess
        daemonProcess = null

        // 2. Wait up to 300 ms for normal exit and kill specific process
        terminateProcessAndPid(proc, targetPid)
    }

    private fun terminateProcessAndPid(proc: Process?, pid: Int) {
        if (proc != null) {
            val exited = runCatching {
                var count = 0
                while (count < 6) {
                    if (!proc.isAlive) return@runCatching true
                    Thread.sleep(50)
                    count++
                }
                false
            }.getOrDefault(false)

            if (!exited) {
                proc.destroy() // SIGTERM
                try { Thread.sleep(100) } catch (_: Throwable) {}
                if (proc.isAlive) {
                    proc.destroyForcibly() // SIGKILL fallback
                }
            }
        }

        // Ensure specific PID is terminated
        if (pid > 0) {
            killSpecificPid(pid)
        }

        // Defensiva anti-zumbi: garante que nenhum processo orfao continue segurando a Sessao 0
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f dev.viagaralauncher.visualizer.AudioVisualizerDaemon || true")).waitFor()
        } catch (_: Throwable) {}
    }

    private fun killSpecificPid(pid: Int) {
        try {
            // First SIGTERM
            Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -TERM $pid || true")).waitFor()
            Thread.sleep(80)
            // If still alive, fallback to SIGKILL
            Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -0 $pid && kill -KILL $pid || true")).waitFor()
        } catch (_: Throwable) {}
    }

    private fun extractOrPrepareDex(context: Context): String? {
        val targetFile = File(context.filesDir, "visualizer_daemon.dex")
        try {
            context.assets.open("visualizer_daemon.dex").use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.setReadable(true, false)
            return targetFile.absolutePath
        } catch (_: Throwable) {
            val fallback = File("/data/local/tmp/visualizer_daemon.dex")
            if (fallback.exists()) {
                return fallback.absolutePath
            }
        }
        return null
    }
}
