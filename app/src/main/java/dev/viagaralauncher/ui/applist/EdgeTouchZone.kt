// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.applist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import dev.viagaralauncher.data.EdgeSide
import dev.viagaralauncher.service.HapticUtil
import kotlinx.coroutines.launch

/** How far the strip may be dragged inward before the pull stops growing. */
private const val MAX_PULL_DP = 400f

/**
 * The invisible strip along a screen edge that opens the app list and then scrubs it, so one
 * unbroken touch does both.
 *
 * Writes straight into [state] rather than reporting upward through callbacks: the letter
 * changes ~26 times per gesture, and routing that through the caller is what used to
 * recompose the home screen on every one of them.
 */
@Composable
fun EdgeTouchZone(
    side: EdgeSide,
    widthDp: Dp,
    letters: List<Char>,
    band: ScrubBand,
    hapticsEnabled: Boolean,
    state: ScrubState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val fromLeft = side == EdgeSide.LEFT

    Box(
        modifier = modifier
            .width(widthDp)
            .fillMaxHeight()
            .pointerInput(letters, band, hapticsEnabled, fromLeft) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (state.active) {
                        down.consume()
                        return@awaitEachGesture
                    }
                    down.consume()
                    state.begin(side)

                    var lastIndex = -1
                    fun report(x: Float, y: Float): Char? {
                        // Same geometry the visible strip uses, so the letter under the
                        // fingertip is the one that swells.
                        val index = ScrubberGeometry.indexForY(y, band.topPx, band.heightPx, letters.size)
                        val letter = letters.getOrNull(index)
                        if (index != lastIndex) {
                            lastIndex = index
                            HapticUtil.tick(view, hapticsEnabled)
                        }
                        // How far the finger has pulled in toward the middle of the screen.
                        val inward = if (fromLeft) x else (size.width - x)
                        state.update(y, inward.coerceIn(0f, MAX_PULL_DP * density), letter)
                        return letter
                    }

                    report(down.position.x, down.position.y)
                    onOpen()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // A tap places the list without ever counting as a scrub, so the
                        // overlay does not spend the tap fading itself out and back in.
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            state.markScrubbing()
                        }
                        report(change.position.x, change.position.y)
                        event.changes.forEach { it.consume() }
                    }
                    val releasedOnStar = letters.getOrNull(lastIndex) == SCRUBBER_STAR
                    if (releasedOnStar) {
                        state.cancel()
                        onDismiss()
                    } else {
                        scope.launch {
                            state.release()
                        }
                    }
                }
            },
    )
}