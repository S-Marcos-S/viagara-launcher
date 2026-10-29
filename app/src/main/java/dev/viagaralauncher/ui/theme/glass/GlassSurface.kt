// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.theme.glass

import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.viagaralauncher.ui.common.LocalIconConfig
import dev.viagaralauncher.ui.theme.LocalGlassThemeActive
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor

/**
 * Universal glass container that renders refractive liquid/frosted glass backdrop
 * mapped 1:1 to screen wallpaper coordinates.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerPx: Float? = null,
    topCornerPx: Float? = null,
    bottomCornerPx: Float? = null,
    cornerFraction: Float? = null,
    tintOpacity: Int = 20,
    style: GlassStyle = GlassStyle.DEFAULT,
    surfaceColor: Int = MaterialTheme.colorScheme.secondaryContainer.toArgb(),
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val (screenWidthPx, screenHeightPx) = remember(context, view, configuration) {
        WallpaperGlassProvider.getRealWindowSize(context, view)
    }
    var positionInWindow by remember { mutableStateOf(Offset.Zero) }
    val elementBounds = remember { RectF() }

    LaunchedEffect(Unit) {
        WallpaperGlassProvider.init(context)
    }
    val wallpaperTick by WallpaperGlassProvider.wallpaperTick.collectAsState()
    val iconConfig = LocalIconConfig.current
    val effectiveCornerFraction = cornerFraction ?: iconConfig.iconShape.cornerFraction

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                positionInWindow = coords.positionInWindow()
            },
        contentAlignment = Alignment.Center,
    ) {
        val tick = wallpaperTick
        Canvas(modifier = Modifier.matchParentSize()) {
            val dummy = tick
            drawIntoCanvas { composeCanvas ->
                val defaultCorner = cornerPx ?: (size.width * effectiveCornerFraction)
                val resolvedTopCorner = topCornerPx ?: defaultCorner
                val resolvedBottomCorner = bottomCornerPx ?: defaultCorner
                elementBounds.set(0f, 0f, size.width, size.height)
                WallpaperGlassProvider.drawGlassElement(
                    canvas = composeCanvas.nativeCanvas,
                    bounds = elementBounds,
                    positionInWindow = positionInWindow,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx,
                    topCorner = resolvedTopCorner,
                    bottomCorner = resolvedBottomCorner,
                    style = style,
                    surfaceColor = surfaceColor,
                    tintOpacity = tintOpacity,
                    density = density,
                    night = isDark,
                )
            }
        }
        content()
    }
}

/**
 * Universal container for modal dialogs, popups and sheets that automatically
 * applies full refractive liquid glass backdrop when the Glass theme is active.
 */
@Composable
fun GlassDialogContainer(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Dp = 28.dp,
    topCornerDp: Dp = cornerRadiusDp,
    bottomCornerDp: Dp = cornerRadiusDp,
    isGlassActive: Boolean = LocalGlassThemeActive.current,
    style: GlassStyle = GlassStyle.DEFAULT,
    tintOpacity: Int = 20,
    surfaceColor: Int = MaterialTheme.colorScheme.secondaryContainer.toArgb(),
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val topPx = with(density) { topCornerDp.toPx() }
    val bottomPx = with(density) { bottomCornerDp.toPx() }
    val shape = RoundedCornerShape(
        topStart = topCornerDp,
        topEnd = topCornerDp,
        bottomEnd = bottomCornerDp,
        bottomStart = bottomCornerDp,
    )

    if (isGlassActive) {
        GlassSurface(
            modifier = modifier.clip(shape),
            topCornerPx = topPx,
            bottomCornerPx = bottomPx,
            style = style,
            tintOpacity = tintOpacity,
            surfaceColor = surfaceColor,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.clip(shape),
            shape = shape,
            color = dynamicSurfaceColor(),
            border = BorderStroke(1.dp, dynamicBorderColor()),
            tonalElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}
