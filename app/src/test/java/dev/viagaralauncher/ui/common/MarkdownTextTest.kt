// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.common

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun `plain text without markdown returns unchanged`() {
        val result = parseMarkdownInline("Texto simples sem marcadores")
        assertEquals("Texto simples sem marcadores", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `bold text with double asterisks applies bold style`() {
        val result = parseMarkdownInline("Aqui está **texto em negrito** legal")
        assertEquals("Aqui está texto em negrito legal", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals("texto em negrito", result.text.substring(span.start, span.end))
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test
    fun `italic text with single asterisks applies italic style`() {
        val result = parseMarkdownInline("Aqui está *texto em itálico* legal")
        assertEquals("Aqui está texto em itálico legal", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals("texto em itálico", result.text.substring(span.start, span.end))
        assertEquals(FontStyle.Italic, span.item.fontStyle)
    }

    @Test
    fun `inline code with backticks applies monospace style`() {
        val result = parseMarkdownInline("Execute `app-release.apk` agora")
        assertEquals("Execute  app-release.apk  agora", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals(FontFamily.Monospace, span.item.fontFamily)
    }

    @Test
    fun `mixed markdown with bold, italic, and code`() {
        val input = "- **Editor:** ajuste com *itálico* e `codigo` final"
        val result = parseMarkdownInline(input)
        assertTrue(result.text.contains("Editor:"))
        assertTrue(!result.text.contains("**"))
        assertTrue(!result.text.contains("`"))
        assertEquals(3, result.spanStyles.size)

        val boldSpan = result.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertTrue(boldSpan != null)
        assertEquals("Editor:", result.text.substring(boldSpan!!.start, boldSpan.end))

        val italicSpan = result.spanStyles.find { it.item.fontStyle == FontStyle.Italic }
        assertTrue(italicSpan != null)
        assertEquals("itálico", result.text.substring(italicSpan!!.start, italicSpan.end))

        val codeSpan = result.spanStyles.find { it.item.fontFamily == FontFamily.Monospace }
        assertTrue(codeSpan != null)
    }

    @Test
    fun `strikethrough text with double tildes applies lineThrough`() {
        val result = parseMarkdownInline("Versão ~~0.59.18~~ 0.59.21")
        assertEquals("Versão 0.59.18 0.59.21", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals(TextDecoration.LineThrough, span.item.textDecoration)
    }
}
