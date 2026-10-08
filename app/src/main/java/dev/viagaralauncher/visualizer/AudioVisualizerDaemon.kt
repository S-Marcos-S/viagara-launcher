// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.visualizer

import android.media.audiofx.Visualizer
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Privileged daemon executed via app_process64 as UID 0 (root).
 * Captures system-wide audio output using global session 0,
 * faithfully reproduces SDWMP3_CN 4-zone spectrum extraction, EMA temporal smoothing,
 * and 14-strip sinusoidal jittered distribution, and streams binary packets
 * to the launcher UI over an abstract LocalSocket.
 *
 * Command protocol (from Launcher to Daemon):
 * - 'P' : Pause visualizer (disables capture, sleeps, stops sending packets)
 * - 'R' : Resume visualizer (re-enables capture, resumes packet streaming)
 * - 'S' / 'Q' : Stop visualizer (graceful release and clean termination)
 */
object AudioVisualizerDaemon {

    private const val SOCKET_NAME = "viagara_audio_viz"
    private const val CAPTURE_SIZE = 1024
    private const val NUM_BANDS = 14
    private const val FRAME_INTERVAL_MS = 16L // ~60 FPS

    @JvmStatic
    fun main(args: Array<String>) {
        var visualizer: Visualizer? = null
        var socket: LocalSocket? = null
        var outStream: OutputStream? = null
        var inStream: InputStream? = null

        try {
            socket = LocalSocket()
            var connected = false
            for (i in 0 until 50) {
                try {
                    socket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                    connected = true
                    break
                } catch (_: Throwable) {
                    Thread.sleep(100)
                }
            }

            if (!connected) {
                return
            }

            socket.soTimeout = 500

            outStream = socket.outputStream
            inStream = socket.inputStream

            visualizer = Visualizer(0)
            visualizer.captureSize = CAPTURE_SIZE
            visualizer.enabled = true

            val fftBuffer = ByteArray(CAPTURE_SIZE)
            val waveBuffer = ByteArray(CAPTURE_SIZE)
            val magnitudes = FloatArray(NUM_BANDS)

            val packetBuffer = ByteBuffer.allocate(4 + 1 + 4 + (NUM_BANDS * 4)).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            var isPaused = false
            var frameSeed = 0L

            var emaSub = 0f
            var emaBass = 0f
            var emaMid = 0f
            var emaHigh = 0f

            while (true) {
                // 1. Process control commands from launcher
                while (inStream.available() > 0) {
                    val cmd = inStream.read()
                    if (cmd == -1 || cmd == 'S'.code || cmd == 'Q'.code) {
                        return
                    } else if (cmd == 'P'.code) {
                        if (!isPaused) {
                            isPaused = true
                            try { visualizer.enabled = false } catch (_: Throwable) {}
                        }
                    } else if (cmd == 'R'.code) {
                        if (isPaused) {
                            isPaused = false
                            try { visualizer.enabled = true } catch (_: Throwable) {}
                        }
                    }
                }

                // If paused, sleep and do not poll FFT or write to socket
                if (isPaused) {
                    Thread.sleep(50)
                    continue
                }

                val loopStartTime = System.currentTimeMillis()

                val fftResult = visualizer.getFft(fftBuffer)
                val waveResult = visualizer.getWaveForm(waveBuffer)

                var rms = 0f

                if (fftResult == Visualizer.SUCCESS) {
                    val n = fftBuffer.size / 2 // 512 pairs
                    var s = 0f
                    var b = 0f
                    var m = 0f
                    var h = 0f
                    var cs = 0
                    var cb = 0
                    var cm = 0
                    var ch = 0

                    val subEnd = (n * 0.08f).toInt().coerceAtLeast(1)   // 40
                    val bassEnd = (n * 0.20f).toInt().coerceAtLeast(2)  // 102
                    val midEnd = (n * 0.55f).toInt().coerceAtLeast(4)   // 281

                    for (i in 0 until n step 2) {
                        val re = fftBuffer[i].toFloat() / 128f
                        val im = if (i + 1 < fftBuffer.size) fftBuffer[i + 1].toFloat() / 128f else 0f
                        val mag = sqrt(re * re + im * im)
                        if (i < subEnd) {
                            s += mag
                            cs++
                        } else if (i < bassEnd) {
                            b += mag
                            cb++
                        } else if (i < midEnd) {
                            m += mag
                            cm++
                        } else {
                            h += mag
                            ch++
                        }
                    }

                    val sv = (s / maxOf(1, cs)).coerceIn(0f, 1f)
                    val bv = (b / maxOf(1, cb)).coerceIn(0f, 1f)
                    val mv = (m / maxOf(1, cm)).coerceIn(0f, 1f)
                    val hv = (h / maxOf(1, ch)).coerceIn(0f, 1f)

                    // SDWMP3_CN PlayerScreen.kt EMA (alpha = 0.15f)
                    emaSub = emaSub * 0.85f + sv * 0.15f
                    emaBass = emaBass * 0.85f + bv * 0.15f
                    emaMid = emaMid * 0.85f + mv * 0.15f
                    emaHigh = emaHigh * 0.85f + hv * 0.15f

                    // SDWMP3_CN PlayerScreen.kt RMS calculation
                    rms = (emaSub * 0.3f + emaBass * 0.4f + emaMid * 0.2f + emaHigh * 0.1f)

                    // SDWMP3_CN VuMeter.kt (VuMixer) 14 strips expansion
                    frameSeed++
                    val isActive = (emaSub > 0.005f || emaBass > 0.005f || emaMid > 0.005f || emaHigh > 0.005f)

                    for (i in 0 until NUM_BANDS) {
                        val bi = (i * 24 / NUM_BANDS).coerceIn(0, 23)
                        val raw = when {
                            bi < 6 -> emaSub
                            bi < 14 -> emaBass
                            bi < 20 -> emaMid
                            else -> emaHigh
                        }
                        val target = if (isActive) jittered(raw, bi, 24, frameSeed) else 0f
                        magnitudes[i] = target
                    }
                } else {
                    magnitudes.fill(0f)
                    if (waveResult == Visualizer.SUCCESS) {
                        rms = computeWaveRms(waveBuffer)
                    }
                }

                packetBuffer.clear()
                packetBuffer.put('V'.code.toByte())
                packetBuffer.put('I'.code.toByte())
                packetBuffer.put('Z'.code.toByte())
                packetBuffer.put('1'.code.toByte())
                packetBuffer.put(NUM_BANDS.toByte())
                packetBuffer.putFloat(rms)
                for (bandIndex in 0 until NUM_BANDS) {
                    packetBuffer.putFloat(magnitudes[bandIndex])
                }

                outStream.write(packetBuffer.array())
                outStream.flush()

                val elapsed = System.currentTimeMillis() - loopStartTime
                val sleepTime = FRAME_INTERVAL_MS - elapsed
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime)
                }
            }
        } catch (_: Throwable) {
        } finally {
            try { visualizer?.enabled = false } catch (_: Throwable) {}
            try { visualizer?.release() } catch (_: Throwable) {}
            try { inStream?.close() } catch (_: Throwable) {}
            try { outStream?.close() } catch (_: Throwable) {}
            try { socket?.close() } catch (_: Throwable) {}
        }
    }

    private fun computeWaveRms(wave: ByteArray): Float {
        var sumSquares = 0.0
        for (b in wave) {
            val v = (b.toInt() and 0xFF) - 128
            sumSquares += (v * v).toDouble()
        }
        val mean = sumSquares / wave.size
        return min(1.0, sqrt(mean) / 128.0).toFloat()
    }

    /**
     * SDWMP3_CN VuMeter.kt jittered implementation:
     * - Strict noise gate: if v < 0.01f -> 0f
     * - Sinusoidal shape across frequency position
     * - Deterministic trig hash avoiding random allocations
     */
    private fun jittered(v: Float, barIndex: Int, totalBars: Int, frameSeed: Long): Float {
        if (v < 0.01f) return 0f
        val pos = barIndex.toFloat() / (totalBars - 1)
        val shape = when {
            pos < 0.25f -> 0.6f + 0.4f * sin(pos * Math.PI.toFloat() * 4f)
            pos < 0.45f -> 1.0f - 0.15f * sin(pos * Math.PI.toFloat() * 2.5f)
            pos < 0.65f -> 0.85f + 0.15f * sin(pos * Math.PI.toFloat() * 3f)
            else -> 0.7f + 0.3f * sin(pos * Math.PI.toFloat() * 5f + frameSeed * 0.1f)
        }
        val h = sin((barIndex * 7919 + frameSeed % 1009).toFloat() * 0.73f) * 0.5f + 0.5f
        val jitter = 0.85f + h * 0.30f
        return (v * shape * jitter).coerceIn(0f, 1f)
    }
}
