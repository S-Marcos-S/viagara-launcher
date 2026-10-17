// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.clock

import android.graphics.Typeface
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.data.CustomClockStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun ClockFace(
    time: String,
    style: CustomClockStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    textAlign: TextAlign = TextAlign.Start,
) {
    val face = style.face
    val family = style.layout.presetFont?.let { GraceClockFonts.family(it) }
        ?: rememberClockFontFamily(face.fontId)
    val scale = fontScale.coerceIn(0.1f, 1f)
    ClockGlyphContent(
        value = time,
        semanticsText = clockFaceText(time, style),
        family = family,
        weight = FontWeight(face.weight),
        fontSize = face.size * scale,
        digitSpacing = face.letterSpacing * scale,
        groupSpacing = face.hourMinuteSpacing * scale,
        stacked = style.layout.stacked,
        showColon = face.showColon,
        separate = face.separateDigits,
        color = color,
        shadow = clockFontShadow(face.fontShadow, scale),
        modifier = modifier,
        textAlign = textAlign,
    )
}

@Composable
fun ClockTimeLabel(
    time: String,
    style: CustomClockStyle,
    textStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val face = style.face
    val size = textStyle.fontSize.value
    val scale = size / face.size
    ClockGlyphContent(
        value = time,
        semanticsText = clockFaceText(time, style),
        family = textStyle.fontFamily ?: FontFamily.Default,
        weight = textStyle.fontWeight ?: FontWeight.Normal,
        fontSize = size,
        digitSpacing = face.letterSpacing * scale,
        groupSpacing = face.hourMinuteSpacing * scale,
        stacked = false,
        showColon = face.showColon,
        separate = false,
        color = color,
        shadow = clockFontShadow(face.fontShadow, scale),
        modifier = modifier,
    )
}

@Composable
private fun clockFontShadow(strength: Int, scale: Float): Shadow {
    if (strength <= 0) return Shadow.None
    val amount = strength.coerceIn(0, 24)
    val density = LocalDensity.current
    return Shadow(
        color = Color.Black.copy(alpha = 0.12f + 0.38f * amount / 24f),
        offset = Offset(0f, with(density) { (amount * scale / 8f).dp.toPx() }),
        blurRadius = with(density) { (amount * scale).dp.toPx() },
    )
}

@Composable
private fun ClockGlyphContent(
    value: String,
    semanticsText: String,
    family: FontFamily,
    weight: FontWeight,
    fontSize: Float,
    digitSpacing: Float,
    groupSpacing: Float,
    stacked: Boolean,
    showColon: Boolean,
    separate: Boolean,
    color: Color,
    shadow: Shadow,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val typeface = LocalFontFamilyResolver.current.resolve(family, weight, FontStyle.Normal, FontSynthesis.All).value as Typeface
    val density = LocalDensity.current
    val fontPx = with(density) { fontSize.sp.toPx() }
    val digitGapPx = with(density) { digitSpacing.sp.toPx() }
    val groupGapPx = with(density) { groupSpacing.sp.toPx() }
    val centered = textAlign == TextAlign.Center
    BoxWithConstraints(
        modifier.semantics { text = AnnotatedString(semanticsText) },
        contentAlignment = if (centered) Alignment.TopCenter else Alignment.TopStart,
    ) {
        val availableWidth = constraints.maxWidth.toFloat()
        val availableHeight = constraints.maxHeight.toFloat()
        val drawing = remember(value, typeface, fontPx, digitGapPx, groupGapPx, stacked, showColon, centered, availableWidth, availableHeight) {
            measureClockGlyphs(value, typeface, fontPx, digitGapPx, groupGapPx, stacked, showColon, centered, availableWidth, availableHeight)
        }
        Layout(
            content = {},
            modifier = Modifier.drawBehind { drawClockGlyphs(drawing, color, shadow, separate) },
        ) { _, limits ->
            val width = limits.constrainWidth(ceil(drawing.bounds.width()).toInt().coerceAtLeast(0))
            val height = limits.constrainHeight(ceil(drawing.bounds.height()).toInt().coerceAtLeast(0))
            layout(
                width,
                height,
                mapOf(
                    FirstBaseline to (-drawing.bounds.top).roundToInt(),
                    LastBaseline to (drawing.lastBaseline - drawing.bounds.top).roundToInt(),
                ),
            ) {}
        }
    }
}

fun clockFaceText(time: String, style: CustomClockStyle): String = when {
    style.layout.stacked -> time.replace(':', '\n')
    style.face.showColon -> time
    else -> time.replace(':', ' ')
}
