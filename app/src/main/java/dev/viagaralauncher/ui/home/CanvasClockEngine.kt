// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.viagaralauncher.data.ClockStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometric font definitions for Canvas-rendered clock digits.
 * Coordinates are defined on a normalized 100 x 160 unit bounding box.
 */
object CanvasGlyphRegistry {

    // -------------------------------------------------------------
    // 1. MODERN GEOMETRIC SANS (Classic, Stacked, Digital Card, Centered Pill, Daily Reflection)
    // Clean, perfectly proportioned Swiss geometric strokes
    // -------------------------------------------------------------
    fun getGeometricStrokes(char: Char): List<Path> {
        val list = mutableListOf<Path>()
        when (char) {
            '0' -> {
                val p = Path()
                p.moveTo(50f, 15f)
                p.cubicTo(22f, 15f, 20f, 48f, 20f, 80f)
                p.cubicTo(20f, 112f, 22f, 145f, 50f, 145f)
                p.cubicTo(78f, 145f, 80f, 112f, 80f, 80f)
                p.cubicTo(80f, 48f, 78f, 15f, 50f, 15f)
                p.close()
                list.add(p)
            }
            '1' -> {
                val p = Path()
                p.moveTo(32f, 40f)
                p.lineTo(52f, 15f)
                p.lineTo(52f, 145f)
                list.add(p)
            }
            '2' -> {
                val p = Path()
                p.moveTo(22f, 48f)
                p.cubicTo(22f, 15f, 78f, 15f, 78f, 48f)
                p.cubicTo(78f, 75f, 48f, 105f, 20f, 145f)
                p.lineTo(80f, 145f)
                list.add(p)
            }
            '3' -> {
                val p = Path()
                p.moveTo(22f, 38f)
                p.cubicTo(22f, 15f, 78f, 15f, 78f, 50f)
                p.cubicTo(78f, 72f, 55f, 78f, 44f, 80f)
                p.cubicTo(58f, 82f, 80f, 90f, 80f, 115f)
                p.cubicTo(80f, 145f, 22f, 145f, 22f, 122f)
                list.add(p)
            }
            '4' -> {
                val p1 = Path()
                p1.moveTo(68f, 145f)
                p1.lineTo(68f, 15f)
                p1.lineTo(18f, 105f)
                p1.lineTo(84f, 105f)
                list.add(p1)
            }
            '5' -> {
                val p = Path()
                p.moveTo(76f, 18f)
                p.lineTo(24f, 18f)
                p.lineTo(22f, 72f)
                p.cubicTo(32f, 66f, 78f, 64f, 78f, 106f)
                p.cubicTo(78f, 145f, 22f, 145f, 22f, 122f)
                list.add(p)
            }
            '6' -> {
                val p = Path()
                p.moveTo(74f, 28f)
                p.cubicTo(55f, 15f, 20f, 42f, 20f, 88f)
                p.cubicTo(20f, 122f, 32f, 145f, 52f, 145f)
                p.cubicTo(75f, 145f, 80f, 122f, 80f, 98f)
                p.cubicTo(80f, 72f, 62f, 62f, 50f, 62f)
                p.cubicTo(32f, 62f, 22f, 78f, 20f, 96f)
                list.add(p)
            }
            '7' -> {
                val p = Path()
                p.moveTo(20f, 18f)
                p.lineTo(80f, 18f)
                p.lineTo(40f, 145f)
                list.add(p)
            }
            '8' -> {
                val p = Path()
                p.moveTo(50f, 76f)
                p.cubicTo(32f, 76f, 25f, 58f, 25f, 44f)
                p.cubicTo(25f, 18f, 75f, 18f, 75f, 44f)
                p.cubicTo(75f, 58f, 68f, 76f, 50f, 76f)
                p.cubicTo(30f, 76f, 20f, 96f, 20f, 114f)
                p.cubicTo(20f, 145f, 80f, 145f, 80f, 114f)
                p.cubicTo(80f, 96f, 70f, 76f, 50f, 76f)
                list.add(p)
            }
            '9' -> {
                val p = Path()
                p.moveTo(26f, 132f)
                p.cubicTo(45f, 145f, 80f, 118f, 80f, 72f)
                p.cubicTo(80f, 38f, 68f, 15f, 48f, 15f)
                p.cubicTo(25f, 15f, 20f, 38f, 20f, 62f)
                p.cubicTo(20f, 88f, 38f, 98f, 50f, 98f)
                p.cubicTo(68f, 98f, 78f, 82f, 80f, 64f)
                list.add(p)
            }
            ':' -> {
                val p1 = Path().apply {
                    addOval(Rect(43f, 46f, 57f, 60f))
                }
                val p2 = Path().apply {
                    addOval(Rect(43f, 100f, 57f, 114f))
                }
                list.add(p1)
                list.add(p2)
            }
        }
        return list
    }

    // -------------------------------------------------------------
    // 2. ULTRA-MINIMAL HAIRLINE (Minimal, Day Focus, Minimal Specs)
    // Elegant, ultra-pure architectural lines with open terminals
    // -------------------------------------------------------------
    fun getMinimalStrokes(char: Char): List<Path> {
        val list = mutableListOf<Path>()
        when (char) {
            '0' -> {
                val p = Path()
                p.moveTo(50f, 14f)
                p.cubicTo(25f, 14f, 24f, 50f, 24f, 80f)
                p.cubicTo(24f, 110f, 25f, 146f, 50f, 146f)
                p.cubicTo(75f, 146f, 76f, 110f, 76f, 80f)
                p.cubicTo(76f, 50f, 75f, 14f, 50f, 14f)
                list.add(p)
            }
            '1' -> {
                val p = Path()
                p.moveTo(50f, 14f)
                p.lineTo(50f, 146f)
                list.add(p)
            }
            '2' -> {
                val p = Path()
                p.moveTo(25f, 45f)
                p.cubicTo(25f, 14f, 75f, 14f, 75f, 45f)
                p.lineTo(25f, 146f)
                p.lineTo(76f, 146f)
                list.add(p)
            }
            '3' -> {
                val p = Path()
                p.moveTo(25f, 32f)
                p.cubicTo(35f, 14f, 75f, 14f, 75f, 48f)
                p.cubicTo(75f, 74f, 45f, 80f, 40f, 80f)
                p.cubicTo(55f, 80f, 76f, 86f, 76f, 115f)
                p.cubicTo(76f, 146f, 32f, 146f, 24f, 128f)
                list.add(p)
            }
            '4' -> {
                val p1 = Path()
                p1.moveTo(68f, 14f)
                p1.lineTo(68f, 146f)
                list.add(p1)
                val p2 = Path()
                p2.moveTo(68f, 30f)
                p2.lineTo(20f, 102f)
                p2.lineTo(82f, 102f)
                list.add(p2)
            }
            '5' -> {
                val p = Path()
                p.moveTo(74f, 16f)
                p.lineTo(26f, 16f)
                p.lineTo(24f, 72f)
                p.cubicTo(38f, 68f, 76f, 70f, 76f, 108f)
                p.cubicTo(76f, 146f, 28f, 146f, 22f, 128f)
                list.add(p)
            }
            '6' -> {
                val p = Path()
                p.moveTo(70f, 22f)
                p.lineTo(34f, 75f)
                p.cubicTo(24f, 90f, 24f, 146f, 52f, 146f)
                p.cubicTo(76f, 146f, 76f, 105f, 76f, 96f)
                p.cubicTo(76f, 72f, 45f, 68f, 30f, 80f)
                list.add(p)
            }
            '7' -> {
                val p = Path()
                p.moveTo(22f, 16f)
                p.lineTo(76f, 16f)
                p.lineTo(38f, 146f)
                list.add(p)
            }
            '8' -> {
                val p = Path()
                p.moveTo(50f, 80f)
                p.cubicTo(30f, 80f, 26f, 60f, 26f, 46f)
                p.cubicTo(26f, 16f, 74f, 16f, 74f, 46f)
                p.cubicTo(74f, 60f, 70f, 80f, 50f, 80f)
                p.cubicTo(28f, 80f, 24f, 100f, 24f, 116f)
                p.cubicTo(24f, 146f, 76f, 146f, 76f, 116f)
                p.cubicTo(76f, 100f, 72f, 80f, 50f, 80f)
                list.add(p)
            }
            '9' -> {
                val p = Path()
                p.moveTo(30f, 138f)
                p.lineTo(66f, 85f)
                p.cubicTo(76f, 70f, 76f, 14f, 48f, 14f)
                p.cubicTo(24f, 14f, 24f, 55f, 24f, 64f)
                p.cubicTo(24f, 88f, 55f, 92f, 70f, 80f)
                list.add(p)
            }
            ':' -> {
                val p1 = Path().apply {
                    addOval(Rect(45f, 50f, 55f, 60f))
                }
                val p2 = Path().apply {
                    addOval(Rect(45f, 100f, 55f, 110f))
                }
                list.add(p1)
                list.add(p2)
            }
        }
        return list
    }

    // -------------------------------------------------------------
    // 3. CYBER HUD 45-DEGREE CHANGER (Tech HUD, Tech HUD Pro, System Monitor)
    // Angled sci-fi telemetry cuts with chamfered corners
    // -------------------------------------------------------------
    fun getCyberHudStrokes(char: Char): List<Path> {
        val list = mutableListOf<Path>()
        when (char) {
            '0' -> {
                val p = Path()
                p.moveTo(38f, 16f)
                p.lineTo(72f, 16f)
                p.lineTo(82f, 32f)
                p.lineTo(82f, 128f)
                p.lineTo(68f, 144f)
                p.lineTo(28f, 144f)
                p.lineTo(18f, 128f)
                p.lineTo(18f, 32f)
                p.close()
                list.add(p)
            }
            '1' -> {
                val p = Path()
                p.moveTo(28f, 36f)
                p.lineTo(54f, 16f)
                p.lineTo(54f, 144f)
                list.add(p)
            }
            '2' -> {
                val p = Path()
                p.moveTo(20f, 38f)
                p.lineTo(38f, 16f)
                p.lineTo(72f, 16f)
                p.lineTo(82f, 36f)
                p.lineTo(82f, 66f)
                p.lineTo(22f, 124f)
                p.lineTo(22f, 144f)
                p.lineTo(82f, 144f)
                list.add(p)
            }
            '3' -> {
                val p = Path()
                p.moveTo(20f, 26f)
                p.lineTo(36f, 16f)
                p.lineTo(72f, 16f)
                p.lineTo(82f, 32f)
                p.lineTo(82f, 64f)
                p.lineTo(52f, 78f)
                p.lineTo(82f, 92f)
                p.lineTo(82f, 128f)
                p.lineTo(70f, 144f)
                p.lineTo(34f, 144f)
                p.lineTo(20f, 134f)
                list.add(p)
            }
            '4' -> {
                val p1 = Path()
                p1.moveTo(68f, 16f)
                p1.lineTo(68f, 144f)
                list.add(p1)
                val p2 = Path()
                p2.moveTo(68f, 16f)
                p2.lineTo(18f, 96f)
                p2.lineTo(84f, 96f)
                list.add(p2)
            }
            '5' -> {
                val p = Path()
                p.moveTo(80f, 16f)
                p.lineTo(22f, 16f)
                p.lineTo(22f, 72f)
                p.lineTo(70f, 72f)
                p.lineTo(82f, 88f)
                p.lineTo(82f, 128f)
                p.lineTo(70f, 144f)
                p.lineTo(30f, 144f)
                p.lineTo(18f, 132f)
                list.add(p)
            }
            '6' -> {
                val p = Path()
                p.moveTo(76f, 28f)
                p.lineTo(34f, 16f)
                p.lineTo(18f, 40f)
                p.lineTo(18f, 128f)
                p.lineTo(32f, 144f)
                p.lineTo(70f, 144f)
                p.lineTo(82f, 128f)
                p.lineTo(82f, 88f)
                p.lineTo(70f, 72f)
                p.lineTo(20f, 72f)
                list.add(p)
            }
            '7' -> {
                val p = Path()
                p.moveTo(18f, 16f)
                p.lineTo(82f, 16f)
                p.lineTo(82f, 40f)
                p.lineTo(44f, 144f)
                list.add(p)
            }
            '8' -> {
                val p = Path()
                p.moveTo(34f, 16f)
                p.lineTo(66f, 16f)
                p.lineTo(78f, 32f)
                p.lineTo(78f, 62f)
                p.lineTo(66f, 76f)
                p.lineTo(82f, 92f)
                p.lineTo(82f, 128f)
                p.lineTo(68f, 144f)
                p.lineTo(32f, 144f)
                p.lineTo(18f, 128f)
                p.lineTo(18f, 92f)
                p.lineTo(34f, 76f)
                p.lineTo(22f, 62f)
                p.lineTo(22f, 32f)
                p.close()
                list.add(p)
            }
            '9' -> {
                val p = Path()
                p.moveTo(24f, 132f)
                p.lineTo(66f, 144f)
                p.lineTo(82f, 120f)
                p.lineTo(82f, 32f)
                p.lineTo(68f, 16f)
                p.lineTo(30f, 16f)
                p.lineTo(18f, 32f)
                p.lineTo(18f, 72f)
                p.lineTo(30f, 88f)
                p.lineTo(80f, 88f)
                list.add(p)
            }
            ':' -> {
                val p1 = Path().apply {
                    addRect(Rect(43f, 46f, 57f, 60f))
                }
                val p2 = Path().apply {
                    addRect(Rect(43f, 100f, 57f, 114f))
                }
                list.add(p1)
                list.add(p2)
            }
        }
        return list
    }

    // -------------------------------------------------------------
    // 4. RETRO MONOCHROME MATRIX (Retro Terminal)
    // Authentic 7-segment digital LED/VFD displays
    // Segments: A (top), B (top-right), C (bot-right), D (bottom), E (bot-left), F (top-left), G (center)
    // -------------------------------------------------------------
    fun get7SegmentSegments(char: Char): List<Int> {
        // Bitmask for segments 0..6 (A, B, C, D, E, F, G)
        return when (char) {
            '0' -> listOf(0, 1, 2, 3, 4, 5)
            '1' -> listOf(1, 2)
            '2' -> listOf(0, 1, 6, 4, 3)
            '3' -> listOf(0, 1, 6, 2, 3)
            '4' -> listOf(5, 6, 1, 2)
            '5' -> listOf(0, 5, 6, 2, 3)
            '6' -> listOf(0, 5, 4, 3, 2, 6)
            '7' -> listOf(0, 1, 2)
            '8' -> listOf(0, 1, 2, 3, 4, 5, 6)
            '9' -> listOf(0, 1, 2, 3, 5, 6)
            ':' -> listOf(7, 8) // colon dots
            else -> emptyList()
        }
    }

    // -------------------------------------------------------------
    // 5. DOT MATRIX 5x7 GRID (Nothing Dots)
    // Physical Nothing-OS styled matrix dots grid
    // -------------------------------------------------------------
    private val MATRIX_5x7: Map<Char, List<Int>> = mapOf(
        '0' to listOf(
            0,1,1,1,0,
            1,0,0,0,1,
            1,0,0,1,1,
            1,0,1,0,1,
            1,1,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        ),
        '1' to listOf(
            0,0,1,0,0,
            0,1,1,0,0,
            0,0,1,0,0,
            0,0,1,0,0,
            0,0,1,0,0,
            0,0,1,0,0,
            0,1,1,1,0
        ),
        '2' to listOf(
            0,1,1,1,0,
            1,0,0,0,1,
            0,0,0,0,1,
            0,0,0,1,0,
            0,0,1,0,0,
            0,1,0,0,0,
            1,1,1,1,1
        ),
        '3' to listOf(
            1,1,1,1,0,
            0,0,0,0,1,
            0,0,0,0,1,
            0,1,1,1,0,
            0,0,0,0,1,
            0,0,0,0,1,
            1,1,1,1,0
        ),
        '4' to listOf(
            0,0,0,1,0,
            0,0,1,1,0,
            0,1,0,1,0,
            1,0,0,1,0,
            1,1,1,1,1,
            0,0,0,1,0,
            0,0,0,1,0
        ),
        '5' to listOf(
            1,1,1,1,1,
            1,0,0,0,0,
            1,1,1,1,0,
            0,0,0,0,1,
            0,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        ),
        '6' to listOf(
            0,1,1,1,0,
            1,0,0,0,0,
            1,0,0,0,0,
            1,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        ),
        '7' to listOf(
            1,1,1,1,1,
            0,0,0,0,1,
            0,0,0,1,0,
            0,0,1,0,0,
            0,0,1,0,0,
            0,1,0,0,0,
            0,1,0,0,0
        ),
        '8' to listOf(
            0,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        ),
        '9' to listOf(
            0,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,1,
            0,0,0,0,1,
            0,0,0,0,1,
            0,1,1,1,0
        ),
        ':' to listOf(
            0,0,0,0,0,
            0,0,1,0,0,
            0,0,0,0,0,
            0,0,0,0,0,
            0,0,1,0,0,
            0,0,0,0,0,
            0,0,0,0,0
        )
    )

    fun getMatrixGrid(char: Char): List<Int> = MATRIX_5x7[char] ?: List(35) { 0 }

    // -------------------------------------------------------------
    // 6. ELEGANT SCRIPT CALLIGRAPHY (Calligraphy Large, Stacked, Minimal)
    // Flowing copperplate Spencerian script curves
    // -------------------------------------------------------------
    fun getScriptStrokes(char: Char): List<Path> {
        val list = mutableListOf<Path>()
        when (char) {
            '0' -> {
                val p = Path()
                p.moveTo(60f, 20f)
                p.cubicTo(30f, 15f, 15f, 60f, 15f, 95f)
                p.cubicTo(15f, 130f, 38f, 148f, 56f, 148f)
                p.cubicTo(78f, 148f, 85f, 115f, 85f, 75f)
                p.cubicTo(85f, 32f, 65f, 20f, 48f, 20f)
                list.add(p)
            }
            '1' -> {
                val p = Path()
                p.moveTo(25f, 42f)
                p.cubicTo(35f, 30f, 46f, 22f, 58f, 16f)
                p.cubicTo(56f, 60f, 54f, 110f, 52f, 146f)
                list.add(p)
            }
            '2' -> {
                val p = Path()
                p.moveTo(22f, 50f)
                p.cubicTo(22f, 18f, 78f, 16f, 78f, 48f)
                p.cubicTo(78f, 78f, 40f, 114f, 20f, 146f)
                p.cubicTo(42f, 142f, 68f, 146f, 84f, 142f)
                list.add(p)
            }
            '3' -> {
                val p = Path()
                p.moveTo(24f, 40f)
                p.cubicTo(32f, 18f, 76f, 16f, 74f, 55f)
                p.cubicTo(72f, 72f, 52f, 78f, 42f, 80f)
                p.cubicTo(66f, 82f, 82f, 98f, 80f, 122f)
                p.cubicTo(78f, 148f, 34f, 148f, 22f, 134f)
                list.add(p)
            }
            '4' -> {
                val p1 = Path()
                p1.moveTo(66f, 16f)
                p1.lineTo(16f, 102f)
                p1.lineTo(84f, 102f)
                list.add(p1)
                val p2 = Path()
                p2.moveTo(66f, 65f)
                p2.cubicTo(66f, 95f, 64f, 125f, 62f, 146f)
                list.add(p2)
            }
            '5' -> {
                val p = Path()
                p.moveTo(76f, 20f)
                p.lineTo(28f, 20f)
                p.lineTo(24f, 70f)
                p.cubicTo(36f, 62f, 78f, 66f, 76f, 110f)
                p.cubicTo(74f, 148f, 32f, 148f, 18f, 134f)
                list.add(p)
            }
            '6' -> {
                val p = Path()
                p.moveTo(70f, 24f)
                p.cubicTo(42f, 45f, 18f, 95f, 20f, 124f)
                p.cubicTo(22f, 148f, 78f, 148f, 78f, 114f)
                p.cubicTo(78f, 84f, 28f, 84f, 22f, 114f)
                list.add(p)
            }
            '7' -> {
                val p = Path()
                p.moveTo(20f, 26f)
                p.cubicTo(45f, 20f, 70f, 20f, 80f, 20f)
                p.cubicTo(68f, 65f, 52f, 110f, 42f, 148f)
                list.add(p)
            }
            '8' -> {
                val p = Path()
                p.moveTo(50f, 76f)
                p.cubicTo(32f, 64f, 28f, 26f, 50f, 20f)
                p.cubicTo(72f, 20f, 68f, 64f, 50f, 76f)
                p.cubicTo(28f, 92f, 24f, 146f, 50f, 146f)
                p.cubicTo(76f, 146f, 72f, 94f, 50f, 76f)
                list.add(p)
            }
            '9' -> {
                val p = Path()
                p.moveTo(76f, 54f)
                p.cubicTo(76f, 20f, 24f, 20f, 24f, 54f)
                p.cubicTo(24f, 88f, 76f, 88f, 76f, 54f)
                p.cubicTo(76f, 95f, 68f, 130f, 44f, 148f)
                list.add(p)
            }
            ':' -> {
                val p1 = Path().apply { addOval(Rect(43f, 48f, 57f, 62f)) }
                val p2 = Path().apply { addOval(Rect(43f, 98f, 57f, 112f)) }
                list.add(p1)
                list.add(p2)
            }
        }
        return list
    }
}

/**
 * Animated Canvas Digit tailored individually to each Clock Style family.
 * Supports individual reveal animation, stroke glow, and distinct stylistic aesthetics.
 */
@Composable
fun ThemedAnimatedCanvasDigit(
    char: Char,
    style: ClockStyle,
    staggerDelayMs: Int,
    contentColor: Color,
    modifier: Modifier = Modifier,
    strokeWidthDp: Dp = 3.6.dp,
) {
    var lastChar by remember { mutableStateOf(char) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(char) {
        if (lastChar != char) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 140, easing = LinearEasing),
            )
            lastChar = char
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            )
        } else {
            if (staggerDelayMs > 0) {
                kotlinx.coroutines.delay(staggerDelayMs.toLong())
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            )
        }
    }

    when (style) {
        ClockStyle.NOTHING_DOTS -> {
            NothingMatrixDigitCanvas(
                char = lastChar,
                progress = progress.value,
                contentColor = contentColor,
                modifier = modifier,
            )
        }
        ClockStyle.RETRO_TERMINAL -> {
            Retro7SegmentDigitCanvas(
                char = lastChar,
                progress = progress.value,
                contentColor = contentColor,
                modifier = modifier,
            )
        }
        ClockStyle.CALLIGRAPHY_LARGE,
        ClockStyle.CALLIGRAPHY_STACKED,
        ClockStyle.CALLIGRAPHY_MINIMAL -> {
            ScriptVectorDigitCanvas(
                char = lastChar,
                progress = progress.value,
                contentColor = contentColor,
                strokeWidthDp = strokeWidthDp,
                modifier = modifier,
            )
        }
        ClockStyle.TECH_HUD,
        ClockStyle.TECH_HUD_PRO,
        ClockStyle.SYSTEM_MONITOR -> {
            CyberHudVectorDigitCanvas(
                char = lastChar,
                progress = progress.value,
                contentColor = contentColor,
                strokeWidthDp = strokeWidthDp,
                modifier = modifier,
            )
        }
        ClockStyle.MINIMAL,
        ClockStyle.DAY_FOCUS,
        ClockStyle.MINIMAL_SPECS -> {
            MinimalVectorDigitCanvas(
                char = lastChar,
                progress = progress.value,
                contentColor = contentColor,
                strokeWidthDp = strokeWidthDp,
                modifier = modifier,
            )
        }
        else -> {
            // CLASSIC, STACKED, DIGITAL_CARD, CENTERED_PILL, DUAL_TONE_STACK, DAILY_REFLECTION, etc.
            GeometricVectorDigitCanvas(
                char = lastChar,
                progress = progress.value,
                contentColor = contentColor,
                strokeWidthDp = strokeWidthDp,
                modifier = modifier,
            )
        }
    }
}

// -------------------------------------------------------------------------
// COMPONENT RENDERERS
// -------------------------------------------------------------------------

@Composable
private fun GeometricVectorDigitCanvas(
    char: Char,
    progress: Float,
    contentColor: Color,
    strokeWidthDp: Dp,
    modifier: Modifier,
) {
    val strokeData = remember(char) {
        DigitStrokeData(CanvasGlyphRegistry.getGeometricStrokes(char))
    }

    Canvas(modifier = modifier) {
        if (progress <= 0f) return@Canvas
        val scale = min(size.width / 100f, size.height / 160f)
        val offsetX = (size.width - 100f * scale) / 2f
        val offsetY = (size.height - 160f * scale) / 2f
        val strokePx = strokeWidthDp.toPx()

        val dstPath = Path()
        strokeData.extractSegment(progress, dstPath)

        translate(left = offsetX, top = offsetY) {
            scale(scale = scale, pivot = Offset.Zero) {
                // Subtle ambient glow
                drawPath(
                    path = dstPath,
                    color = contentColor.copy(alpha = 0.18f),
                    style = Stroke(width = (strokePx / scale) * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // Crisp geometric stroke
                drawPath(
                    path = dstPath,
                    color = contentColor,
                    style = Stroke(width = strokePx / scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
private fun MinimalVectorDigitCanvas(
    char: Char,
    progress: Float,
    contentColor: Color,
    strokeWidthDp: Dp,
    modifier: Modifier,
) {
    val strokeData = remember(char) {
        DigitStrokeData(CanvasGlyphRegistry.getMinimalStrokes(char))
    }

    Canvas(modifier = modifier) {
        if (progress <= 0f) return@Canvas
        val scale = min(size.width / 100f, size.height / 160f)
        val offsetX = (size.width - 100f * scale) / 2f
        val offsetY = (size.height - 160f * scale) / 2f
        val strokePx = (strokeWidthDp * 0.75f).toPx()

        val dstPath = Path()
        strokeData.extractSegment(progress, dstPath)

        translate(left = offsetX, top = offsetY) {
            scale(scale = scale, pivot = Offset.Zero) {
                drawPath(
                    path = dstPath,
                    color = contentColor,
                    style = Stroke(width = strokePx / scale, cap = StrokeCap.Square, join = StrokeJoin.Miter),
                )
            }
        }
    }
}

@Composable
private fun CyberHudVectorDigitCanvas(
    char: Char,
    progress: Float,
    contentColor: Color,
    strokeWidthDp: Dp,
    modifier: Modifier,
) {
    val strokeData = remember(char) {
        DigitStrokeData(CanvasGlyphRegistry.getCyberHudStrokes(char))
    }

    Canvas(modifier = modifier) {
        if (progress <= 0f) return@Canvas
        val scale = min(size.width / 100f, size.height / 160f)
        val offsetX = (size.width - 100f * scale) / 2f
        val offsetY = (size.height - 160f * scale) / 2f
        val strokePx = strokeWidthDp.toPx()

        val dstPath = Path()
        val penTip = strokeData.extractSegment(progress, dstPath)

        translate(left = offsetX, top = offsetY) {
            scale(scale = scale, pivot = Offset.Zero) {
                // Neon trace bloom
                drawPath(
                    path = dstPath,
                    color = contentColor.copy(alpha = 0.25f),
                    style = Stroke(width = (strokePx / scale) * 2.2f, cap = StrokeCap.Square, join = StrokeJoin.Miter),
                )
                // Solid angular stroke
                drawPath(
                    path = dstPath,
                    color = contentColor,
                    style = Stroke(width = strokePx / scale, cap = StrokeCap.Square, join = StrokeJoin.Miter),
                )
                // Cyber spark at leading edge
                if (penTip != null && progress in 0.02f..0.98f) {
                    drawRect(
                        color = contentColor,
                        topLeft = Offset(penTip.x - 4f, penTip.y - 4f),
                        size = Size(8f, 8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptVectorDigitCanvas(
    char: Char,
    progress: Float,
    contentColor: Color,
    strokeWidthDp: Dp,
    modifier: Modifier,
) {
    val strokeData = remember(char) {
        DigitStrokeData(CanvasGlyphRegistry.getScriptStrokes(char))
    }

    Canvas(modifier = modifier) {
        if (progress <= 0f) return@Canvas
        val scale = min(size.width / 100f, size.height / 160f)
        val offsetX = (size.width - 100f * scale) / 2f
        val offsetY = (size.height - 160f * scale) / 2f
        val strokePx = (strokeWidthDp * 1.1f).toPx()

        val dstPath = Path()
        val penTip = strokeData.extractSegment(progress, dstPath)

        translate(left = offsetX, top = offsetY) {
            scale(scale = scale, pivot = Offset.Zero) {
                drawPath(
                    path = dstPath,
                    color = contentColor.copy(alpha = 0.15f),
                    style = Stroke(width = (strokePx / scale) * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path = dstPath,
                    color = contentColor,
                    style = Stroke(width = strokePx / scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                if (penTip != null && progress in 0.02f..0.98f) {
                    drawCircle(
                        color = contentColor,
                        radius = (strokePx / scale) * 0.9f,
                        center = penTip,
                    )
                }
            }
        }
    }
}

@Composable
private fun NothingMatrixDigitCanvas(
    char: Char,
    progress: Float,
    contentColor: Color,
    modifier: Modifier,
) {
    val grid = remember(char) { CanvasGlyphRegistry.getMatrixGrid(char) }

    Canvas(modifier = modifier) {
        if (progress <= 0f) return@Canvas
        val cols = 5
        val rows = 7
        val dotRadius = min(size.width / (cols * 2.4f), size.height / (rows * 2.4f))
        val stepX = size.width / cols
        val stepY = size.height / rows

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                val isActive = grid.getOrElse(idx) { 0 } == 1
                val cx = c * stepX + stepX * 0.5f
                val cy = r * stepY + stepY * 0.5f

                // Matrix scanline appearance animation
                val dotDelayThreshold = (r.toFloat() / rows.toFloat()) * 0.7f
                val dotProgress = ((progress - dotDelayThreshold) / 0.3f).coerceIn(0f, 1f)

                if (isActive) {
                    if (dotProgress > 0f) {
                        drawCircle(
                            color = contentColor,
                            radius = dotRadius * dotProgress,
                            center = Offset(cx, cy),
                        )
                    }
                } else {
                    // Dim unlit background matrix pixel
                    drawCircle(
                        color = contentColor.copy(alpha = 0.08f),
                        radius = dotRadius * 0.45f,
                        center = Offset(cx, cy),
                    )
                }
            }
        }
    }
}

@Composable
private fun Retro7SegmentDigitCanvas(
    char: Char,
    progress: Float,
    contentColor: Color,
    modifier: Modifier,
) {
    val activeSegments = remember(char) { CanvasGlyphRegistry.get7SegmentSegments(char) }

    Canvas(modifier = modifier) {
        if (progress <= 0f) return@Canvas
        val w = size.width
        val h = size.height
        val t = min(w, h) * 0.12f // segment thickness
        val pad = t * 0.6f

        fun drawSegment(index: Int, rect: Rect) {
            val isActive = activeSegments.contains(index)
            val alpha = if (isActive) progress else 0.08f
            drawRoundRect(
                color = contentColor.copy(alpha = alpha),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(t * 0.35f, t * 0.35f),
            )
        }

        if (char == ':') {
            val dotSize = t * 1.1f
            val dotX = w * 0.5f - dotSize * 0.5f
            drawRoundRect(
                color = contentColor.copy(alpha = progress),
                topLeft = Offset(dotX, h * 0.35f),
                size = Size(dotSize, dotSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(t * 0.3f, t * 0.3f)
            )
            drawRoundRect(
                color = contentColor.copy(alpha = progress),
                topLeft = Offset(dotX, h * 0.65f),
                size = Size(dotSize, dotSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(t * 0.3f, t * 0.3f)
            )
            return@Canvas
        }

        val midY = h * 0.5f

        // A (Top)
        drawSegment(0, Rect(pad + t, pad, w - pad - t, pad + t))
        // B (Top-Right)
        drawSegment(1, Rect(w - pad - t, pad + t * 0.5f, w - pad, midY - t * 0.25f))
        // C (Bottom-Right)
        drawSegment(2, Rect(w - pad - t, midY + t * 0.25f, w - pad, h - pad - t * 0.5f))
        // D (Bottom)
        drawSegment(3, Rect(pad + t, h - pad - t, w - pad - t, h - pad))
        // E (Bottom-Left)
        drawSegment(4, Rect(pad, midY + t * 0.25f, pad + t, h - pad - t * 0.5f))
        // F (Top-Left)
        drawSegment(5, Rect(pad, pad + t * 0.5f, pad + t, midY - t * 0.25f))
        // G (Center)
        drawSegment(6, Rect(pad + t, midY - t * 0.5f, w - pad - t, midY + t * 0.5f))
    }
}

/**
 * Standard horizontal row of animated Canvas digits (HH:mm) tailored to the specified ClockStyle.
 */
@Composable
fun AnimatedCanvasTimeRow(
    timeString: String,
    style: ClockStyle,
    contentColor: Color,
    digitWidth: Dp,
    digitHeight: Dp,
    modifier: Modifier = Modifier,
    colonWidth: Dp = digitWidth * 0.40f,
    strokeWidthDp: Dp = (digitHeight.value * 0.055f).coerceIn(2.0f, 4.5f).dp,
    spacing: Dp = (digitWidth * 0.08f).coerceAtLeast(1.dp),
) {
    val chars = timeString.toList()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chars.forEachIndexed { index, ch ->
            if (ch == ':') {
                ThemedAnimatedCanvasDigit(
                    char = ch,
                    style = style,
                    staggerDelayMs = index * 90,
                    contentColor = contentColor.copy(alpha = 0.90f),
                    strokeWidthDp = strokeWidthDp * 0.9f,
                    modifier = Modifier.size(colonWidth, digitHeight),
                )
            } else {
                ThemedAnimatedCanvasDigit(
                    char = ch,
                    style = style,
                    staggerDelayMs = index * 90,
                    contentColor = contentColor,
                    strokeWidthDp = strokeWidthDp,
                    modifier = Modifier.size(digitWidth, digitHeight),
                )
            }
            if (index < chars.lastIndex) {
                Spacer(Modifier.width(spacing))
            }
        }
    }
}

/**
 * Stacked vertical presentation of animated Canvas digits (Hours on top, Minutes on bottom).
 */
@Composable
fun AnimatedCanvasStackedTime(
    hoursString: String,
    minutesString: String,
    style: ClockStyle,
    contentColor: Color,
    digitWidth: Dp,
    digitHeight: Dp,
    modifier: Modifier = Modifier,
    strokeWidthDp: Dp = (digitHeight.value * 0.055f).coerceIn(2.0f, 4.5f).dp,
    spacing: Dp = (digitWidth * 0.08f).coerceAtLeast(1.dp),
    minutesAlpha: Float = 0.85f,
) {
    val hChars = hoursString.toList()
    val mChars = minutesString.toList()

    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hours row
        Row(verticalAlignment = Alignment.CenterVertically) {
            hChars.forEachIndexed { index, ch ->
                ThemedAnimatedCanvasDigit(
                    char = ch,
                    style = style,
                    staggerDelayMs = index * 90,
                    contentColor = contentColor,
                    strokeWidthDp = strokeWidthDp,
                    modifier = Modifier.size(digitWidth, digitHeight),
                )
                if (index < hChars.lastIndex) {
                    Spacer(Modifier.width(spacing))
                }
            }
        }

        // Minutes row
        Row(verticalAlignment = Alignment.CenterVertically) {
            mChars.forEachIndexed { index, ch ->
                ThemedAnimatedCanvasDigit(
                    char = ch,
                    style = style,
                    staggerDelayMs = (hChars.size + index) * 90,
                    contentColor = contentColor.copy(alpha = minutesAlpha),
                    strokeWidthDp = strokeWidthDp,
                    modifier = Modifier.size(digitWidth, digitHeight),
                )
                if (index < mChars.lastIndex) {
                    Spacer(Modifier.width(spacing))
                }
            }
        }
    }
}
