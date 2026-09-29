// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.DailyQuote

internal data class AutoSizedQuoteResult(
    val quoteFontSize: TextUnit,
    val quoteLineHeight: TextUnit,
    val authorFontSize: TextUnit,
    val maxLines: Int,
    val spacerHeightDp: Dp,
)

internal fun calculateAutoSizedQuote(
    textMeasurer: TextMeasurer,
    quote: DailyQuote,
    maxWidthPx: Int,
    widthFactor: Float,
    baseQuoteFontSizeSp: Float,
    minQuoteFontSizeSp: Float,
    baseAuthorFontSizeSp: Float,
    minAuthorFontSizeSp: Float,
    targetMaxLines: Int,
    maxAllowedLines: Int,
    baseTextStyle: TextStyle,
    authorFontWeight: FontWeight,
): AutoSizedQuoteResult {
    val fallbackQuoteSp = (baseQuoteFontSizeSp * widthFactor).coerceAtLeast(minQuoteFontSizeSp)
    val fallbackAuthorSp = (baseAuthorFontSizeSp * widthFactor).coerceAtLeast(minAuthorFontSizeSp)

    if (maxWidthPx <= 0) {
        return AutoSizedQuoteResult(
            quoteFontSize = fallbackQuoteSp.sp,
            quoteLineHeight = (fallbackQuoteSp * 1.30f).sp,
            authorFontSize = fallbackAuthorSp.sp,
            maxLines = targetMaxLines,
            spacerHeightDp = 4.dp,
        )
    }

    val quoteText = "“${quote.quote}”"
    val authorText = "— ${quote.author}"

    val baseQuoteSp = (baseQuoteFontSizeSp * widthFactor).coerceAtLeast(minQuoteFontSizeSp)
    val minQuoteSp = (minQuoteFontSizeSp * widthFactor).coerceAtLeast(7.0f)

    var chosenQuoteSp = minQuoteSp
    var chosenMaxLines = targetMaxLines
    var quoteFitted = false

    val step = 0.5f

    // 1. Try to fit in targetMaxLines (e.g. 4 lines) stepping down from baseQuoteSp
    var candidateQuoteSp = baseQuoteSp
    while (candidateQuoteSp >= minQuoteSp - 0.01f) {
        val lineH = (candidateQuoteSp * 1.30f).sp
        val style = baseTextStyle.copy(
            fontSize = candidateQuoteSp.sp,
            lineHeight = lineH,
            fontStyle = FontStyle.Italic,
        )
        val result = textMeasurer.measure(
            text = quoteText,
            style = style,
            constraints = Constraints(maxWidth = maxWidthPx),
        )
        if (!result.hasVisualOverflow && result.lineCount <= targetMaxLines) {
            chosenQuoteSp = candidateQuoteSp
            chosenMaxLines = targetMaxLines
            quoteFitted = true
            break
        }
        candidateQuoteSp -= step
    }

    // 2. If it did not fit in targetMaxLines, try allowing up to maxAllowedLines (e.g. 5 lines)
    if (!quoteFitted && maxAllowedLines > targetMaxLines) {
        candidateQuoteSp = (baseQuoteSp - 0.5f).coerceAtLeast(minQuoteSp)
        while (candidateQuoteSp >= minQuoteSp - 0.01f) {
            val lineH = (candidateQuoteSp * 1.25f).sp
            val style = baseTextStyle.copy(
                fontSize = candidateQuoteSp.sp,
                lineHeight = lineH,
                fontStyle = FontStyle.Italic,
            )
            val result = textMeasurer.measure(
                text = quoteText,
                style = style,
                constraints = Constraints(maxWidth = maxWidthPx),
            )
            if (!result.hasVisualOverflow && result.lineCount <= maxAllowedLines) {
                chosenQuoteSp = candidateQuoteSp
                chosenMaxLines = maxAllowedLines
                quoteFitted = true
                break
            }
            candidateQuoteSp -= step
        }
    }

    // 3. Fallback: if it still needs more space on an extremely constrained screen,
    // use minQuoteSp and expand lines so the phrase is never truncated
    if (!quoteFitted) {
        val lineH = (minQuoteSp * 1.25f).sp
        val style = baseTextStyle.copy(
            fontSize = minQuoteSp.sp,
            lineHeight = lineH,
            fontStyle = FontStyle.Italic,
        )
        val result = textMeasurer.measure(
            text = quoteText,
            style = style,
            constraints = Constraints(maxWidth = maxWidthPx),
        )
        chosenQuoteSp = minQuoteSp
        chosenMaxLines = result.lineCount.coerceAtLeast(maxAllowedLines)
    }

    // Author font size: scale down if needed so author fits in 1 line without overflow
    val baseAuthorSp = (baseAuthorFontSizeSp * widthFactor)
        .coerceAtMost(chosenQuoteSp * 0.90f)
        .coerceAtLeast(minAuthorFontSizeSp)
    val minAuthorSp = (minAuthorFontSizeSp * widthFactor).coerceAtLeast(6.5f)

    var chosenAuthorSp = minAuthorSp
    var candidateAuthorSp = baseAuthorSp
    while (candidateAuthorSp >= minAuthorSp - 0.01f) {
        val style = baseTextStyle.copy(
            fontSize = candidateAuthorSp.sp,
            fontWeight = authorFontWeight,
        )
        val result = textMeasurer.measure(
            text = authorText,
            style = style,
            constraints = Constraints(maxWidth = maxWidthPx),
        )
        if (!result.hasVisualOverflow && result.lineCount <= 1) {
            chosenAuthorSp = candidateAuthorSp
            break
        }
        candidateAuthorSp -= step
    }

    val spacerDp = if (chosenMaxLines >= 5 || chosenQuoteSp < 10.5f) 2.dp else 4.dp
    val lineMultiplier = if (chosenMaxLines >= 5) 1.25f else 1.30f

    return AutoSizedQuoteResult(
        quoteFontSize = chosenQuoteSp.sp,
        quoteLineHeight = (chosenQuoteSp * lineMultiplier).sp,
        authorFontSize = chosenAuthorSp.sp,
        maxLines = chosenMaxLines,
        spacerHeightDp = spacerDp,
    )
}

/**
 * Exibe a frase do dia com cálculo dinâmico de tamanho de fonte e altura de linha,
 * garantindo que mesmo frases longas caibam inteiramente no espaço destinado sem reticências (...).
 */
@Composable
internal fun AutoSizedDailyQuoteView(
    quote: DailyQuote,
    contentColor: Color,
    alignRight: Boolean,
    widthFactor: Float,
    baseQuoteFontSizeSp: Float,
    minQuoteFontSizeSp: Float,
    baseAuthorFontSizeSp: Float,
    minAuthorFontSizeSp: Float,
    targetMaxLines: Int,
    maxAllowedLines: Int,
    modifier: Modifier = Modifier,
    authorFontWeight: FontWeight = FontWeight.SemiBold,
    authorAlpha: Float = 0.65f,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maxWidthPx = if (maxWidth.isSpecified && maxWidth > 0.dp && maxWidth != Dp.Infinity) {
            with(density) { maxWidth.roundToPx() }
        } else {
            0
        }
        val fontScale = density.fontScale
        val baseTextStyle = LocalTextStyle.current

        val layout = remember(
            quote.quote,
            quote.author,
            maxWidthPx,
            fontScale,
            widthFactor,
            baseTextStyle.fontFamily,
            targetMaxLines,
            maxAllowedLines,
        ) {
            calculateAutoSizedQuote(
                textMeasurer = textMeasurer,
                quote = quote,
                maxWidthPx = maxWidthPx,
                widthFactor = widthFactor,
                baseQuoteFontSizeSp = baseQuoteFontSizeSp,
                minQuoteFontSizeSp = minQuoteFontSizeSp,
                baseAuthorFontSizeSp = baseAuthorFontSizeSp,
                minAuthorFontSizeSp = minAuthorFontSizeSp,
                targetMaxLines = targetMaxLines,
                maxAllowedLines = maxAllowedLines,
                baseTextStyle = baseTextStyle,
                authorFontWeight = authorFontWeight,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = "“${quote.quote}”",
                color = contentColor.copy(alpha = 0.9f),
                fontSize = layout.quoteFontSize,
                fontStyle = FontStyle.Italic,
                lineHeight = layout.quoteLineHeight,
                maxLines = layout.maxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
            )

            Spacer(Modifier.height(layout.spacerHeightDp))

            Text(
                text = "— ${quote.author}",
                color = contentColor.copy(alpha = authorAlpha),
                fontSize = layout.authorFontSize,
                fontWeight = authorFontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
            )
        }
    }
}
