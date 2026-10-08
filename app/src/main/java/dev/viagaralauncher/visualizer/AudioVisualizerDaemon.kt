// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.visualizer

import android.media.audiofx.Visualizer
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Privileged daemon executed via app_process64 as UID 0 (root).
 * Captures system-wide audio output using global session 0,
 * calculates 4 acoustic frequency bands (Sub, Bass, Mid, High) matching Oboe DSP,
 * applies spectral Parseval RMS, persistent EMA (alpha = 0.20), uniform gain (6.0),
 * and feeds the 14-strip VuMixer distribution over an abstract LocalSocket.
 *
 * Command protocol (from Launcher to Daemon):
 * - 'P' : Pause visualizer (disables capture, sleeps, stops sending packets)
 * - 'R' : Resume visualizer (re-enables capture, resumes packet streaming)
 * - 'S' / 'Q' : Stop visualizer (graceful release and clean termination)
 */
object AudioVisualizerDaemon {

    private const val TAG = "VIZ_DIAG"
    private const val SOCKET_NAME = "viagara_audio_viz"
    private const val CAPTURE_SIZE = 1024
    private const val NUM_BANDS = 14
    private const val FRAME_INTERVAL_MS = 16L // ~60 FPS
    private val DIAG_LOG_FILE = File("/data/local/tmp/viz_diag.log")

    @JvmStatic
    fun main(args: Array<String>) {
        var visualizer: Visualizer? = null
        var socket: LocalSocket? = null
        var outStream: OutputStream? = null
        var inStream: InputStream? = null

        logDiag("=== AudioVisualizerDaemon started ===")

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
                logDiag("Failed to connect to LocalSocket viagara_audio_viz after 50 attempts")
                return
            }

            socket.soTimeout = 500

            outStream = socket.outputStream
            inStream = socket.inputStream

            visualizer = Visualizer(0)
            visualizer.captureSize = CAPTURE_SIZE
            visualizer.enabled = true

            val actualCaptureSize = visualizer.captureSize
            val rawSampleRate = visualizer.samplingRate
            val sampleRateHz = if (rawSampleRate > 0) rawSampleRate / 1000 else 48000
            val binWidth = sampleRateHz.toFloat() / actualCaptureSize.toFloat()

            // 14 Logarithmic Acoustic Frequency Bands (25 Hz to 18,500 Hz):
            // Band 0:  25 - 65 Hz   (Deep Sub / Subgrave profundo)
            // Band 1:  65 - 115 Hz  (Kick Drum / Bumbo)
            // Band 2:  115 - 175 Hz (Punch / Percussão de ataque)
            // Band 3:  175 - 260 Hz (Bassline / Linha de contrabaixo)
            // Band 4:  260 - 390 Hz (Corpo / Calor)
            // Band 5:  390 - 580 Hz (Caixa / Snare / harmônicos baixos)
            // Band 6:  580 - 870 Hz (Médios / Harmônicos melódicos e voz)
            // Band 7:  870 - 1300 Hz (Clareza vocal / sintetizadores)
            // Band 8:  1300 - 2000 Hz (Ataque melódico / presença)
            // Band 9:  2000 - 3200 Hz (Estalo da caixa / transientes)
            // Band 10: 3200 - 5000 Hz (Agudos / pratos de ataque)
            // Band 11: 5000 - 8000 Hz (Pratos de condução / ride)
            // Band 12: 8000 - 12500 Hz (Chimbal / hi-hat / estalo metálico)
            // Band 13: 12500 - 18500 Hz (Ar / shimmer / ambiência cristalina)
            val bandFreqs = floatArrayOf(
                25f, 65f, 115f, 175f, 260f, 390f, 580f, 870f,
                1300f, 2000f, 3200f, 5000f, 8000f, 12500f, 18500f
            )

            val bandStartBins = IntArray(NUM_BANDS)
            val bandEndBins = IntArray(NUM_BANDS)
            var lastEndBin = 0
            for (b in 0 until NUM_BANDS) {
                val start = max(lastEndBin + 1, (bandFreqs[b] / binWidth).roundToInt())
                val end = max(start, (bandFreqs[b + 1] / binWidth).roundToInt())
                bandStartBins[b] = start
                bandEndBins[b] = min(511, end)
                lastEndBin = bandEndBins[b]
            }

            // ISO-calibrated perceptual EQ curve:
            // Balanced visual energy across sub, bass, mids, and treble
            val eqGains = floatArrayOf(
                1.70f, // Band 0: Deep Sub
                1.55f, // Band 1: Kick drum
                1.35f, // Band 2: Punch
                1.20f, // Band 3: Bass
                1.10f, // Band 4: Low mid
                1.00f, // Band 5: Snare
                1.00f, // Band 6: Vocal body
                1.00f, // Band 7: Vocal clarity
                1.05f, // Band 8: Presence
                1.10f, // Band 9: Attack
                1.15f, // Band 10: Treble
                1.25f, // Band 11: Cymbals
                1.35f, // Band 12: Hi-hat
                1.50f  // Band 13: Air / shimmer
            )

            val emaBands = FloatArray(NUM_BANDS)
            val fftBuffer = ByteArray(CAPTURE_SIZE)
            val waveBuffer = ByteArray(CAPTURE_SIZE)
            val magnitudes = FloatArray(NUM_BANDS)

            val packetBuffer = ByteBuffer.allocate(4 + 1 + 4 + (NUM_BANDS * 4)).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            var isPaused = false
            var sampleCounter = 0L

            logDiag("14 physical acoustic bands configured (25Hz - 18.5kHz)")

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
                    var totalLevelSum = 0f

                    for (b in 0 until NUM_BANDS) {
                        val rawRms = computeBandRms(fftBuffer, bandStartBins[b], bandEndBins[b])

                        // Fast, punchy EMA (alpha = 0.35f) so transient beats (e.g. 0.5s kicks) pump dynamically
                        emaBands[b] = emaBands[b] * 0.65f + rawRms * 0.35f

                        val level = min(1.0f, emaBands[b] * eqGains[b])
                        magnitudes[b] = level
                        totalLevelSum += level
                    }

                    // Waveform RMS
                    rms = if (waveResult == Visualizer.SUCCESS) {
                        computeWaveRms(waveBuffer)
                    } else {
                        (totalLevelSum / NUM_BANDS).coerceIn(0f, 1f)
                    }

                    // Periodic diagnostic logging (~1s = every 60 frames)
                    sampleCounter++
                    if (sampleCounter % 60L == 0L) {
                        logDiag(
                            String.format(
                                "14_BANDS:\n" +
                                "B0=%.3f B1(Kick)=%.3f B2=%.3f B3(Bass)=%.3f B4=%.3f B5=%.3f B6=%.3f\n" +
                                "B7=%.3f B8=%.3f B9=%.3f B10=%.3f B11=%.3f B12(HiHat)=%.3f B13=%.3f\n",
                                magnitudes[0], magnitudes[1], magnitudes[2], magnitudes[3],
                                magnitudes[4], magnitudes[5], magnitudes[6], magnitudes[7],
                                magnitudes[8], magnitudes[9], magnitudes[10], magnitudes[11],
                                magnitudes[12], magnitudes[13]
                            )
                        )
                    }
                } else {
                    magnitudes.fill(0f)
                    emaBands.fill(0f)
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
        } catch (t: Throwable) {
            logDiag("Daemon exception: ${t.message}")
        } finally {
            try { visualizer?.enabled = false } catch (_: Throwable) {}
            try { visualizer?.release() } catch (_: Throwable) {}
            try { inStream?.close() } catch (_: Throwable) {}
            try { outStream?.close() } catch (_: Throwable) {}
            try { socket?.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Spectral RMS calculation for an acoustic band [startBin..endBin]:
     *
     * In Android Visualizer FFT:
     * - fft[0] = DC real component (imaginary is 0)
     * - fft[1] = Nyquist real component (imaginary is 0)
     * - For k >= 1: fft[2k] = real, fft[2k+1] = imaginary (signed 8-bit, normalized by 128.0f)
     *
     * By Parseval's theorem, total band energy is the sum of squared harmonic amplitudes.
     * The factor 2 converts peak complex harmonic amplitude to RMS power (A_rms = A_peak / sqrt(2)).
     */
    private fun computeBandRms(fft: ByteArray, startBin: Int, endBin: Int): Float {
        var sumPower = 0.0
        val maxK = min(511, endBin)

        for (k in startBin..maxK) {
            val re: Float
            val im: Float
            if (k == 0) {
                re = fft[0].toFloat() / 128.0f
                im = 0.0f
            } else {
                re = fft[2 * k].toFloat() / 128.0f
                im = if (2 * k + 1 < fft.size) fft[2 * k + 1].toFloat() / 128.0f else 0.0f
            }
            sumPower += (re * re + im * im).toDouble()
        }

        return sqrt(sumPower / 2.0).toFloat()
    }

    private fun logDiag(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {}
        try {
            FileOutputStream(DIAG_LOG_FILE, true).use { fos ->
                fos.write((msg + "\n").toByteArray(StandardCharsets.UTF_8))
            }
            DIAG_LOG_FILE.setReadable(true, false)
            DIAG_LOG_FILE.setWritable(true, false)
        } catch (_: Throwable) {}
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
}
