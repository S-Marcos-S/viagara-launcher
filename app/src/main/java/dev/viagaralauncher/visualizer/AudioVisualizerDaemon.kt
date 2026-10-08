// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.visualizer

import android.media.audiofx.Visualizer
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Privileged daemon executed via app_process64 as UID 0 (root).
 * Captures system-wide audio output using global session 0,
 * calculates 32 logarithmic frequency bands from 1024-point FFT,
 * and streams binary packets to the launcher UI over an abstract LocalSocket.
 *
 * Command protocol (from Launcher to Daemon):
 * - 'P' : Pause visualizer (disables capture, sleeps, stops sending packets)
 * - 'R' : Resume visualizer (re-enables capture, resumes packet streaming)
 * - 'S' / 'Q' : Stop visualizer (graceful release and clean termination)
 */
object AudioVisualizerDaemon {

    private const val SOCKET_NAME = "viagara_audio_viz"
    private const val CAPTURE_SIZE = 1024
    private const val NUM_BANDS = 32
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

            val bandEdges = computeSdwBandEdges(CAPTURE_SIZE)

            val packetBuffer = ByteBuffer.allocate(4 + 1 + 4 + (NUM_BANDS * 4)).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            var isPaused = false

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

                if (fftResult == Visualizer.SUCCESS) {
                    processFftToBands(fftBuffer, bandEdges, magnitudes)
                } else {
                    magnitudes.fill(0f)
                }

                val rms = if (waveResult == Visualizer.SUCCESS) {
                    computeWaveRms(waveBuffer)
                } else 0f

                packetBuffer.clear()
                packetBuffer.put('V'.code.toByte())
                packetBuffer.put('I'.code.toByte())
                packetBuffer.put('Z'.code.toByte())
                packetBuffer.put('1'.code.toByte())
                packetBuffer.put(NUM_BANDS.toByte())
                packetBuffer.putFloat(rms)
                for (b in 0 until NUM_BANDS) {
                    packetBuffer.putFloat(magnitudes[b])
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
            try {
                visualizer?.enabled = false
            } catch (_: Throwable) {}
            try {
                visualizer?.release()
            } catch (_: Throwable) {}
            try {
                inStream?.close()
            } catch (_: Throwable) {}
            try {
                outStream?.close()
            } catch (_: Throwable) {}
            try {
                socket?.close()
            } catch (_: Throwable) {}
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

    private data class BandEdge(val startBin: Int, val endBin: Int)

    /**
     * Maps 512 FFT bins into 32 bands adhering strictly to SDWMP3_CN frequency zones:
     * - Sub (0% .. 8% of bins: 1..40)     -> 8 bands (bands 0..7)
     * - Bass (8% .. 20% of bins: 41..102)  -> 10 bands (bands 8..17)
     * - Mid (20% .. 55% of bins: 103..281) -> 8 bands (bands 18..25)
     * - High (55% .. 100% of bins: 282..511) -> 6 bands (bands 26..31)
     */
    private fun computeSdwBandEdges(captureSize: Int): Array<BandEdge> {
        val maxBin = (captureSize / 2) - 1 // 511
        val edges = ArrayList<BandEdge>(NUM_BANDS)

        fun addZone(startBin: Int, endBin: Int, count: Int) {
            val total = endBin - startBin + 1
            for (i in 0 until count) {
                val bStart = startBin + (i * total) / count
                val bEnd = (startBin + ((i + 1) * total) / count - 1).coerceAtLeast(bStart)
                edges.add(BandEdge(bStart, bEnd))
            }
        }

        addZone(1, 40, 8)       // Sub-bass
        addZone(41, 102, 10)    // Bass
        addZone(103, 281, 8)    // Midrange
        addZone(282, maxBin, 6) // High frequencies

        return edges.toTypedArray()
    }

    /**
     * SDWMP3_CN Linear Spectrum Analysis:
     * 1. Linear normalized magnitude: re = fft[2k]/128, im = fft[2k+1]/128, mag = sqrt(re^2 + im^2)
     * 2. Band magnitude = arithmetic mean of bin magnitudes
     * 3. Noise floor cutoff: if mag < 0.01f -> 0f (SDWMP3_CN strict silence)
     * 4. SDWMP3_CN proportional scaling with 1/f high frequency compensation
     */
    private fun processFftToBands(
        fft: ByteArray,
        bandEdges: Array<BandEdge>,
        outBands: FloatArray,
    ) {
        for (i in bandEdges.indices) {
            val edge = bandEdges[i]
            var sumMagnitude = 0f
            var count = 0

            for (k in edge.startBin..edge.endBin) {
                val real = fft[2 * k].toFloat() / 128f
                val imag = fft[2 * k + 1].toFloat() / 128f
                val mag = sqrt(real * real + imag * imag)
                sumMagnitude += mag
                count++
            }

            val avgMag = if (count > 0) sumMagnitude / count else 0f

            // Noise floor cutoff from SDWMP3_CN: values below 1% are strict zero
            val raw = if (avgMag < 0.01f) 0f else avgMag

            // Linear scale avoiding saturation and preserving spectral dynamic range
            val gain = when {
                i <= 7 -> 1.00f   // Sub-bass (drums/kicks fundamental): 1:1 scale avoids saturation on heavy bass
                i <= 17 -> 1.05f  // Bass punch (100-450 Hz)
                i <= 25 -> 1.10f  // Midrange (vocal & instrument presence)
                else -> 1.20f     // High frequencies (gentle balance without artificial distortion)
            }

            outBands[i] = (raw * gain).coerceIn(0f, 1f)
        }
    }
}
