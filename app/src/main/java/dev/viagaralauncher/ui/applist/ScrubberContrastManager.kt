// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.applist

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.viagaralauncher.data.EdgeSide
import dev.viagaralauncher.wallpaper.WallpaperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-performance, battery-friendly manager that samples background luminance along the
 * vertical edge strip where EdgeScrubber is placed.
 *
 * If a letter sits above a very bright/white part of the wallpaper, its entry in
 * the returned boolean array is true (signaling that it should be rendered in dark/black for contrast).
 * For dark, colored, or medium backgrounds, it remains false (rendering in normal style).
 */
object ScrubberContrastManager {

    private const val BRIGHTNESS_THRESHOLD = 0.72f // Only trigger black text on very bright/white backgrounds
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Composable
    fun rememberEdgeContrast(
        side: EdgeSide,
        lettersCount: Int,
        bandTopRatio: Float,
        bandBottomRatio: Float,
    ): State<BooleanArray> {
        val context = LocalContext.current
        val contrastState = remember(side, lettersCount) {
            mutableStateOf(BooleanArray(lettersCount) { false })
        }

        DisposableEffect(side, lettersCount, bandTopRatio, bandBottomRatio) {
            val wm = WallpaperManager.getInstance(context)

            fun computeContrast() {
                scope.launch {
                    val flags = sampleEdgeLuminance(
                        context = context,
                        side = side,
                        count = lettersCount,
                        topRatio = bandTopRatio,
                        bottomRatio = bandBottomRatio,
                    )
                    withContext(Dispatchers.Main) {
                        contrastState.value = flags
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
                WallpaperRepository.wallpaperUpdateTick.collect { tick ->
                    if (tick > 0L) {
                        computeContrast()
                    }
                }
            }

            onDispose {
                repoJob.cancel()
                if (listener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching { wm.removeOnColorsChangedListener(listener) }
                }
            }
        }

        return contrastState
    }

    private suspend fun sampleEdgeLuminance(
        context: Context,
        side: EdgeSide,
        count: Int,
        topRatio: Float,
        bottomRatio: Float,
    ): BooleanArray = withContext(Dispatchers.IO) {
        val result = BooleanArray(count) { false }
        if (count <= 0) return@withContext result

        val bitmap = runCatching {
            val wm = WallpaperManager.getInstance(context)
            val drawable = wm.drawable
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                null
            }
        }.getOrNull() ?: return@withContext result

        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0) return@withContext result

        // Sample near the edge (about 4% from the edge, within the scrubber strip)
        val sampleX = if (side == EdgeSide.LEFT) {
            (bw * 0.04f).toInt().coerceIn(0, bw - 1)
        } else {
            (bw * 0.96f).toInt().coerceIn(0, bw - 1)
        }

        val safeTopRatio = topRatio.coerceIn(0f, 1f)
        val safeBottomRatio = bottomRatio.coerceIn(safeTopRatio + 0.01f, 1f)
        val heightRatioSpan = safeBottomRatio - safeTopRatio

        for (i in 0 until count) {
            val normY = safeTopRatio + ((i + 0.5f) / count) * heightRatioSpan
            val sampleY = (normY * bh).toInt().coerceIn(0, bh - 1)

            val pixel = runCatching { bitmap.getPixel(sampleX, sampleY) }.getOrDefault(0)
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            // Relative luminance formula (ITU-R BT.709)
            val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (luminance >= BRIGHTNESS_THRESHOLD) {
                result[i] = true
            }
        }

        result
    }
}
