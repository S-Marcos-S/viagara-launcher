// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.visualizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance Jetpack Compose Audio Spectrum Visualizer.
 * Renders 24-32 logarithmic frequency bands with:
 * - Dynamic rounded bars (rounded pills)
 * - Independent floating peak markers
 * - Smooth 60 FPS transitions
 * - Seamless integration with Material 3 dynamic colors and wallpaper themes.
 */
@Composable
fun AudioVisualizerView(
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    peakColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = barColor.copy(alpha = 0.08f),
) {
    val frame by AudioVisualizerManager.frameFlow.collectAsState()

    AnimatedVisibility(
        visible = frame.hasAudio,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val bands = frame.bands
                val peaks = frame.peaks
                val count = bands.size
                if (count == 0) return@Canvas

                val canvasWidth = size.width
                val canvasHeight = size.height

                val totalSpacing = canvasWidth * 0.28f
                val spacing = totalSpacing / (count + 1)
                val barWidth = (canvasWidth - totalSpacing) / count
                val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                val peakHeight = (barWidth * 0.7f).coerceIn(2f, 4.dp.toPx())

                for (i in 0 until count) {
                    val x = spacing + i * (barWidth + spacing)
                    val rawLevel = bands[i].coerceIn(0f, 1f)
                    val peakLevel = peaks[i].coerceIn(0f, 1f)

                    val barHeight = (canvasHeight * rawLevel).coerceAtLeast(barWidth * 0.5f)
                    val barTop = canvasHeight - barHeight

                    // 1. Dim track placeholder
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, canvasHeight),
                        cornerRadius = cornerRadius,
                    )

                    // 2. Active spectrum bar
                    drawRoundRect(
                        color = barColor.copy(alpha = (0.55f + rawLevel * 0.45f).coerceIn(0.55f, 1f)),
                        topLeft = Offset(x, barTop),
                        size = Size(barWidth, barHeight),
                        cornerRadius = cornerRadius,
                    )

                    // 3. Floating peak line/pill
                    if (peakLevel > 0.04f && peakLevel >= rawLevel) {
                        val peakTop = (canvasHeight - (canvasHeight * peakLevel) - peakHeight).coerceAtLeast(0f)
                        drawRoundRect(
                            color = peakColor.copy(alpha = 0.95f),
                            topLeft = Offset(x, peakTop),
                            size = Size(barWidth, peakHeight),
                            cornerRadius = CornerRadius(peakHeight / 2f, peakHeight / 2f),
                        )
                    }
                }
            }
        }
    }
}
