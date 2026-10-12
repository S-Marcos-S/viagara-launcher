// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.clock

data class RemoteClockItem(
    val id: String,
    val name: String,
    val author: String,
    val description: String = "",
    val styleJsonUrl: String = "",
    val previewType: String = "dynamic", // "dynamic", "image"
    val previewImageUrl: String? = null,
    val layoutType: String = "column", // "column", "row", "stacked", "analog"
    val tags: List<String> = emptyList(),
)

data class RemoteClockStyleDefinition(
    val id: String,
    val name: String,
    val author: String = "Viagara Team",
    val layout: String = "column", // "column", "row", "stacked", "centered", "analog"
    val alignment: String = "start", // "start", "center", "end"
    val timeElement: ClockTextElement = ClockTextElement(),
    val dateElement: ClockTextElement? = null,
    val secondaryElement: ClockTextElement? = null,
    val analogConfig: AnalogClockConfig? = null,
    val containerPaddingDp: Int = 0,
)

data class ClockTextElement(
    val format: String = "HH:mm",
    val fontSizeSp: Int = 48,
    val fontWeight: String = "bold", // "normal", "medium", "bold", "extra_bold", "thin"
    val fontUrl: String? = null,
    val fontFileName: String? = null,
    val colorHex: String? = null, // null means adapt to wallpaper/theme
    val secondaryColorHex: String? = null,
    val animation: String = "none", // "none", "shimmer", "pulse", "flip", "slide"
    val animationDurationMs: Int = 2000,
    val letterSpacingSp: Float = 0f,
    val glowRadiusDp: Float = 0f,
    val glowColorHex: String? = null,
)

data class AnalogClockConfig(
    val dialShape: String = "circle", // "circle", "rounded_rect", "minimal"
    val showTicks: Boolean = true,
    val smoothSecondHand: Boolean = true,
    val dialColorHex: String? = null,
    val hourHandColorHex: String? = null,
    val minuteHandColorHex: String? = null,
    val secondHandColorHex: String? = null,
)

data class ClockCatalog(
    val version: Int,
    val clocks: List<RemoteClockItem>,
)
