// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.applist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.data.EdgeSide
import kotlin.math.exp
import kotlin.math.roundToInt

/** Letters sit ~3 mm (19 dp) away from the screen edge. */
private const val EDGE_INSET_DP = 19f

/** How far the bulge pushes the column away from the edge at its peak. */
private const val BELL_AMPLITUDE_DP = 75f

/**
 * The A-Z strip. The letters never change size — the *column* bows outward around the
 * fingertip on a gaussian, so the alphabet traces a bell curve and settles flat again when
 * the finger lifts.
 *
 * [scrubY] and [pullPx] are read as lambdas inside a graphicsLayer so the whole strip
 * animates in the draw phase; recomposing 26 Text nodes per pointer move made this jitter.
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
    val spacingPx = band.heightPx / letters.size
    // Wide enough that a good stretch of the alphabet takes part in the curve.
    val sigmaPx = 2.6f * spacingPx.coerceAtLeast(1f)
    val bellPx = BELL_AMPLITUDE_DP * density
    val insetDp = sidePaddingDp.coerceAtLeast(0).dp

    Box(
        modifier = modifier
            .width(132.dp)
            .fillMaxHeight()
            .padding(
                start = if (side == EdgeSide.LEFT) insetDp else 0.dp,
                end = if (side != EdgeSide.LEFT) insetDp else 0.dp,
            ),
    ) {
        letters.forEachIndexed { index, c ->
            val centerY = ScrubberGeometry.letterCenterY(index, band.topPx, band.heightPx, letters.size)

            Box(
                modifier = Modifier
                    .align(if (side == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, (centerY - spacingPx / 2f).roundToInt()) }
                    .width(24.dp)
                    .graphicsLayer {
                        val y = scrubY()
                        val gain = if (y == null || sigmaPx <= 0f) {
                            0f
                        } else {
                            val d = y - centerY
                            exp(-(d * d) / (2f * sigmaPx * sigmaPx))
                        }

                        // Position only — the glyph is never scaled. Outward pushes inward toward screen center.
                        val outward = bellPx * gain + pullPx() * gain
                        translationX = if (side == EdgeSide.LEFT) outward else -outward
                        alpha = 0.60f + 0.40f * gain
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (c == SCRUBBER_STAR) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                } else {
                    Text(
                        text = c.toString(),
                        fontSize = 12.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}