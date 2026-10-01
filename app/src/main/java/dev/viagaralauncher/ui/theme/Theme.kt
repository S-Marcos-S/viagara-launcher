// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.theme

import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import dev.viagaralauncher.data.AppFont

fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.SANS_SERIF -> FontFamily.SansSerif
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONOSPACE -> FontFamily.Monospace
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1E0),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004F5B),
    onPrimaryContainer = Color(0xFF9EEFFE),
    background = Color.Transparent,
    surface = Color(0xFF1B2733),
    onSurface = Color(0xFFE3E8EC),
    surfaceVariant = Color(0xFF22303E),
    onSurfaceVariant = Color(0xFFB0BCC7),
    surfaceContainer = Color(0xFF1F2C39),
    surfaceContainerHigh = Color(0xFF263544),
    surfaceContainerHighest = Color(0xFF2C3E4F),
    surfaceContainerLow = Color(0xFF18232F),
    surfaceContainerLowest = Color(0xFF121B24),
    outline = Color(0xFF6F8191),
    outlineVariant = Color(0xFF384755),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF10707F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8EAFF),
    onPrimaryContainer = Color(0xFF001F25),
    background = Color.Transparent,
    surface = Color(0xFFF3F6F8),
    onSurface = Color(0xFF1B2733),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF404A50),
    surfaceContainer = Color(0xFFE8EEF1),
    surfaceContainerHigh = Color(0xFFE1E8EC),
    surfaceContainerHighest = Color(0xFFDBE2E6),
    surfaceContainerLow = Color(0xFFEFF4F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = Color(0xFF707B82),
    outlineVariant = Color(0xFFC0CCD2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun ViagaraTheme(
    font: AppFont = AppFont.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val wallpaperPrimary = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                val wm = WallpaperManager.getInstance(context)
                wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()?.let { Color(it) }
            }.getOrNull()
        } else null
    }

    // On Android 12+, use the system's Material You palette (derived from the user's
    // wallpaper) so Settings/dialogs feel like stock Android instead of a bespoke skin;
    // background stays transparent regardless, since the home screen relies on it to let
    // the real wallpaper show through.
    val baseColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val fallback = if (darkTheme) DarkColors else LightColors
        if (wallpaperPrimary != null) {
            fallback.copy(
                primary = wallpaperPrimary,
                primaryContainer = wallpaperPrimary.copy(alpha = 0.35f),
            )
        } else {
            fallback
        }
    }
    val colors = baseColors.copy(background = Color.Transparent)
    val baseTypography = MaterialTheme.typography
    val fontFamily = font.toFontFamily()
    val typography = baseTypography.copy(
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = fontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = fontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = fontFamily),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

/**
 * Calculates a rich frosted-glass surface color tinted with the active
 * dynamic Monet color (Material You theme), ensuring dialogs and floating surfaces
 * feel deeply integrated with the user's wallpaper rather than appearing as flat neutral grey.
 */
@Composable
fun dynamicSurfaceColor(
    alpha: Float = 0.90f,
    tintFraction: Float = 0.12f,
): Color {
    val colorScheme = MaterialTheme.colorScheme
    return lerp(colorScheme.surfaceContainerHigh, colorScheme.primary, tintFraction).copy(alpha = alpha)
}

/**
 * Calculates an elegant dynamic glowing border color harmonized with the Monet palette.
 */
@Composable
fun dynamicBorderColor(
    alpha: Float = 0.35f,
    tintFraction: Float = 0.40f,
): Color {
    val colorScheme = MaterialTheme.colorScheme
    return lerp(colorScheme.outlineVariant, colorScheme.primary, tintFraction).copy(alpha = alpha)
}