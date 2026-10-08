// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.visualizer

import android.media.audiofx.Visualizer
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Privileged daemon executed via app_process64 as UID 0 (root).
 * Captures system-wide audio output using global session 0,
 * calculates 32 logarithmic frequency bands from 1024-point FFT,
 * and streams binary packets to the launcher UI over an abstract LocalSocket.
 *
 * Packet format:
 * - Magic header: 0x56 0x49 0x5A 0x31 ("VIZ1") - 4 bytes
 * - Band count: Byte (32)
 * - RMS energy: Float (4 bytes)
 * - 32 Band magnitudes: FloatArray (32 * 4 = 128 bytes)
 * Total frame size: 137 bytes.
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

        try {
            // 1. Connect to launcher LocalServerSocket
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

            outStream = socket.outputStream
            val inStream = socket.inputStream

            // 2. Instantiate and configure Visualizer(0)
            visualizer = Visualizer(0)
            visualizer.captureSize = CAPTURE_SIZE
            visualizer.enabled = true

            val sampleRateHz = (visualizer.samplingRate / 1000).coerceAtLeast(44100)
            val fftBuffer = ByteArray(CAPTURE_SIZE)
            val waveBuffer = ByteArray(CAPTURE_SIZE)
            val magnitudes = FloatArray(NUM_BANDS)

            // Pre-calculate band boundaries
            val bandEdges = computeLogBandEdges(NUM_BANDS, CAPTURE_SIZE, sampleRateHz)

            val packetBuffer = ByteBuffer.allocate(4 + 1 + 4 + (NUM_BANDS * 4)).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            // Loop running until socket disconnects or clean exit request received
            while (true) {
                val loopStartTime = System.currentTimeMillis()

                // Check for commands from launcher (e.g. exit 'Q')
                if (inStream.available() > 0) {
                    val cmd = inStream.read()
                    if (cmd == 'Q'.code || cmd == -1) {
                        break
                    }
                }

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

                // Assemble packet
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
            // Clean exit on socket closed or exception
        } finally {
            try {
                visualizer?.enabled = false
            } catch (_: Throwable) {}
            try {
                visualizer?.release()
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
        return (sqrt(mean) / 128.0).toFloat().coerceIn(0f, 1f)
    }

    private data class BandEdge(val startBin: Int, val endBin: Int)

    private fun computeLogBandEdges(bandsCount: Int, captureSize: Int, sampleRateHz: Int): Array<BandEdge> {
        val nyquist = sampleRateHz / 2.0
        val minFreq = 35.0
        val maxFreq = nyquist.coerceAtMost(18000.0)
        val hzPerBin = sampleRateHz.toDouble() / captureSize.toDouble()

        val logMin = ln(minFreq)
        val logMax = ln(maxFreq)
        val logStep = (logMax - logMin) / bandsCount

        val edges = ArrayList<BandEdge>(bandsCount)
        var lastBin = 1

        for (i in 0 until bandsCount) {
            val fStart = Math.exp(logMin + i * logStep)
            val fEnd = Math.exp(logMin + (i + 1) * logStep)

            var binStart = (fStart / hzPerBin).toInt().coerceIn(1, (captureSize / 2) - 1)
            var binEnd = (fEnd / hzPerBin).toInt().coerceIn(binStart, (captureSize / 2) - 1)

            if (binStart <= lastBin && i > 0) {
                binStart = lastBin
            }
            if (binEnd < binStart) {
                binEnd = binStart
            }
            edges.add(BandEdge(binStart, binEnd))
            lastBin = binEnd
        }

        return edges.toTypedArray()
    }

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
                val real = fft[2 * k].toFloat()
                val imag = fft[2 * k + 1].toFloat()
                val mag = sqrt(real * real + imag * imag)
                sumMagnitude += mag
                count++
            }

            val avg = if (count > 0) sumMagnitude / count else 0f
            // Normalization: raw FFT amplitudes typically reach 40..100 for loud signals
            val normalized = (avg / 65f).coerceIn(0f, 1f)
            outBands[i] = normalized
        }
    }
}
