// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

/**
 * Renders a circular collapse (reverse reveal / iris) animation towards [targetOffset].
 * Darkness closes in from the outer screen edges until converging at [targetOffset],
 * at which point [onAnimationEnd] is invoked to turn off the display.
 */
@Composable
fun ScreenOffEffect(
    targetOffset: Offset,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val path = remember { Path() }

    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 240,
                easing = FastOutSlowInEasing,
            ),
        )
        onAnimationEnd()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
    ) {
        val currentProgress = progress.value
        val center = if (targetOffset.x.isFinite() && targetOffset.y.isFinite() && targetOffset != Offset.Zero) {
            targetOffset
        } else {
            Offset(size.width / 2f, size.height / 2f)
        }

        val maxDistance = hypot(
            maxOf(center.x, size.width - center.x),
            maxOf(center.y, size.height - center.y),
        )
        val currentRadius = (maxDistance * (1f - currentProgress)).coerceAtLeast(0f)

        if (currentRadius > 0f) {
            path.reset()
            path.fillType = PathFillType.EvenOdd
            path.addRect(Rect(0f, 0f, size.width, size.height))
            path.addOval(Rect(center = center, radius = currentRadius))
            drawPath(path = path, color = Color.Black)

            drawCircle(
                color = Color.Black.copy(alpha = (currentProgress * 0.45f).coerceIn(0f, 1f)),
                center = center,
                radius = currentRadius,
            )
        } else {
            drawRect(color = Color.Black)
        }
    }
}
