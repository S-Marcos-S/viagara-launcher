// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val INLINE_TOKEN_REGEX = Regex(
    """(\*\*\*(.+?)\*\*\*|___(.+?)___|\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\)|\*(?!\s)(.+?)(?<!\s)\*|_(?!\s)(.+?)(?<!\s)_)"""
)

/**
 * Parses inline Markdown formatting (bold, italic, strikethrough, inline code, and links)
 * into an [AnnotatedString] with matching [SpanStyle]s.
 */
fun parseMarkdownInline(
    text: String,
    primaryColor: Color = Color.Unspecified,
    codeColor: Color = Color.Unspecified,
    codeBackgroundColor: Color = Color.Unspecified,
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        var cursor = 0
        for (match in INLINE_TOKEN_REGEX.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            val value = match.value
            when {
                (value.startsWith("***") && value.endsWith("***") && value.length >= 6) ||
                (value.startsWith("___") && value.endsWith("___") && value.length >= 6) -> {
                    val content = value.substring(3, value.length - 3)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                }
                (value.startsWith("**") && value.endsWith("**") && value.length >= 4) ||
                (value.startsWith("__") && value.endsWith("__") && value.length >= 4) -> {
                    val content = value.substring(2, value.length - 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                }
                value.startsWith("~~") && value.endsWith("~~") && value.length >= 4 -> {
                    val content = value.substring(2, value.length - 2)
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(content)
                    }
                }
                value.startsWith("`") && value.endsWith("`") && value.length >= 2 -> {
                    val content = value.substring(1, value.length - 1)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBackgroundColor,
                        )
                    ) {
                        append(" $content ")
                    }
                }
                value.startsWith("[") && value.contains("](") && value.endsWith(")") -> {
                    val label = value.substringAfter("[").substringBefore("](")
                    withStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline)) {
                        append(label)
                    }
                }
                (value.startsWith("*") && value.endsWith("*") && value.length >= 2) ||
                (value.startsWith("_") && value.endsWith("_") && value.length >= 2) -> {
                    val content = value.substring(1, value.length - 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                }
                else -> {
                    append(value)
                }
            }
            cursor = end
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

/**
 * Renders multi-line Markdown text (such as changelogs and release notes) with support for:
 * - Headers (`# `, `## `, `### `, `#### `)
 * - Bullet lists (`- `, `* `, with nested indentation)
 * - Numbered lists (`1. `, `2. `)
 * - Horizontal dividers (`---`, `***`)
 * - Bold (`**bold**`), Italic (`*italic*`), Strikethrough (`~~strike~~`), Code (`` `code` ``)
 */
@Composable
fun ChangelogMarkdownViewer(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val colorScheme = MaterialTheme.colorScheme
    val lines = markdown.lines()

    Column(modifier = modifier) {
        lines.forEach { rawLine ->
            val trimmed = rawLine.trim()

            if (trimmed.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                return@forEach
            }

            if (trimmed in listOf("---", "***", "___")) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                return@forEach
            }

            if (trimmed.startsWith("#### ")) {
                Text(
                    text = parseMarkdownInline(
                        text = trimmed.removePrefix("#### ").trim(),
                        primaryColor = colorScheme.primary,
                        codeColor = colorScheme.primary,
                        codeBackgroundColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                return@forEach
            }

            if (trimmed.startsWith("### ")) {
                Text(
                    text = parseMarkdownInline(
                        text = trimmed.removePrefix("### ").trim(),
                        primaryColor = colorScheme.primary,
                        codeColor = colorScheme.primary,
                        codeBackgroundColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                return@forEach
            }

            if (trimmed.startsWith("## ")) {
                Text(
                    text = parseMarkdownInline(
                        text = trimmed.removePrefix("## ").trim(),
                        primaryColor = colorScheme.primary,
                        codeColor = colorScheme.primary,
                        codeBackgroundColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                return@forEach
            }

            if (trimmed.startsWith("# ")) {
                Text(
                    text = parseMarkdownInline(
                        text = trimmed.removePrefix("# ").trim(),
                        primaryColor = colorScheme.primary,
                        codeColor = colorScheme.primary,
                        codeBackgroundColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                return@forEach
            }

            val indentSpaces = rawLine.takeWhile { it == ' ' || it == '\t' }.length
            val indentLevel = (indentSpaces / 2).coerceIn(0, 4)

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                val bulletChar = if (indentLevel == 0) "•" else "◦"
                val bulletColor = if (indentLevel == 0) colorScheme.primary else colorScheme.onSurfaceVariant
                val content = trimmed.substring(2).trimStart()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (indentLevel * 12).dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = bulletChar,
                        color = bulletColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = parseMarkdownInline(
                            text = content,
                            primaryColor = colorScheme.primary,
                            codeColor = colorScheme.primary,
                            codeBackgroundColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                return@forEach
            }

            val numberMatch = Regex("""^(\d+)\.\s+(.*)$""").find(trimmed)
            if (numberMatch != null) {
                val number = numberMatch.groupValues[1]
                val content = numberMatch.groupValues[2]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (indentLevel * 12).dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "$number.",
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = parseMarkdownInline(
                            text = content,
                            primaryColor = colorScheme.primary,
                            codeColor = colorScheme.primary,
                            codeBackgroundColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                return@forEach
            }

            // Normal paragraph line
            Text(
                text = parseMarkdownInline(
                    text = trimmed,
                    primaryColor = colorScheme.primary,
                    codeColor = colorScheme.primary,
                    codeBackgroundColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}
