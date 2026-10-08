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
 * Dynamically adapts to the host container (e.g. Now Playing widget) without fixed bounds:
 * - Adapts bar count (16, 24, 32) and spacing dynamically to available canvas width
 * - Samples/averages the 32 daemon frequency bands without modifying the backend daemon
 * - Smooth entrance and exit alpha transitions on audio activity changes
 * - Renders behind foreground content with subtle, readable transparency
 * - Respects rounded corners and widget layout padding.
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
            val totalBands = bands.size
            if (totalBands == 0) return@Canvas

            val canvasWidth = size.width
            val canvasHeight = size.height
            if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

            // Dynamic bar count selection based on available dp width
            val densityDpWidth = canvasWidth / density
            val targetCount = when {
                densityDpWidth < 180f -> 16
                densityDpWidth < 260f -> 24
                else -> 32
            }

            // Downsample/aggregate 32 daemon frequency bands to targetCount bars cleanly
            val displayBands = FloatArray(targetCount)
            val displayPeaks = FloatArray(targetCount)

            for (i in 0 until targetCount) {
                val startBin = (i * totalBands) / targetCount
                val endBin = (((i + 1) * totalBands) / targetCount).coerceAtLeast(startBin + 1)
                var sumBand = 0f
                var sumPeak = 0f
                for (b in startBin until endBin) {
                    sumBand += bands[b]
                    sumPeak += peaks[b]
                }
                val binCount = (endBin - startBin).toFloat()
                displayBands[i] = (sumBand / binCount).coerceIn(0f, 1f)
                displayPeaks[i] = (sumPeak / binCount).coerceIn(0f, 1f)
            }

            // Internal horizontal margins to stay within rounded bounds & padding
            val horizontalPadding = (12f * density).coerceAtMost(canvasWidth * 0.08f)
            val usableWidth = (canvasWidth - 2 * horizontalPadding).coerceAtLeast(10f)

            // Dynamic bar width and spacing
            val spacingRatio = 0.38f
            val rawBarWidth = usableWidth / (targetCount + (targetCount - 1) * spacingRatio)
            val barWidth = rawBarWidth.coerceIn(2.5f * density, 8f * density)
            val spacing = (barWidth * spacingRatio).coerceAtLeast(1.5f * density)
            val totalBarsWidth = targetCount * barWidth + (targetCount - 1) * spacing
            val startX = (canvasWidth - totalBarsWidth) / 2f

            val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val peakHeight = (barWidth * 0.75f).coerceIn(2f * density, 4f * density)

            // Vertical dimensions adapting dynamically to widget height
            val bottomMargin = (4f * density).coerceAtMost(canvasHeight * 0.08f)
            val baselineY = canvasHeight - bottomMargin
            val maxBarHeight = (canvasHeight * 0.82f).coerceAtLeast(barWidth)

            for (i in 0 until targetCount) {
                val x = startX + i * (barWidth + spacing)
                val rawLevel = displayBands[i]
                val peakLevel = displayPeaks[i]

                val minVisualHeight = 1.5f * density
                val barHeight = if (rawLevel > 0.005f) {
                    (maxBarHeight * rawLevel).coerceAtLeast(minVisualHeight)
                } else {
                    0f
                }
                val barTop = baselineY - barHeight

                // 1. Subtle track placeholder (optional background guide)
                if (trackColor.alpha > 0f) {
                    drawRoundRect(
                        color = trackColor.copy(alpha = trackColor.alpha * 0.4f),
                        topLeft = Offset(x, baselineY - maxBarHeight),
                        size = Size(barWidth, maxBarHeight),
                        cornerRadius = cornerRadius,
                    )
                }

                // 2. Integrated background spectrum bar
                if (barHeight > 0f) {
                    val barAlpha = (0.16f + rawLevel * 0.52f).coerceIn(0.16f, 0.75f)
                    drawRoundRect(
                        color = barColor.copy(alpha = barAlpha),
                        topLeft = Offset(x, barTop),
                        size = Size(barWidth, barHeight),
                        cornerRadius = cornerRadius,
                    )
                }

                // 3. Floating peak indicator
                if (peakLevel > 0.05f && peakLevel >= rawLevel && barHeight > 0f) {
                    val peakTop = (baselineY - (maxBarHeight * peakLevel) - peakHeight).coerceAtLeast(0f)
                    drawRoundRect(
                        color = peakColor.copy(alpha = 0.85f),
                        topLeft = Offset(x, peakTop),
                        size = Size(barWidth, peakHeight),
                        cornerRadius = CornerRadius(peakHeight / 2f, peakHeight / 2f),
                    )
                }
            }
        }
    }
}
