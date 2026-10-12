// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.clock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DynamicClockContent(
    definition: RemoteClockStyleDefinition,
    currentTime: Date,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1.0f,
    onClockClick: () -> Unit = {},
    onDateClick: () -> Unit = {},
) {
    val context = LocalContext.current
    var customTimeFont by remember(definition.timeElement) { mutableStateOf<FontFamily?>(null) }
    var customDateFont by remember(definition.dateElement) { mutableStateOf<FontFamily?>(null) }

    LaunchedEffect(definition) {
        customTimeFont = ClockRepository.loadCustomFont(context, definition.timeElement)
        definition.dateElement?.let {
            customDateFont = ClockRepository.loadCustomFont(context, it)
        }
    }

    val align = when (definition.alignment.lowercase()) {
        "center" -> Alignment.CenterHorizontally
        "end" -> Alignment.End
        else -> horizontalAlignment
    }

    val columnMod = modifier
        .fillMaxWidth()
        .then(
            if (definition.containerPaddingDp > 0) {
                Modifier.padding(definition.containerPaddingDp.dp)
            } else Modifier
        )

    when (definition.layout.lowercase()) {
        "row" -> {
            Row(
                modifier = columnMod,
                horizontalArrangement = when (definition.alignment.lowercase()) {
                    "center" -> Arrangement.Center
                    "end" -> Arrangement.End
                    else -> Arrangement.Start
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RenderClockText(
                    element = definition.timeElement,
                    currentTime = currentTime,
                    defaultColor = contentColor,
                    customFont = customTimeFont,
                    scaleFactor = scaleFactor,
                    onClick = onClockClick,
                )
                if (definition.dateElement != null) {
                    Spacer(Modifier.padding(horizontal = (8 * scaleFactor).dp.coerceAtLeast(3.dp)))
                    RenderClockText(
                        element = definition.dateElement,
                        currentTime = currentTime,
                        defaultColor = contentColor.copy(alpha = 0.85f),
                        customFont = customDateFont,
                        scaleFactor = scaleFactor,
                        onClick = onDateClick,
                    )
                }
            }
        }
        else -> {
            // "column", "stacked", "centered", etc.
            Column(
                modifier = columnMod,
                horizontalAlignment = align,
            ) {
                RenderClockText(
                    element = definition.timeElement,
                    currentTime = currentTime,
                    defaultColor = contentColor,
                    customFont = customTimeFont,
                    scaleFactor = scaleFactor,
                    onClick = onClockClick,
                )

                if (definition.dateElement != null) {
                    Spacer(Modifier.height((4 * scaleFactor).dp.coerceAtLeast(2.dp)))
                    RenderClockText(
                        element = definition.dateElement,
                        currentTime = currentTime,
                        defaultColor = contentColor.copy(alpha = 0.82f),
                        customFont = customDateFont,
                        scaleFactor = scaleFactor,
                        onClick = onDateClick,
                    )
                }

                if (definition.secondaryElement != null) {
                    Spacer(Modifier.height((2 * scaleFactor).dp.coerceAtLeast(1.dp)))
                    RenderClockText(
                        element = definition.secondaryElement,
                        currentTime = currentTime,
                        defaultColor = contentColor.copy(alpha = 0.65f),
                        customFont = null,
                        scaleFactor = scaleFactor,
                        onClick = onDateClick,
                    )
                }
            }
        }
    }
}

@Composable
fun RenderClockText(
    element: ClockTextElement,
    currentTime: Date,
    defaultColor: Color,
    customFont: FontFamily?,
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1.0f,
    onClick: () -> Unit = {},
) {
    val text = remember(currentTime, element.format) {
        runCatching {
            val sdf = SimpleDateFormat(element.format, Locale.getDefault())
            val formatted = sdf.format(currentTime)
            if (formatted.isNotEmpty() && formatted[0].isLowerCase()) {
                formatted.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            } else formatted
        }.getOrDefault("12:00")
    }

    val currentMinute = remember(currentTime) {
        val cal = Calendar.getInstance().apply { time = currentTime }
        cal.get(Calendar.MINUTE)
    }

    val weight = when (element.fontWeight.lowercase()) {
        "thin" -> FontWeight.Thin
        "light" -> FontWeight.Light
        "medium" -> FontWeight.Medium
        "bold" -> FontWeight.Bold
        "extra_bold" -> FontWeight.ExtraBold
        "black" -> FontWeight.Black
        else -> FontWeight.Normal
    }

    val primaryColor = remember(element.colorHex, defaultColor) {
        element.colorHex?.let { parseHexColor(it) } ?: defaultColor
    }

    val glowColor = remember(element.glowColorHex) {
        element.glowColorHex?.let { parseHexColor(it) } ?: Color.Transparent
    }

    var textModifier = modifier
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )

    // Handle animations
    val trigger = element.animationTrigger.lowercase()
    val isMinuteTrigger = (trigger == "minute_change" || trigger == "minute" || trigger == "on_minute")

    when (element.animation.lowercase()) {
        "shimmer" -> {
            val sweepOffset = if (isMinuteTrigger) {
                // One-shot shimmer sweep triggered strictly when current minute updates
                val anim = remember { Animatable(-1.2f) }
                LaunchedEffect(currentMinute) {
                    anim.snapTo(-1.2f)
                    anim.animateTo(
                        targetValue = 2.2f,
                        animationSpec = tween(
                            durationMillis = element.animationDurationMs.coerceAtLeast(800),
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                anim.value
            } else {
                val shimmerTransition = rememberInfiniteTransition(label = "DynamicShimmer")
                val offset by shimmerTransition.animateFloat(
                    initialValue = -1.2f,
                    targetValue = 2.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(element.animationDurationMs.coerceAtLeast(800), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "SweepOffset",
                )
                offset
            }

            val highlightColor = element.secondaryColorHex?.let { parseHexColor(it) } ?: Color.White.copy(alpha = 0.85f)

            // Only draw shimmer if actively in visible sweeping range
            if (!isMinuteTrigger || sweepOffset in -1.19f..2.19f) {
                textModifier = textModifier.drawWithContent {
                    drawContent()
                    val width = size.width
                    val x = width * sweepOffset
                    val shimmerWidth = (width * 0.45f).coerceAtLeast(60f)
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            highlightColor,
                            Color.Transparent,
                        ),
                        start = Offset(x, 0f),
                        end = Offset(x + shimmerWidth, size.height),
                    )
                    drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                }
            }
        }
        "pulse" -> {
            if (isMinuteTrigger) {
                val anim = remember { Animatable(1.0f) }
                LaunchedEffect(currentMinute) {
                    anim.snapTo(0.45f)
                    anim.animateTo(
                        targetValue = 1.0f,
                        animationSpec = tween(
                            durationMillis = element.animationDurationMs.coerceAtLeast(600),
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                textModifier = textModifier.alpha(anim.value)
            } else {
                val pulseTransition = rememberInfiniteTransition(label = "DynamicPulse")
                val alpha by pulseTransition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(element.animationDurationMs.coerceAtLeast(600), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "PulseAlpha",
                )
                textModifier = textModifier.alpha(alpha)
            }
        }
        "flip" -> {
            if (isMinuteTrigger) {
                val anim = remember { Animatable(1.0f) }
                LaunchedEffect(currentMinute) {
                    anim.snapTo(0.92f)
                    anim.animateTo(
                        targetValue = 1.0f,
                        animationSpec = tween(
                            durationMillis = element.animationDurationMs.coerceAtLeast(600),
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                textModifier = textModifier.graphicsLayer {
                    scaleX = anim.value
                    scaleY = anim.value
                }
            } else {
                val flipTransition = rememberInfiniteTransition(label = "DynamicFlip")
                val scale by flipTransition.animateFloat(
                    initialValue = 0.96f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(element.animationDurationMs.coerceAtLeast(1000), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "FlipScale",
                )
                textModifier = textModifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }

    val style = when (element.fontStyle.lowercase()) {
        "italic" -> FontStyle.Italic
        else -> FontStyle.Normal
    }

    val family = customFont ?: when (element.fontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "sans_serif", "sans-serif" -> FontFamily.SansSerif
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    Text(
        text = text,
        fontSize = ((element.fontSizeSp * scaleFactor).coerceAtLeast(8f)).sp,
        fontWeight = weight,
        fontStyle = style,
        fontFamily = family,
        color = primaryColor,
        letterSpacing = (element.letterSpacingSp * scaleFactor).sp,
        modifier = textModifier,
    )
}

private fun parseHexColor(hex: String): Color {
    return runCatching {
        val clean = hex.removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> (0xFF000000 or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        Color(colorInt)
    }.getOrDefault(Color.White)
}
