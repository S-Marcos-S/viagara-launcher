// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Geometric single-stroke path definitions for handwritten numerals.
 * Coordinates are defined on a 100 x 160 unit bounding box with organic Bézier curves.
 */
object HandwrittenGlyphs {

    fun getStrokes(char: Char): List<Path> {
        val list = mutableListOf<Path>()
        when (char) {
            '0' -> {
                val p = Path()
                p.moveTo(56f, 18f)
                p.cubicTo(30f, 22f, 16f, 62f, 18f, 96f)
                p.cubicTo(20f, 126f, 32f, 146f, 52f, 146f)
                p.cubicTo(74f, 146f, 86f, 118f, 84f, 76f)
                p.cubicTo(82f, 38f, 68f, 16f, 48f, 16f)
                p.cubicTo(40f, 16f, 36f, 20f, 32f, 26f)
                list.add(p)
            }
            '1' -> {
                val p = Path()
                p.moveTo(28f, 48f)
                p.quadraticBezierTo(38f, 32f, 52f, 18f)
                p.cubicTo(52f, 55f, 49f, 105f, 48f, 146f)
                p.quadraticBezierTo(48f, 148f, 56f, 146f)
                list.add(p)
            }
            '2' -> {
                val p = Path()
                p.moveTo(24f, 46f)
                p.cubicTo(24f, 20f, 76f, 18f, 76f, 48f)
                p.cubicTo(76f, 76f, 38f, 112f, 22f, 144f)
                p.cubicTo(36f, 142f, 66f, 146f, 82f, 142f)
                list.add(p)
            }
            '3' -> {
                val p = Path()
                p.moveTo(26f, 36f)
                p.cubicTo(34f, 18f, 76f, 18f, 72f, 66f)
                p.cubicTo(70f, 76f, 52f, 80f, 46f, 82f)
                p.cubicTo(66f, 84f, 82f, 104f, 78f, 130f)
                p.cubicTo(74f, 150f, 38f, 150f, 24f, 136f)
                list.add(p)
            }
            '4' -> {
                val p1 = Path()
                p1.moveTo(64f, 18f)
                p1.lineTo(18f, 102f)
                p1.cubicTo(35f, 102f, 70f, 102f, 84f, 102f)
                list.add(p1)

                val p2 = Path()
                p2.moveTo(62f, 68f)
                p2.cubicTo(62f, 95f, 60f, 125f, 58f, 148f)
                list.add(p2)
            }
            '5' -> {
                val p = Path()
                p.moveTo(74f, 22f)
                p.lineTo(28f, 22f)
                p.lineTo(24f, 72f)
                p.cubicTo(38f, 64f, 80f, 70f, 76f, 114f)
                p.cubicTo(74f, 148f, 34f, 150f, 20f, 136f)
                list.add(p)
            }
            '6' -> {
                val p = Path()
                p.moveTo(68f, 24f)
                p.cubicTo(44f, 44f, 18f, 92f, 22f, 126f)
                p.cubicTo(26f, 150f, 76f, 148f, 76f, 116f)
                p.cubicTo(76f, 88f, 30f, 88f, 24f, 116f)
                list.add(p)
            }
            '7' -> {
                val p = Path()
                p.moveTo(24f, 30f)
                p.lineTo(32f, 22f)
                p.lineTo(78f, 22f)
                p.cubicTo(68f, 68f, 52f, 112f, 42f, 148f)
                list.add(p)
            }
            '8' -> {
                val p = Path()
                p.moveTo(50f, 78f)
                p.cubicTo(36f, 66f, 32f, 26f, 50f, 22f)
                p.cubicTo(68f, 22f, 66f, 64f, 50f, 78f)
                p.cubicTo(32f, 94f, 30f, 146f, 52f, 146f)
                p.cubicTo(72f, 146f, 70f, 96f, 50f, 78f)
                list.add(p)
            }
            '9' -> {
                val p = Path()
                p.moveTo(74f, 58f)
                p.cubicTo(74f, 22f, 24f, 22f, 24f, 58f)
                p.cubicTo(24f, 90f, 74f, 90f, 74f, 58f)
                p.cubicTo(74f, 95f, 68f, 130f, 46f, 148f)
                list.add(p)
            }
            ':' -> {
                val p1 = Path()
                p1.moveTo(46f, 56f)
                p1.cubicTo(46f, 51f, 54f, 51f, 54f, 56f)
                p1.cubicTo(54f, 61f, 46f, 61f, 46f, 56f)
                list.add(p1)

                val p2 = Path()
                p2.moveTo(46f, 102f)
                p2.cubicTo(46f, 97f, 54f, 97f, 54f, 102f)
                p2.cubicTo(54f, 107f, 46f, 107f, 46f, 102f)
                list.add(p2)
            }
            else -> {
                val p = Path()
                p.moveTo(48f, 80f)
                p.lineTo(52f, 80f)
                list.add(p)
            }
        }
        return list
    }

    fun createSquigglePath(width: Float, height: Float = 12f): Path {
        val p = Path()
        p.moveTo(0f, height * 0.5f)
        val step = width / 4f
        p.cubicTo(step * 0.4f, 0f, step * 0.7f, height, step, height * 0.5f)
        p.cubicTo(step * 1.4f, 0f, step * 1.7f, height, step * 2f, height * 0.5f)
        p.cubicTo(step * 2.4f, 0f, step * 2.7f, height, step * 3f, height * 0.5f)
        p.cubicTo(step * 3.4f, 0f, step * 3.7f, height, width, height * 0.5f)
        return p
    }
}

/**
 * Pre-measures and handles progressive path reveal via PathMeasure.
 */
class DigitStrokeData(val strokes: List<Path>) {
    val measures: List<PathMeasure> = strokes.map { stroke ->
        PathMeasure().apply { setPath(stroke, false) }
    }
    val lengths: List<Float> = measures.map { it.length }
    val totalLength: Float = lengths.sum()

    fun extractSegment(progress: Float, dst: Path): Offset? {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f || totalLength <= 0f) return null

        val targetDist = p * totalLength
        var accumulated = 0f
        var penTip: Offset? = null

        for (i in strokes.indices) {
            val len = lengths[i]
            if (len <= 0f) continue
            val measure = measures[i]

            if (accumulated + len <= targetDist) {
                dst.addPath(strokes[i])
                accumulated += len
                if (accumulated >= targetDist - 0.001f) {
                    penTip = measure.getPosition(len)
                }
            } else if (accumulated < targetDist) {
                val subDist = targetDist - accumulated
                val segmentPath = Path()
                measure.getSegment(0f, subDist, segmentPath, true)
                dst.addPath(segmentPath)
                penTip = measure.getPosition(subDist)
                accumulated += len
                break
            } else {
                break
            }
        }
        return penTip
    }
}

/**
 * Individual digit drawn entirely in Canvas with authentic write-on stroke animation.
 */
@Composable
fun HandwrittenAnimatedDigit(
    char: Char,
    staggerDelayMs: Int,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    strokeWidthDp: Dp = 3.4.dp,
    showPenTip: Boolean = true,
) {
    var lastChar by remember { mutableStateOf(char) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(char) {
        if (lastChar != char) {
            // Digit transitioned: quick wipe out old digit and write in new digit immediately
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 140, easing = LinearEasing),
            )
            lastChar = char
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            )
        } else {
            // First appearance: stagger delay creates left-to-right hand-drawing cascade
            if (staggerDelayMs > 0) {
                kotlinx.coroutines.delay(staggerDelayMs.toLong())
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 580, easing = FastOutSlowInEasing),
            )
        }
    }

    val strokeData = remember(lastChar) {
        DigitStrokeData(HandwrittenGlyphs.getStrokes(lastChar))
    }

    Canvas(modifier = modifier) {
        val p = progress.value
        if (p <= 0f) return@Canvas

        val scale = min(size.width / 100f, size.height / 160f)
        val offsetX = (size.width - 100f * scale) / 2f
        val offsetY = (size.height - 160f * scale) / 2f
        val strokePx = strokeWidthDp.toPx()

        val dstPath = Path()
        val penTip = strokeData.extractSegment(p, dstPath)

        translate(left = offsetX, top = offsetY) {
            scale(scale = scale, pivot = Offset.Zero) {
                // 1. Ambient paper bleed / subtle shadow stroke
                drawPath(
                    path = dstPath,
                    color = contentColor.copy(alpha = 0.16f),
                    style = Stroke(
                        width = (strokePx / scale) * 1.50f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                // 2. Crisp main ink pen stroke
                drawPath(
                    path = dstPath,
                    color = contentColor,
                    style = Stroke(
                        width = strokePx / scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                // 3. Glowing live ink nib/point during write-on
                if (showPenTip && penTip != null && p > 0.01f && p < 0.98f) {
                    drawCircle(
                        color = contentColor.copy(alpha = 0.35f),
                        radius = (strokePx / scale) * 1.6f,
                        center = penTip,
                    )
                    drawCircle(
                        color = contentColor,
                        radius = (strokePx / scale) * 0.85f,
                        center = penTip,
                    )
                }
            }
        }
    }
}

/**
 * Hand-drawn animated squiggle accent line underneath the clock.
 */
@Composable
fun HandwrittenSquiggle(
    widthDp: Dp,
    heightDp: Dp = 10.dp,
    contentColor: Color = Color.White,
    strokeWidthDp: Dp = 2.dp,
    delayMs: Int = 480,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) {
            kotlinx.coroutines.delay(delayMs.toLong())
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        )
    }

    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.toPx() }
    val heightPx = with(density) { heightDp.toPx() }
    val path = remember(widthPx, heightPx) {
        HandwrittenGlyphs.createSquigglePath(widthPx, heightPx)
    }
    val measure = remember(path) {
        PathMeasure().apply { setPath(path, false) }
    }
    val len = measure.length

    Canvas(modifier = modifier.size(widthDp, heightDp)) {
        val p = progress.value
        if (p > 0f && len > 0f) {
            val segment = Path()
            measure.getSegment(0f, p * len, segment, true)
            val strokePx = strokeWidthDp.toPx()
            drawPath(
                path = segment,
                color = contentColor.copy(alpha = 0.20f),
                style = Stroke(width = strokePx * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = segment,
                color = contentColor.copy(alpha = 0.85f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * Full handwritten clock widget on the home screen.
 */
@Composable
fun HandwrittenCanvasClockContent(
    hoursString: String,
    minutesString: String,
    dateString: String,
    contentColor: Color,
    widthFactor: Float,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val digitWidth = (46.dp * widthFactor).coerceIn(34.dp, 52.dp)
    val digitHeight = (72.dp * widthFactor).coerceIn(54.dp, 82.dp)
    val colonWidth = (18.dp * widthFactor).coerceIn(14.dp, 22.dp)
    val strokeWidth = (3.2.dp * widthFactor).coerceIn(2.4.dp, 3.6.dp)

    // Parse individual characters of the time display
    val hourChars = hoursString.toList()
    val minuteChars = minutesString.toList()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Time row: interactive, launches alarm/clock
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClockClick,
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Hours digits
            hourChars.forEachIndexed { idx, ch ->
                HandwrittenAnimatedDigit(
                    char = ch,
                    staggerDelayMs = idx * 110,
                    contentColor = contentColor,
                    strokeWidthDp = strokeWidth,
                    modifier = Modifier.size(digitWidth, digitHeight),
                )
            }

            // Colon separator
            HandwrittenAnimatedDigit(
                char = ':',
                staggerDelayMs = hourChars.size * 110,
                contentColor = contentColor.copy(alpha = 0.90f),
                strokeWidthDp = strokeWidth * 0.9f,
                modifier = Modifier.size(colonWidth, digitHeight),
            )

            // Minutes digits
            minuteChars.forEachIndexed { idx, ch ->
                HandwrittenAnimatedDigit(
                    char = ch,
                    staggerDelayMs = (hourChars.size + 1 + idx) * 110,
                    contentColor = contentColor,
                    strokeWidthDp = strokeWidth,
                    modifier = Modifier.size(digitWidth, digitHeight),
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Animated hand-drawn flourish squiggle
        val squiggleWidth = (140.dp * widthFactor).coerceIn(100.dp, 160.dp)
        HandwrittenSquiggle(
            widthDp = squiggleWidth,
            heightDp = 8.dp,
            contentColor = contentColor.copy(alpha = 0.75f),
            strokeWidthDp = (1.8.dp * widthFactor).coerceIn(1.4.dp, 2.2.dp),
            delayMs = 460,
        )

        Spacer(Modifier.height(4.dp))

        // Date text: launches calendar
        Text(
            text = dateString,
            color = contentColor.copy(alpha = 0.82f),
            fontSize = (13 * widthFactor).coerceAtLeast(11f).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDateClick,
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Compact preview for the ClockStylePickerScreen grid.
 */
@Composable
fun HandwrittenCanvasClockPreview(
    hoursString: String,
    minutesString: String,
    shortDateString: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val digitWidth = 14.dp
    val digitHeight = 22.dp
    val colonWidth = 7.dp
    val strokeWidth = 1.6.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            hoursString.forEach { ch ->
                HandwrittenAnimatedDigit(
                    char = ch,
                    staggerDelayMs = 0,
                    contentColor = tint,
                    strokeWidthDp = strokeWidth,
                    showPenTip = false,
                    modifier = Modifier.size(digitWidth, digitHeight),
                )
            }

            HandwrittenAnimatedDigit(
                char = ':',
                staggerDelayMs = 0,
                contentColor = tint.copy(alpha = 0.90f),
                strokeWidthDp = strokeWidth,
                showPenTip = false,
                modifier = Modifier.size(colonWidth, digitHeight),
            )

            minutesString.forEach { ch ->
                HandwrittenAnimatedDigit(
                    char = ch,
                    staggerDelayMs = 0,
                    contentColor = tint,
                    strokeWidthDp = strokeWidth,
                    showPenTip = false,
                    modifier = Modifier.size(digitWidth, digitHeight),
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = shortDateString,
            color = tint.copy(alpha = 0.75f),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
        )
    }
}
