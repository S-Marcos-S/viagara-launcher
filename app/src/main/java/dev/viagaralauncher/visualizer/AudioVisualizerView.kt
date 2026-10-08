// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.visualizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance Jetpack Compose Audio Spectrum Visualizer.
 * Faithfully reproduces the SDWMP3_CN VuMixer layout, geometry, and visual dynamics:
 * - 14 dual-channel strips (Left and Right channels) with right channel attenuated by 0.92f
 * - Strip width computed using SDWMP3_CN geometry: gap = 3.5.dp, half = stripW / 2 - 1.dp
 * - Floating peak indicators with instant rise and continuous asymptotic fall
 * - Smooth fade-in/fade-out transitions on audio activity changes
 * - Seamless integration as a background layer in NowPlayingWidget with Material 3 dynamic colors.
 */
@Composable
fun AudioVisualizerView(
    modifier: Modifier = Modifier,
    height: Dp = Dp.Unspecified,
    barColor: Color = MaterialTheme.colorScheme.primary,
    peakColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = barColor.copy(alpha = 0.05f),
) {
    val frame by AudioVisualizerManager.frameFlow.collectAsState()

    val alphaAnim by animateFloatAsState(
        targetValue = if (frame.hasAudio) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "visualizerFade",
    )

    if (alphaAnim <= 0.001f) return

    val boxModifier = if (height != Dp.Unspecified) {
        modifier.fillMaxWidth().height(height)
    } else {
        modifier
    }

    Box(modifier = boxModifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = alphaAnim }
        ) {
            val bands = frame.bands
            val peaks = frame.peaks
            val strips = bands.size
            if (strips == 0) return@Canvas

            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // SDWMP3_CN VuMixer strip geometry
            val gap = 3.5f * density
            val stripW = ((w - gap * (strips - 1) - gap * 2) / strips).coerceAtLeast(3f * density)
            val cr = stripW * 0.4f
            val half = (stripW / 2f - 1f * density).coerceAtLeast(1.5f * density)
            val peakBlockHeight = 3f * density
            val peakCr = 1.5f * density

            // Center strips within available canvas width
            val totalWidth = strips * stripW + (strips - 1) * gap
            val startX = (w - totalWidth) / 2f

            // Vertical dimensions adapting dynamically to widget height
            val bottomMargin = 4f * density
            val baselineY = h - bottomMargin
            val maxBarHeight = (baselineY - 4f * density).coerceAtLeast(stripW)

            for (i in 0 until strips) {
                val v = bands[i]
                val pk = peaks[i]

                val x = startX + i * (stripW + gap)
                val rx = x + stripW / 2f + 1f * density

                // 1. Subtle guide tracks (if enabled)
                if (trackColor.alpha > 0f) {
                    val trackAlpha = trackColor.alpha * 0.4f
                    drawRoundRect(
                        color = trackColor.copy(alpha = trackAlpha),
                        topLeft = Offset(x, baselineY - maxBarHeight),
                        size = Size(half, maxBarHeight),
                        cornerRadius = CornerRadius(cr, cr),
                    )
                    drawRoundRect(
                        color = trackColor.copy(alpha = trackAlpha * 0.9f),
                        topLeft = Offset(rx, baselineY - maxBarHeight),
                        size = Size(half, maxBarHeight),
                        cornerRadius = CornerRadius(cr, cr),
                    )
                }

                // 2. Left channel main strip (SDWMP3_CN: lh > 1f)
                val lh = (v * maxBarHeight).coerceAtLeast(0f)
                if (lh > 1f) {
                    val stripAlpha = (0.35f + v * 0.65f).coerceIn(0.2f, 0.95f)
                    drawRoundRect(
                        color = barColor.copy(alpha = barColor.alpha * stripAlpha),
                        topLeft = Offset(x, baselineY - lh),
                        size = Size(half, lh),
                        cornerRadius = CornerRadius(cr, cr),
                    )
                }

                // 3. Left channel peak block (SDWMP3_CN: pk > 0.03f && pkH > 2f)
                val pkH = (pk * maxBarHeight).coerceAtLeast(0f)
                if (pk > 0.03f && pkH > 2f) {
                    val peakTop = (baselineY - pkH - 2f * density).coerceAtLeast(0f)
                    drawRoundRect(
                        color = peakColor.copy(alpha = 0.88f),
                        topLeft = Offset(x, peakTop),
                        size = Size(half, peakBlockHeight),
                        cornerRadius = CornerRadius(peakCr, peakCr),
                    )
                }

                // 4. Right channel main strip (SDWMP3_CN: rv = v * 0.92f, slightly dimmer)
                val rv = v * 0.92f
                val rh = (rv * maxBarHeight).coerceAtLeast(0f)
                if (rh > 1f) {
                    val rightStripAlpha = (0.35f + rv * 0.65f) * 0.9f
                    drawRoundRect(
                        color = barColor.copy(alpha = (barColor.alpha * rightStripAlpha).coerceIn(0.18f, 0.85f)),
                        topLeft = Offset(rx, baselineY - rh),
                        size = Size(half, rh),
                        cornerRadius = CornerRadius(cr, cr),
                    )
                }

                // 5. Right channel peak block (SDWMP3_CN: rpk = pk * 0.92f)
                val rpk = (pk * 0.92f).coerceIn(0f, 1f)
                val rpkH = (rpk * maxBarHeight).coerceAtLeast(0f)
                if (rpk > 0.03f && rpkH > 2f) {
                    val rightPeakTop = (baselineY - rpkH - 2f * density).coerceAtLeast(0f)
                    drawRoundRect(
                        color = peakColor.copy(alpha = 0.78f),
                        topLeft = Offset(rx, rightPeakTop),
                        size = Size(half, peakBlockHeight),
                        cornerRadius = CornerRadius(peakCr, peakCr),
                    )
                }
            }
        }
    }
}
