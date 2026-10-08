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

            // Acoustic frequency bands (matching Oboe DSP cutoffs):
            // Sub:  0 - 60 Hz
            // Bass: 60 - 250 Hz
            // Mid:  250 - 2000 Hz
            // High: 2000 - 20000 Hz
            val subStartBin = 0
            val subEndBin = max(1, (60.0f / binWidth).roundToInt())

            val bassStartBin = subEndBin + 1
            val bassEndBin = max(bassStartBin, (250.0f / binWidth).roundToInt())

            val midStartBin = bassEndBin + 1
            val midEndBin = max(midStartBin, (2000.0f / binWidth).roundToInt())

            val highStartBin = midEndBin + 1
            val highEndBin = min(511, max(highStartBin, (20000.0f / binWidth).roundToInt()))

            val fftBuffer = ByteArray(CAPTURE_SIZE)
            val waveBuffer = ByteArray(CAPTURE_SIZE)
            val magnitudes = FloatArray(NUM_BANDS)

            val packetBuffer = ByteBuffer.allocate(4 + 1 + 4 + (NUM_BANDS * 4)).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            var isPaused = false
            var frameSeed = 0L
            var sampleCounter = 0L

            // Persistent EMA state per acoustic band (matching Oboe alpha = 0.20f)
            var emaSub = 0f
            var emaBass = 0f
            var emaMid = 0f
            var emaHigh = 0f

            logDiag(
                String.format(
                    "Acoustic Bands configured:\nsr=%d Hz, capture=%d, binWidth=%.2f Hz\n" +
                    "Sub:  [%d..%d] (%d bins, 0 - %.1f Hz)\n" +
                    "Bass: [%d..%d] (%d bins, %.1f - %.1f Hz)\n" +
                    "Mid:  [%d..%d] (%d bins, %.1f - %.1f Hz)\n" +
                    "High: [%d..%d] (%d bins, %.1f - %.1f Hz)\n",
                    sampleRateHz, actualCaptureSize, binWidth,
                    subStartBin, subEndBin, (subEndBin - subStartBin + 1), subEndBin * binWidth,
                    bassStartBin, bassEndBin, (bassEndBin - bassStartBin + 1), bassStartBin * binWidth, bassEndBin * binWidth,
                    midStartBin, midEndBin, (midEndBin - midStartBin + 1), midStartBin * binWidth, midEndBin * binWidth,
                    highStartBin, highEndBin, (highEndBin - highStartBin + 1), highStartBin * binWidth, highEndBin * binWidth
                )
            )

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
                    // Spectral RMS per acoustic band using Parseval integration:
                    // re = fft[2k]/128, im = fft[2k+1]/128
                    // power_k = re^2 + im^2
                    // rms_band = sqrt(sum(power_k) / 2) (sinusoidal peak-to-RMS factor 2)
                    val rawSub  = computeBandRms(fftBuffer, subStartBin, subEndBin)
                    val rawBass = computeBandRms(fftBuffer, bassStartBin, bassEndBin)
                    val rawMid  = computeBandRms(fftBuffer, midStartBin, midEndBin)
                    val rawHigh = computeBandRms(fftBuffer, highStartBin, highEndBin)

                    // Persistent EMA per band (Oboe alpha = 0.20f)
                    emaSub  = emaSub  * 0.80f + rawSub  * 0.20f
                    emaBass = emaBass * 0.80f + rawBass * 0.20f
                    emaMid  = emaMid  * 0.80f + rawMid  * 0.20f
                    emaHigh = emaHigh * 0.80f + rawHigh * 0.20f

                    // Uniform gain = 6.0f and clamp = 1.0f (exact Oboe constants)
                    val gain = 6.0f
                    val finalSub  = min(1.0f, emaSub  * gain)
                    val finalBass = min(1.0f, emaBass * gain)
                    val finalMid  = min(1.0f, emaMid  * gain)
                    val finalHigh = min(1.0f, emaHigh * gain)

                    // Waveform RMS
                    rms = if (waveResult == Visualizer.SUCCESS) {
                        computeWaveRms(waveBuffer)
                    } else {
                        finalSub * 0.3f + finalBass * 0.4f + finalMid * 0.2f + finalHigh * 0.1f
                    }

                    // Periodic diagnostic logging (~500ms = every 30 frames)
                    sampleCounter++
                    if (sampleCounter % 30L == 0L) {
                        val diag = String.format(
                            "FFT_BANDS:\nsr=%d\ncapture=%d\nbins: sub=%d, bass=%d, mid=%d, high=%d\n\n" +
                            "RAW:\nsub=%.5f\nbass=%.5f\nmid=%.5f\nhigh=%.5f\n\n" +
                            "EMA:\nsub=%.5f\nbass=%.5f\nmid=%.5f\nhigh=%.5f\n\n" +
                            "FINAL:\nsub=%.5f\nbass=%.5f\nmid=%.5f\nhigh=%.5f\n",
                            sampleRateHz, actualCaptureSize,
                            (subEndBin - subStartBin + 1),
                            (bassEndBin - bassStartBin + 1),
                            (midEndBin - midStartBin + 1),
                            (highEndBin - highStartBin + 1),
                            rawSub, rawBass, rawMid, rawHigh,
                            emaSub, emaBass, emaMid, emaHigh,
                            finalSub, finalBass, finalMid, finalHigh
                        )
                        logDiag(diag)
                    }

                    frameSeed++

                    // Feed VuMixer 14 strips via existing jittered() function
                    for (i in 0 until NUM_BANDS) {
                        val bi = (i * 24 / NUM_BANDS).coerceIn(0, 23)
                        val raw = when {
                            bi < 6 -> finalSub
                            bi < 14 -> finalBass
                            bi < 20 -> finalMid
                            else -> finalHigh
                        }
                        val target = jittered(raw, bi, 24, frameSeed)
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
