// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.clock

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.viagaralauncher.R
import dev.viagaralauncher.data.ClockPresetFont
import java.io.File

object GraceClockFonts {
    private val presetFamilies = mapOf(
        ClockPresetFont.Sacramento to FontFamily(Font(R.font.sacramento)),
        ClockPresetFont.Bokor to FontFamily(Font(R.font.bokor)),
        ClockPresetFont.Plaster to FontFamily(Font(R.font.plaster)),
        ClockPresetFont.Monoton to FontFamily(Font(R.font.monoton)),
        ClockPresetFont.LuckiestGuy to FontFamily(Font(R.font.luckiest_guy)),
    )

    fun family(font: ClockPresetFont): FontFamily =
        presetFamilies.getValue(font)

    fun resolveFont(context: Context, fontId: String?): FontFamily {
        if (fontId == null) return FontFamily.Default
        return when (fontId) {
            "serif" -> FontFamily.Serif
            "sans_serif" -> FontFamily.SansSerif
            "monospace" -> FontFamily.Monospace
            "cursive" -> presetFamilies[ClockPresetFont.Sacramento] ?: FontFamily.Cursive
            else -> {
                val store = ClockFontStore(context)
                val file = store.file(fontId)
                if (file != null && file.exists()) {
                    runCatching {
                        FontFamily(Typeface.createFromFile(file))
                    }.getOrDefault(FontFamily.Default)
                } else {
                    FontFamily.Default
                }
            }
        }
    }
}

@Composable
fun rememberClockFontFamily(fontId: String?): FontFamily {
    val context = LocalContext.current
    return remember(fontId) {
        GraceClockFonts.resolveFont(context, fontId)
    }
}
