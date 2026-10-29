// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.theme.glass

import android.graphics.Color

/**
 * Visual configuration for the refractive liquid and frosted glass shader effects.
 * Direct port of PixelLauncherEnhanced glass architecture.
 */
data class GlassStyle(
    val refraction: Float = 1f,
    val depth: Float = 1f,
    val dispersion: Float = 1f,
    val vibrancy: Float = 0f,
    val rim: Float = 1f,
    val shadow: Float = 1f,
    val outlineWidth: Float = 1f,
    val outlineOpacity: Float = 0.20f,
    val outlineColor: Int = Color.WHITE,
    val dimFactor: Float = 0f,
) {
    val saturation: Float
        get() = (1f + vibrancy).coerceAtLeast(0f)

    companion object {
        const val DEFAULT_REFRACTION = 100
        const val DEFAULT_DEPTH = 100
        const val DEFAULT_DISPERSION = 100
        const val DEFAULT_VIBRANCY = 0
        const val DEFAULT_RIM = 100
        const val DEFAULT_SHADOW = 100
        const val DEFAULT_OUTLINE_WIDTH = 1
        const val DEFAULT_OUTLINE_OPACITY = 20
        const val DEFAULT_OUTLINE_COLOR = Color.WHITE

        val DEFAULT = GlassStyle()

        val DIALOG = GlassStyle(
            refraction = 0.85f,
            depth = 0.95f,
            dispersion = 0.4f,
            vibrancy = -0.30f,
            rim = 1.15f,
            shadow = 0.9f,
            outlineWidth = 1f,
            outlineOpacity = 0.22f,
            outlineColor = Color.WHITE,
            dimFactor = 1f,
        )

        val ICON = GlassStyle(
            refraction = 0.85f,
            depth = 0.85f,
            dispersion = 0.7f,
            vibrancy = 0f,
            rim = 0.85f,
            shadow = 0f,
            outlineWidth = 0.75f,
            outlineOpacity = 0.18f,
            outlineColor = Color.WHITE,
        )
    }
}
