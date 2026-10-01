// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.viagaralauncher.ViagaraApp
import dev.viagaralauncher.data.ThemedIconStyle

/**
 * Renders a folder's custom icon, using the same override encoding apps use so a folder can
 * take an icon-pack drawable or a picture from the gallery just like any app.
 *
 * Automatically adapts to the user's Monet themed icon settings if enabled.
 * Returns false when the override can't be decoded, so the caller can fall back to drawing
 * the folder's app previews instead.
 */
@Composable
fun FolderIconImage(override: String, sizeDp: Int, modifier: Modifier = Modifier): Boolean {
    val context = LocalContext.current
    val viagaraApp = context.applicationContext as ViagaraApp
    val config = LocalIconConfig.current
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)

    val bitmap: ImageBitmap? = remember(override, px, config.themedIcons) {
        runCatching {
            val drawable = decodeIconOverride(context, viagaraApp, override) ?: return@runCatching null
            if (config.themedIcons) {
                generateMonochromeBitmap(drawable, px).asImageBitmap()
            } else {
                drawable.toBitmap(px, px).asImageBitmap()
            }
        }.getOrNull()
    }

    if (bitmap != null) {
        if (config.themedIcons) {
            when (config.themedIconStyle) {
                ThemedIconStyle.MATERIAL_YOU -> {
                    val squircleShape = remember { RoundedCornerShape(percent = 28) }
                    Box(
                        modifier = modifier
                            .size(sizeDp.dp)
                            .clip(squircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer, squircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.SrcIn),
                            modifier = Modifier.size((sizeDp * 0.74f).dp),
                        )
                    }
                }
                ThemedIconStyle.MINIMALIST -> {
                    Box(
                        modifier = modifier.size(sizeDp.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.SrcIn),
                            modifier = Modifier.size(sizeDp.dp),
                        )
                    }
                }
            }
        } else {
            Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(sizeDp.dp))
        }
        return true
    }
    return false
}