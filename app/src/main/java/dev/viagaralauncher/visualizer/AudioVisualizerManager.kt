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
import kotlin.math.max
import kotlin.math.pow

/**
 * Visualizer spectrum frame containing 32 smooth frequency bands and floating peak positions.
 */
data class VisualizerFrame(
    val bands: FloatArray = FloatArray(32),
    val peaks: FloatArray = FloatArray(32),
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
    private const val NUM_BANDS = 32

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

    // Ballistic physics state
    private val currentLevels = FloatArray(NUM_BANDS)
    private val currentPeaks = FloatArray(NUM_BANDS)
    private val peakHoldFrames = IntArray(NUM_BANDS)

    // --- Perceptual Dynamic Range Calibration ---
    // The daemon maps [-6 dB .. +42 dB] linearly into [0.0 .. 1.0].
    // We calibrate an active musical range that cuts DAC/mixer noise while emphasizing beats and basslines.
    private const val NOISE_FLOOR_DB = 9.0f         // Below ~9 dB (mag < 2.8) is clamped to 0 (rejects residual noise/hiss)
    private const val CEILING_DB = 38.0f            // Upper reference ceiling (~80x FFT magnitude)
    private const val DYNAMIC_RANGE_DB = CEILING_DB - NOISE_FLOOR_DB // 29.0 dB active dynamic span
    private const val DYNAMIC_EXPONENT = 1.35f      // Stevens' power-law perceptual contrast curve

    // Ballistic physics constants
    private const val ATTACK_FACTOR = 0.85f         // Fast attack: beats register within 1-2 frames
    private const val DECAY_PERCENT = 0.86f         // Musical exponential decay per frame (~60 FPS)
    private const val DECAY_SUBTRACTION = 0.012f    // Linear baseline bleed to guarantee decay reaches true 0
    private const val PEAK_HOLD_COUNT = 9           // ~150 ms peak hold at 60 FPS
    private const val PEAK_FALL_SPEED = 0.032f      // Swift, organic peak descent

    // Perceptual spectral weighting:
    // - Boosts bass impact (bands 0..5: 35-180 Hz) so kick drums produce dramatic leaps
    // - Slightly relaxes dense midrange (bands 6..18: 200-2500 Hz) to avoid a static wall of bars
    // - Smoothly compensates natural 1/f falloff in treble (bands 19..31: 3-18 kHz)
    private val spectralWeights = FloatArray(NUM_BANDS) { b ->
        when {
            b <= 5 -> 1.18f - (b.toFloat() / 5f) * 0.18f
            b <= 18 -> 0.90f + ((b - 6).toFloat() / 12f) * 0.10f
            else -> 1.00f + ((b - 18).toFloat() / 13f) * 0.28f
        }
    }

    fun start(context: Context) {
        scope.launch {
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
    }

    fun stop() {
        scope.launch {
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
                    peakHoldFrames.fill(0)
                    _frameFlow.value = VisualizerFrame()
                }

                currentState = VisualizerState.STOPPED
            }
        }
    }

    fun onLauncherResume() {
        scope.launch {
            lifecycleMutex.withLock {
                if (currentState == VisualizerState.PAUSED) {
                    currentState = VisualizerState.RUNNING
                    sendControlCommand('R')
                }
            }
        }
    }

    fun onLauncherPause() {
        scope.launch {
            lifecycleMutex.withLock {
                if (currentState == VisualizerState.RUNNING) {
                    currentState = VisualizerState.PAUSED
                    sendControlCommand('P')
                }
            }
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
            val timeoutMillis = System.currentTimeMillis() + 1500L
            while (System.currentTimeMillis() < timeoutMillis && reader.ready()) {
                val line = reader.readLine() ?: break
                if (line.startsWith("PID:")) {
                    extractedPid = line.substring(4).trim().toIntOrNull() ?: -1
                    break
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
            val packetSize = 4 + 1 + 4 + (NUM_BANDS * 4) // 137 bytes
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

                // Apply perceptual dynamic range mapping and physics ballistics
                var activeSignal = false
                val bandsOut = FloatArray(NUM_BANDS)
                val peaksOut = FloatArray(NUM_BANDS)

                for (i in 0 until NUM_BANDS) {
                    val rawVal = incomingBands[i].coerceIn(0f, 1f)

                    // 1. Reconstruct estimated dB from daemon's linear [-6 dB .. +42 dB] mapping
                    val rawDb = (rawVal * 48.0f) - 6.0f

                    // 2. Noise floor gate & active dynamic range normalization
                    val target: Float
                    if (rawDb <= NOISE_FLOOR_DB) {
                        target = 0f
                    } else {
                        val linearSpan = ((rawDb - NOISE_FLOOR_DB) / DYNAMIC_RANGE_DB).coerceIn(0f, 1f)
                        val weighted = (linearSpan * spectralWeights[i]).coerceIn(0f, 1f)
                        target = weighted.toDouble().pow(DYNAMIC_EXPONENT.toDouble()).toFloat()
                    }

                    if (target > 0.03f) activeSignal = true

                    // 3. Attack (instant rise) & Decay (exponential drop + linear floor bleed)
                    if (target >= currentLevels[i]) {
                        currentLevels[i] = currentLevels[i] + (target - currentLevels[i]) * ATTACK_FACTOR
                    } else {
                        val decayed = currentLevels[i] * DECAY_PERCENT - DECAY_SUBTRACTION
                        currentLevels[i] = max(target, decayed).coerceAtLeast(0f)
                    }
                    bandsOut[i] = currentLevels[i]

                    // 4. Peak Hold & Swift Fall
                    if (currentLevels[i] >= currentPeaks[i]) {
                        currentPeaks[i] = currentLevels[i]
                        peakHoldFrames[i] = PEAK_HOLD_COUNT
                    } else {
                        if (peakHoldFrames[i] > 0) {
                            peakHoldFrames[i]--
                        } else {
                            currentPeaks[i] = (currentPeaks[i] - PEAK_FALL_SPEED).coerceAtLeast(currentLevels[i])
                        }
                    }
                    peaksOut[i] = currentPeaks[i]
                }

                _frameFlow.value = VisualizerFrame(
                    bands = bandsOut,
                    peaks = peaksOut,
                    rms = rms,
                    hasAudio = activeSignal || rms > 0.02f,
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
