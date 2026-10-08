// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.visualizer

import android.media.audiofx.Visualizer
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.log10
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

            val sampleRateHz = max(44100, visualizer.samplingRate / 1000)
            val fftBuffer = ByteArray(CAPTURE_SIZE)
            val waveBuffer = ByteArray(CAPTURE_SIZE)
            val magnitudes = FloatArray(NUM_BANDS)

            val bandEdges = computeLogBandEdges(NUM_BANDS, CAPTURE_SIZE, sampleRateHz)

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

    private fun computeLogBandEdges(bandsCount: Int, captureSize: Int, sampleRateHz: Int): Array<BandEdge> {
        val nyquist = sampleRateHz / 2.0
        val minFreq = 35.0
        val maxFreq = min(18000.0, nyquist)
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

            val avgMagnitude = if (count > 0) sumMagnitude / count else 0f

            // Robust Logarithmic / dB Normalization:
            // Dynamic range ~48 dB: Floor at -48 dB (magnitude ~0.35), Ceiling at 0 dB (magnitude ~90.0)
            if (avgMagnitude <= 0.35f) {
                outBands[i] = 0f
            } else {
                val db = 20.0 * log10(avgMagnitude.toDouble())
                // db ranges from ~ -9 dB (for 0.35) to ~ 39 dB (for 90)
                // Normalize 48 dB dynamic range: [ -6 dB .. 42 dB ] -> [ 0.0 .. 1.0 ]
                val normalized = ((db + 6.0) / 48.0).toFloat()
                outBands[i] = max(0f, min(1f, normalized))
            }
        }
    }
}
