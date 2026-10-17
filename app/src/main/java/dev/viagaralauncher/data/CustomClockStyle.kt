// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.data

import org.json.JSONObject

enum class ClockPresetFont {
    Sacramento,
    Bokor,
    Plaster,
    Monoton,
    LuckiestGuy,
}

enum class ClockLayout(
    val stacked: Boolean = false,
    val week: Boolean = false,
    val presetFont: ClockPresetFont? = null,
) {
    SingleLine,
    TwoLines(stacked = true),
    Week(week = true),
    Sacramento(presetFont = ClockPresetFont.Sacramento),
    Bokor(presetFont = ClockPresetFont.Bokor),
    Plaster(presetFont = ClockPresetFont.Plaster),
    DoublePlaster(stacked = true, presetFont = ClockPresetFont.Plaster),
    Monoton(presetFont = ClockPresetFont.Monoton),
    Lucky(presetFont = ClockPresetFont.LuckiestGuy),
    SacramentoWeek(week = true, presetFont = ClockPresetFont.Sacramento);

    val allowsCustomFont: Boolean get() = presetFont == null
}

data class ClockFaceStyle(
    val fontId: String? = null,
    val weight: Int = 300,
    val size: Int = 72,
    val letterSpacing: Int = -3,
    val showColon: Boolean = false,
    val separateDigits: Boolean = false,
    val hourMinuteSpacing: Int = 12,
    val fontShadow: Int = 0,
) {
    fun maxWeight(layout: ClockLayout): Int =
        if (layout.presetFont == null && fontId == null) 700 else 900

    fun normalized(layout: ClockLayout = ClockLayout.SingleLine): ClockFaceStyle = copy(
        fontId = fontId.takeIf { layout.allowsCustomFont },
        weight = weight.coerceIn(100, maxWeight(layout)),
        size = size.coerceIn(32, 144),
        letterSpacing = letterSpacing.coerceIn(-16, 16),
        hourMinuteSpacing = hourMinuteSpacing.coerceIn(-16, 64),
        fontShadow = fontShadow.coerceIn(0, 24),
    )
}

/**
 * Each layout keeps its own typography settings and presets.
 */
data class CustomClockStyle(
    val layout: ClockLayout = ClockLayout.SingleLine,
    val singleLine: ClockFaceStyle = defaults(ClockLayout.SingleLine),
    val twoLines: ClockFaceStyle = defaults(ClockLayout.TwoLines),
    val presets: Map<ClockLayout, ClockFaceStyle> = emptyMap(),
) {
    val face: ClockFaceStyle
        get() = when (layout) {
            ClockLayout.SingleLine -> singleLine
            ClockLayout.TwoLines -> twoLines
            else -> presets[layout] ?: defaults(layout)
        }.normalized(layout)

    fun withFace(face: ClockFaceStyle): CustomClockStyle {
        val safe = face.normalized(layout)
        return when (layout) {
            ClockLayout.SingleLine -> copy(singleLine = safe)
            ClockLayout.TwoLines -> copy(twoLines = safe)
            else -> copy(presets = presets + (layout to safe))
        }
    }

    fun withoutFonts(ids: Set<String>): CustomClockStyle {
        fun ClockFaceStyle.clean(): ClockFaceStyle = if (fontId in ids) copy(fontId = null) else this
        return copy(
            singleLine = singleLine.clean(),
            twoLines = twoLines.clean(),
            presets = presets.mapValues { it.value.clean() },
        )
    }

    fun encode(): String = JSONObject().apply {
        put("layout", layout.name)
        fun encodeFace(face: ClockFaceStyle, layout: ClockLayout): JSONObject = JSONObject().apply {
            val safe = face.normalized(layout)
            put("fontId", safe.fontId ?: JSONObject.NULL)
            put("weight", safe.weight)
            put("size", safe.size)
            put("letterSpacing", safe.letterSpacing)
            put("hourMinuteSpacing", safe.hourMinuteSpacing)
            put("fontShadow", safe.fontShadow)
            put("showColon", safe.showColon)
            put("separateDigits", safe.separateDigits)
        }
        put("singleLine", encodeFace(singleLine, ClockLayout.SingleLine))
        put("twoLines", encodeFace(twoLines, ClockLayout.TwoLines))
        put("presets", JSONObject().apply {
            presets.forEach { (layout, face) -> put(layout.name, encodeFace(face, layout)) }
        })
    }.toString()

    companion object {
        fun defaults(layout: ClockLayout): ClockFaceStyle = when (layout) {
            ClockLayout.SingleLine -> ClockFaceStyle(letterSpacing = 3)
            ClockLayout.TwoLines -> ClockFaceStyle(size = 88, letterSpacing = 3)
            ClockLayout.Week -> ClockFaceStyle(letterSpacing = 3)
            ClockLayout.Sacramento -> ClockFaceStyle(size = 96, letterSpacing = -6, separateDigits = true)
            ClockLayout.SacramentoWeek -> ClockFaceStyle(size = 96, letterSpacing = 0, separateDigits = true)
            ClockLayout.DoublePlaster -> ClockFaceStyle(size = 88, letterSpacing = 0)
            ClockLayout.Bokor, ClockLayout.Plaster, ClockLayout.Monoton -> ClockFaceStyle(letterSpacing = 0)
            ClockLayout.Lucky -> ClockFaceStyle(size = 80, letterSpacing = -6, separateDigits = true)
        }

        fun decode(value: String?): CustomClockStyle = runCatching {
            val json = JSONObject(value ?: "{}")
            fun face(layout: ClockLayout, data: JSONObject?): ClockFaceStyle {
                val defaults = defaults(layout)
                if (data == null) return defaults
                return ClockFaceStyle(
                    fontId = data.optString("fontId").takeIf { it.isNotBlank() && it != "null" },
                    weight = data.optInt("weight", defaults.weight),
                    size = data.optInt("size", defaults.size),
                    letterSpacing = data.optInt("letterSpacing", defaults.letterSpacing),
                    hourMinuteSpacing = data.optInt("hourMinuteSpacing", defaults.hourMinuteSpacing),
                    fontShadow = data.optInt("fontShadow", defaults.fontShadow),
                    showColon = data.optBoolean("showColon", defaults.showColon),
                    separateDigits = data.optBoolean("separateDigits", defaults.separateDigits),
                ).normalized(layout)
            }
            val presets = json.optJSONObject("presets")
            CustomClockStyle(
                layout = ClockLayout.entries.firstOrNull { it.name == json.optString("layout") } ?: ClockLayout.SingleLine,
                singleLine = face(ClockLayout.SingleLine, json.optJSONObject("singleLine")),
                twoLines = face(ClockLayout.TwoLines, json.optJSONObject("twoLines")),
                presets = ClockLayout.entries.filter { presets?.has(it.name) == true }
                    .associateWith { face(it, presets?.optJSONObject(it.name)) },
            )
        }.getOrDefault(CustomClockStyle())
    }
}
