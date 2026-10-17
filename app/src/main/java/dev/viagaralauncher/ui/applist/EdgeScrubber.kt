// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.applist

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.data.EdgeSide
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Physics constants from GraceLauncher (AlphabetRail.kt) */
private const val WAVE_RADIUS_DP = 82f
private const val BASE_PULL_DP = 50f

/**
 * Ultra-high-performance Canvas-based A-Z strip with GraceLauncher fluid wave animation.
 *
 * Rather than creating 28+ separate Compose LayoutNodes, Boxes, and Text components with
 * individual graphicsLayer invalidation passes, the entire alphabet is rendered directly on a
 * single hardware-accelerated Canvas during the draw phase.
 *
 * Uses GraceLauncher physics: 82dp wave radius, 50dp base pull, extra horizontal pull,
 * and spring wave damping (0.86f / 700f).
 */
@Composable
fun EdgeScrubber(
    letters: List<Char>,
    scrubY: () -> Float?,
    pullPx: () -> Float,
    band: ScrubBand,
    side: EdgeSide,
    modifier: Modifier = Modifier,
    sidePaddingDp: Int = 20,
) {
    if (letters.isEmpty() || band.heightPx <= 0f) return

    val density = LocalDensity.current.density
    val textSizePx = with(LocalDensity.current) { 12.sp.toPx() }
    val waveRadiusPx = WAVE_RADIUS_DP * density
    val twoWaveRadiusSq = 2f * waveRadiusPx * waveRadiusPx
    val basePullPx = BASE_PULL_DP * density
    val insetPx = sidePaddingDp.coerceAtLeast(0) * density
    val baseGlyphWidthPx = 24f * density
    val halfGlyphWidthPx = baseGlyphWidthPx / 2f

    val currentY = scrubY()
    val isScrubbing = currentY != null
    val lastY = remember { floatArrayOf(0f) }

    val wave by animateFloatAsState(
        targetValue = if (isScrubbing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 700f),
        label = "alphabetWave",
    )

    val textPaint = remember(textSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.WHITE
        }
    }
    val textYOffset = remember(textPaint) {
        -(textPaint.descent() + textPaint.ascent()) / 2f
    }

    // Pre-calculate 5-pointed star path for SCRUBBER_STAR centered at (0, 0)
    val starPath = remember(density) {
        val outerRadius = 6.5f * density
        val innerRadius = 2.8f * density
        Path().apply {
            val points = 5
            val step = (Math.PI / points).toFloat()
            var angle = -Math.PI.toFloat() / 2f
            for (i in 0 until (points * 2)) {
                val r = if (i % 2 == 0) outerRadius else innerRadius
                val px = r * cos(angle)
                val py = r * sin(angle)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
                angle += step
            }
            close()
        }
    }

    // Dynamic contrast state sampled from wallpaper edge luminance
    val screenHeightPx = with(LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val topRatio = if (screenHeightPx > 0f) (band.topPx / screenHeightPx).coerceIn(0f, 1f) else 0.1f
    val bottomRatio = if (screenHeightPx > 0f) (band.bottomPx / screenHeightPx).coerceIn(topRatio, 1f) else 0.9f

    val isWallpaperBright by ScrubberContrastManager.rememberEdgeIsBright(
        side = side,
        bandTopRatio = topRatio,
        bandBottomRatio = bottomRatio,
    )

    Canvas(
        modifier = modifier
            .width(132.dp)
            .fillMaxHeight(),
    ) {
        val y = scrubY()
        if (y != null) {
            lastY[0] = y
        }
        val targetY = y ?: lastY[0]
        val pull = pullPx()
        val isLeft = side == EdgeSide.LEFT

        // Baseline X position centered inside the 24dp glyph column
        val baseX = if (isLeft) {
            insetPx + halfGlyphWidthPx
        } else {
            size.width - insetPx - halfGlyphWidthPx
        }

        val extraPull = (pull * 0.35f).coerceIn(0f, basePullPx)
        val count = letters.size
        for (index in 0 until count) {
            val c = letters[index]
            val centerY = ScrubberGeometry.letterCenterY(index, band.topPx, band.heightPx, count)

            val distance = centerY - targetY
            val gaussian = if (twoWaveRadiusSq > 0f) {
                exp(-(distance * distance) / twoWaveRadiusSq)
            } else {
                0f
            }

            val outward = (basePullPx + extraPull) * gaussian * wave
            val glyphX = if (isLeft) baseX + outward else baseX - outward
            val alpha = 0.60f + 0.40f * gaussian * wave

            if (c == SCRUBBER_STAR) {
                val starColor = if (isWallpaperBright) {
                    Color(0xFF10161C).copy(alpha = alpha.coerceAtLeast(0.85f))
                } else {
                    Color.White.copy(alpha = alpha)
                }
                translate(left = glyphX, top = centerY) {
                    drawPath(starPath, color = starColor)
                }
            } else {
                val a = if (isWallpaperBright) {
                    (alpha.coerceAtLeast(0.85f) * 255f).roundToInt().coerceIn(0, 255)
                } else {
                    (alpha * 255f).roundToInt().coerceIn(0, 255)
                }
                val targetColor = if (isWallpaperBright) {
                    android.graphics.Color.argb(a, 16, 22, 28)
                } else {
                    android.graphics.Color.argb(a, 255, 255, 255)
                }
                textPaint.color = targetColor
                drawContext.canvas.nativeCanvas.drawText(
                    c.toString(),
                    glyphX,
                    centerY + textYOffset,
                    textPaint,
                )
            }
        }
    }
}