// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.applist

/**
 * One source of truth for where the A-Z strip sits, so the letter under the finger
 * matches the one that highlights. The strip is vertically centered along the screen edge
 * with comfortable spacing rather than collapsing to the top.
 */
object ScrubberGeometry {

    /** Top margin in dp to leave space for status bar and widgets. */
    const val TOP_MARGIN_DP = 72f

    /** Bottom margin in dp: 1 centimeter above the screen bottom (~64dp). */
    const val BOTTOM_MARGIN_DP = 64f

    /** Slot height for each letter: ~15dp glyph space + ~1mm (6dp) spacing between letters. */
    const val LETTER_SLOT_DP = 21f

    fun indexForY(y: Float, topPx: Float, heightPx: Float, count: Int): Int {
        if (count <= 0 || heightPx <= 0f) return 0
        val idx = ((y - topPx) / (heightPx / count)).toInt()
        return idx.coerceIn(0, count - 1)
    }

    /** Screen-space centre of the letter at [index]. */
    fun letterCenterY(index: Int, topPx: Float, heightPx: Float, count: Int): Float {
        if (count <= 0) return topPx
        val spacing = heightPx / count
        return topPx + index * spacing + spacing / 2f
    }

    /**
     * Computes the vertical band for the alphabet strip:
     * - Ends exactly 1 cm (~64dp) above the bottom edge of the phone.
     * - Keeps letters compact with ~1mm gap between them without touching.
     */
    fun computeBand(viewportHeightPx: Float, density: Float, letterCount: Int): ScrubBand {
        if (viewportHeightPx <= 0f || letterCount <= 0) {
            return ScrubBand(topPx = 0f, heightPx = 0f)
        }
        val bottomMarginPx = BOTTOM_MARGIN_DP * density
        val bottomPx = (viewportHeightPx - bottomMarginPx).coerceAtLeast(0f)
        val desiredHeightPx = letterCount * LETTER_SLOT_DP * density
        val topPx = (bottomPx - desiredHeightPx).coerceAtLeast(16f * density)
        val heightPx = (bottomPx - topPx).coerceAtLeast(0f)

        return ScrubBand(topPx = topPx, heightPx = heightPx)
    }
}

/** The vertical band the strip is laid out in, in screen pixels. */
data class ScrubBand(val topPx: Float, val heightPx: Float) {
    val bottomPx: Float get() = topPx + heightPx

    companion object {
        fun fallbackFor(viewportHeightPx: Int) = ScrubBand(
            topPx = viewportHeightPx * 0.10f,
            heightPx = viewportHeightPx * 0.80f,
        )
    }
}