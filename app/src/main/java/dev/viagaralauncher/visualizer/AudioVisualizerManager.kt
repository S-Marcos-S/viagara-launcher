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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

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

/**
 * Manages the lifecycle of the root audio visualizer daemon:
 * - Listens on abstract LocalServerSocket "viagara_audio_viz"
 * - Spawns privileged worker via su and app_process64 using visualizer_daemon.dex
 * - Applies 60 FPS ballistic physics (fast attack, smooth gravitational decay, peak hold & gradual drop)
 * - Suspends processing when the launcher is in the background (ON_PAUSE / ON_STOP)
 * - Performs graceful shutdown (sends 'Q' command over socket, waits, falls back to SIGTERM, never immediate SIGKILL)
 * - Zero CPU / memory consumption when disabled.
 */
object AudioVisualizerManager {

    private const val SOCKET_NAME = "viagara_audio_viz"
    private const val NUM_BANDS = 32

    private val _frameFlow = MutableStateFlow(VisualizerFrame())
    val frameFlow: StateFlow<VisualizerFrame> = _frameFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var supervisorJob: Job? = null
    private var readerJob: Job? = null

    private var serverSocket: LocalServerSocket? = null
    private var clientSocket: LocalSocket? = null
    private var clientOut: OutputStream? = null

    private var daemonProcess: Process? = null
    private val isRunning = AtomicBoolean(false)
    private val isSuspended = AtomicBoolean(false)

    // Ballistic physics state
    private val currentLevels = FloatArray(NUM_BANDS)
    private val currentPeaks = FloatArray(NUM_BANDS)
    private val peakHoldFrames = IntArray(NUM_BANDS)

    private const val ATTACK_FACTOR = 0.82f
    private const val DECAY_FACTOR = 0.10f
    private const val PEAK_HOLD_COUNT = 14 // ~230 ms @ 60 FPS
    private const val PEAK_FALL_SPEED = 0.018f

    fun start(context: Context) {
        if (isRunning.getAndSet(true)) return
        isSuspended.set(false)

        supervisorJob = scope.launch {
            runSupervisor(context.applicationContext)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        isSuspended.set(false)

        supervisorJob?.cancel()
        supervisorJob = null

        scope.launch {
            cleanupDaemon()
            withContext(Dispatchers.Default) {
                currentLevels.fill(0f)
                currentPeaks.fill(0f)
                peakHoldFrames.fill(0)
                _frameFlow.value = VisualizerFrame()
            }
        }
    }

    fun onLauncherResume() {
        if (isRunning.get()) {
            isSuspended.set(false)
        }
    }

    fun onLauncherPause() {
        if (isRunning.get()) {
            isSuspended.set(true)
        }
    }

    private suspend fun runSupervisor(context: Context) = withContext(Dispatchers.IO) {
        try {
            // 1. Terminate any previous orphan daemon instance cleanly
            killOrphanDaemons()

            // 2. Open LocalServerSocket
            try {
                serverSocket = LocalServerSocket(SOCKET_NAME)
            } catch (e: Throwable) {
                serverSocket?.close()
                serverSocket = LocalServerSocket(SOCKET_NAME)
            }

            // 3. Prepare standalone DEX asset in filesDir
            val dexPath = extractOrPrepareDex(context) ?: return@withContext

            // 4. Start daemon via su
            val launchScript = """
                export CLASSPATH="$dexPath"
                exec /system/bin/app_process64 /data/local/tmp dev.viagaralauncher.visualizer.AudioVisualizerDaemon
            """.trimIndent()

            val process = ProcessBuilder("su", "-c", launchScript).start()
            daemonProcess = process

            // 5. Accept client socket connection
            val client = serverSocket?.accept() ?: return@withContext
            clientSocket = client
            clientOut = client.outputStream
            val inputStream = client.inputStream

            startReaderLoop(inputStream)
        } catch (_: Throwable) {
            cleanupDaemon()
        }
    }

    private fun startReaderLoop(inputStream: InputStream) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.Default) {
            val packetSize = 4 + 1 + 4 + (NUM_BANDS * 4) // 137 bytes
            val rawBytes = ByteArray(packetSize)
            val byteBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
            val incomingBands = FloatArray(NUM_BANDS)

            while (isActive && isRunning.get()) {
                if (isSuspended.get()) {
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

                // Apply physics & ballistics: attack, decay, peak hold
                var activeSignal = false
                val bandsOut = FloatArray(NUM_BANDS)
                val peaksOut = FloatArray(NUM_BANDS)

                for (i in 0 until NUM_BANDS) {
                    val target = incomingBands[i]
                    if (target > 0.02f) activeSignal = true

                    // Attack / Decay
                    if (target >= currentLevels[i]) {
                        currentLevels[i] = currentLevels[i] + (target - currentLevels[i]) * ATTACK_FACTOR
                    } else {
                        currentLevels[i] = (currentLevels[i] - DECAY_FACTOR).coerceAtLeast(target).coerceAtLeast(0f)
                    }
                    bandsOut[i] = currentLevels[i]

                    // Peak Hold & Fall
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
                    hasAudio = activeSignal || rms > 0.015f,
                )
            }
        }
    }

    private suspend fun cleanupDaemon() = withContext(Dispatchers.IO) {
        readerJob?.cancel()
        readerJob = null

        // 1. Graceful exit: send 'Q' command to daemon socket
        try {
            clientOut?.write('Q'.code)
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

        // 2. Wait up to 300 ms for normal exit
        val proc = daemonProcess
        if (proc != null) {
            val exited = runCatching {
                var count = 0
                while (count < 6) {
                    if (!proc.isAlive) return@runCatching true
                    delay(50)
                    count++
                }
                false
            }.getOrDefault(false)

            // 3. Fallback to SIGTERM (and SIGKILL only if stubborn)
            if (!exited) {
                proc.destroy() // Sends SIGTERM
                delay(100)
                if (proc.isAlive) {
                    proc.destroyForcibly() // SIGKILL fallback
                }
            }
        }
        daemonProcess = null

        // Ensure no lingering background process
        killOrphanDaemons()
    }

    private fun killOrphanDaemons() {
        try {
            Runtime.getRuntime().exec(arrayOf(
                "su", "-c",
                "pkill -TERM -f 'dev.viagaralauncher.visualizer.AudioVisualizerDaemon' || true"
            )).waitFor()
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
            // Fallback to /data/local/tmp if previously pushed or available
            val fallback = File("/data/local/tmp/visualizer_daemon.dex")
            if (fallback.exists()) {
                return fallback.absolutePath
            }
        }
        return null
    }
}
