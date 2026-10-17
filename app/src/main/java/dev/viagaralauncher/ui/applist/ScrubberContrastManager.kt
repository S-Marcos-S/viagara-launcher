// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.applist

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.viagaralauncher.data.EdgeSide
import dev.viagaralauncher.wallpaper.WallpaperRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-performance, battery-friendly manager that samples background luminance along the
 * vertical edge strip where EdgeScrubber is placed.
 *
 * In automatic font color mode, if the wallpaper edge strip has light tones, the entire
 * alphabet is rendered in solid black. For dark or medium backgrounds, it is rendered in solid white.
 * Per-letter individual color contrast calculation is completely eliminated.
 */
object ScrubberContrastManager {

    private const val TAG = "ScrubberContrastManager"
    private const val BRIGHTNESS_THRESHOLD = 0.55f // Threshold for detecting light tones

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "Uncaught error in scrubber contrast coroutine", throwable)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    @Composable
    fun rememberEdgeIsBright(
        side: EdgeSide,
        bandTopRatio: Float,
        bandBottomRatio: Float,
    ): State<Boolean> {
        val context = LocalContext.current
        val isBrightState = remember(side, bandTopRatio, bandBottomRatio) {
            mutableStateOf(false)
        }

        DisposableEffect(side, bandTopRatio, bandBottomRatio) {
            val wm = WallpaperManager.getInstance(context)

            fun computeContrast() {
                scope.launch {
                    runCatching {
                        val isBright = sampleEdgeLuminance(
                            context = context,
                            side = side,
                            topRatio = bandTopRatio,
                            bottomRatio = bandBottomRatio,
                        )
                        withContext(Dispatchers.Main) {
                            isBrightState.value = isBright
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to compute edge contrast", e)
                    }
                }
            }

            computeContrast()

            val listener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val l = WallpaperManager.OnColorsChangedListener { _, which ->
                    if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                        computeContrast()
                    }
                }
                runCatching { wm.addOnColorsChangedListener(l, Handler(Looper.getMainLooper())) }
                l
            } else null

            // Also listen to internal repository changes when user sets a wallpaper inside the app
            val repoJob = scope.launch {
                runCatching {
                    WallpaperRepository.wallpaperUpdateTick.collect { tick ->
                        if (tick > 0L) {
                            computeContrast()
                        }
                    }
                }.onFailure { e ->
                    Log.w(TAG, "Error in wallpaper update collection", e)
                }
            }

            onDispose {
                repoJob.cancel()
                if (listener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching { wm.removeOnColorsChangedListener(listener) }
                }
            }
        }

        return isBrightState
    }

    @Composable
    fun rememberEdgeContrast(
        side: EdgeSide,
        lettersCount: Int,
        bandTopRatio: Float,
        bandBottomRatio: Float,
    ): State<BooleanArray> {
        val isBrightState = rememberEdgeIsBright(side, bandTopRatio, bandBottomRatio)
        return remember(isBrightState.value, lettersCount) {
            mutableStateOf(BooleanArray(lettersCount) { isBrightState.value })
        }
    }

    private suspend fun sampleEdgeLuminance(
        context: Context,
        side: EdgeSide,
        topRatio: Float,
        bottomRatio: Float,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = runCatching {
                val wm = WallpaperManager.getInstance(context)
                val drawable = wm.drawable
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    null
                }
            }.getOrNull() ?: return@withContext false

            val bw = bitmap.width
            val bh = bitmap.height
            if (bw <= 0 || bh <= 0) return@withContext false

            val minRatio = minOf(topRatio, bottomRatio).coerceIn(0f, 1f)
            val maxRatio = maxOf(topRatio, bottomRatio).coerceIn(0f, 1f)
            val heightRatioSpan = maxRatio - minRatio
            if (heightRatioSpan < 0.005f) return@withContext false

            // Horizontal sample offsets across the entire letter width margin (2% to 6% from edge)
            val xOffsets = if (side == EdgeSide.LEFT) {
                floatArrayOf(0.02f, 0.04f, 0.06f)
            } else {
                floatArrayOf(0.94f, 0.96f, 0.98f)
            }

            val sampleStepsY = 24
            var totalLuminance = 0f
            var sampleCount = 0

            for (i in 0 until sampleStepsY) {
                val normY = (minRatio + ((i + 0.5f) / sampleStepsY) * heightRatioSpan).coerceIn(0f, 1f)
                val centerY = (normY * bh).toInt().coerceIn(0, bh - 1)

                for (xFrac in xOffsets) {
                    val sx = (bw * xFrac).toInt().coerceIn(0, bw - 1)
                    val pixel = runCatching { bitmap.getPixel(sx, centerY) }.getOrDefault(0)
                    val r = (pixel shr 16 and 0xFF) / 255.0f
                    val g = (pixel shr 8 and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f
                    totalLuminance += (0.2126f * r + 0.7152f * g + 0.0722f * b)
                    sampleCount++
                }
            }

            val avgLuminance = if (sampleCount > 0) totalLuminance / sampleCount else 0f
            avgLuminance >= BRIGHTNESS_THRESHOLD
        }.getOrDefault(false)
    }
}
