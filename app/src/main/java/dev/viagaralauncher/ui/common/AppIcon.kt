// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.viagaralauncher.ViagaraApp
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.data.ThemedIconStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Per-app icon overrides are stored either as a gallery `content://` URI or as `pack:<pkg>:<drawable>`. */
const val ICON_PACK_OVERRIDE_PREFIX = "pack:"

fun encodePackOverride(packPackage: String, drawableName: String) =
    "$ICON_PACK_OVERRIDE_PREFIX$packPackage:$drawableName"

/**
 * Icons are rasterised once and cached. They used to be drawn by handing a Drawable to an
 * ImageView through AndroidView, which meant inflating a real Android View per row — far too
 * expensive for a list that rebuilds while scrubbing.
 *
 * The cache is bounded by *bytes*, not entry count. A fixed count is the wrong unit here: the
 * cache key includes the rasterised pixel size, so moving the icon-size slider adds a whole
 * new generation of bitmaps rather than replacing the old one, and at 96dp on a dense screen
 * a single icon is ~450 KB. Sizing by bytes against the heap keeps that bounded no matter how
 * many apps are installed or how large the icons are set.
 */
private object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(maxSizeKb()) {
        // Reported in KB so the running total cannot overflow an Int.
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
    }

    private fun maxSizeKb(): Int {
        val heapKb = Runtime.getRuntime().maxMemory() / 1024L
        return (heapKb / 8L).toInt().coerceIn(4 * 1024, 96 * 1024)
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) = cache.put(key, value)

    /**
     * Dropped wholesale when a package is added, removed or changed: an app that ships a new
     * icon in an update keeps none of the key's components, so nothing else would invalidate
     * the stale bitmap.
     */
    fun clear() = cache.evictAll()
}

fun clearIconCache() = IconCache.clear()

private fun iconCacheKey(app: AppInfo, iconPack: String?, override: String?, px: Int) =
    "${app.key}|$iconPack|$override|$px"

private fun themedIconCacheKey(app: AppInfo, iconPack: String?, override: String?, px: Int) =
    "themed_v3|${app.key}|$iconPack|$override|$px"

/**
 * Decode every app's icon ahead of time, off the main thread. Without this the first open of
 * the A-Z list rasterises a screenful of icons synchronously during composition, which is
 * what made it take a beat to appear.
 *
 * Work is fanned out across the dispatcher rather than run one icon at a time, and
 * [priorityKeys] (favorites and folder members) go first so the home screen is covered before
 * the long tail of everything else installed.
 */
suspend fun warmIconCache(
    context: Context,
    apps: List<AppInfo>,
    iconPack: String?,
    overrides: Map<String, String>,
    px: Int,
    priorityKeys: Set<String> = emptySet(),
    themedIcons: Boolean = false,
) {
    if (px <= 0 || apps.isEmpty()) return
    val viagaraApp = context.applicationContext as ViagaraApp

    fun warm(app: AppInfo) {
        val key = if (themedIcons) {
            themedIconCacheKey(app, iconPack, overrides[app.key], px)
        } else {
            iconCacheKey(app, iconPack, overrides[app.key], px)
        }
        if (IconCache.get(key) != null) return
        runCatching {
            val drawable = resolveDrawable(context, viagaraApp, app, iconPack, overrides[app.key])
            val imageBitmap = if (themedIcons) {
                generateMonochromeBitmap(drawable, px, app.label).asImageBitmap()
            } else {
                drawable.toBitmap(px, px).asImageBitmap()
            }
            IconCache.put(key, imageBitmap)
        }
    }

    val (first, rest) = apps.partition { it.key in priorityKeys }
    // Explicitly off the caller's dispatcher: this is invoked from a LaunchedEffect, which
    // runs on Main, and coroutineScope/async would inherit it.
    withContext(Dispatchers.Default) {
        for (group in listOf(first, rest)) {
            if (group.isEmpty()) continue
            group.chunked(CHUNK_SIZE)
                .map { chunk -> async { chunk.forEach(::warm) } }
                .awaitAll()
        }
    }
}

/** Big enough that per-task overhead stays negligible against a drawable decode. */
private const val CHUNK_SIZE = 16

/**
 * Icon pack, per-app overrides, and themed icon settings, provided once for the whole tree.
 */
@Immutable
data class IconConfig(
    val pack: String?,
    val overrides: Map<String, String>,
    val themedIcons: Boolean = false,
    val themedIconStyle: ThemedIconStyle = ThemedIconStyle.MATERIAL_YOU,
)

val LocalIconConfig = staticCompositionLocalOf {
    IconConfig(null, emptyMap(), false, ThemedIconStyle.MATERIAL_YOU)
}

@Composable
fun AppIcon(app: AppInfo, sizeDp: Int, modifier: Modifier = Modifier) {
    val config = LocalIconConfig.current
    if (config.themedIcons) {
        ThemedAppIcon(
            app = app,
            sizeDp = sizeDp,
            modifier = modifier,
            style = config.themedIconStyle,
        )
    } else {
        val context = LocalContext.current
        val viagaraApp = context.applicationContext as ViagaraApp
        val iconPackPackage = config.pack
        val overrideValue = config.overrides[app.key]
        val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)

        val cacheKey = iconCacheKey(app, iconPackPackage, overrideValue, px)
        val bitmap: ImageBitmap? = remember(cacheKey) {
            IconCache.get(cacheKey) ?: runCatching {
                val drawable = resolveDrawable(context, viagaraApp, app, iconPackPackage, overrideValue)
                drawable.toBitmap(px, px).asImageBitmap().also { IconCache.put(cacheKey, it) }
            }.getOrNull()
        }

        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = app.label, modifier = modifier.size(sizeDp.dp))
        } else {
            Box(modifier = modifier.size(sizeDp.dp))
        }
    }
}

/**
 * Renders an app icon using the system dynamic color (Material You / Monet theme).
 * Supports both [ThemedIconStyle.MATERIAL_YOU] (adaptive container + glyph) and
 * [ThemedIconStyle.MINIMALIST] (flat glyph tinted with wallpaper Monet accent color).
 */
@Composable
fun ThemedAppIcon(
    app: AppInfo,
    sizeDp: Int,
    modifier: Modifier = Modifier,
    style: ThemedIconStyle = LocalIconConfig.current.themedIconStyle,
    tintColor: Color? = null,
    containerColor: Color? = null,
) {
    val context = LocalContext.current
    val viagaraApp = context.applicationContext as ViagaraApp
    val config = LocalIconConfig.current
    val iconPackPackage = config.pack
    val overrideValue = config.overrides[app.key]
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)

    val cacheKey = themedIconCacheKey(app, iconPackPackage, overrideValue, px)
    val bitmap: ImageBitmap? = remember(cacheKey) {
        IconCache.get(cacheKey) ?: runCatching {
            val drawable = resolveDrawable(context, viagaraApp, app, iconPackPackage, overrideValue)
            generateMonochromeBitmap(drawable, px, app.label).asImageBitmap().also { IconCache.put(cacheKey, it) }
        }.getOrNull()
    }

    val effectiveTintColor = tintColor ?: MaterialTheme.colorScheme.primary
    val effectiveContainerColor = containerColor ?: MaterialTheme.colorScheme.secondaryContainer

    when (style) {
        ThemedIconStyle.MATERIAL_YOU -> {
            val squircleShape = remember { RoundedCornerShape(percent = 28) }
            Box(
                modifier = modifier
                    .size(sizeDp.dp)
                    .clip(squircleShape)
                    .background(effectiveContainerColor, squircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.label,
                        colorFilter = ColorFilter.tint(effectiveTintColor, BlendMode.SrcIn),
                        modifier = Modifier.size((sizeDp * 0.74f).dp),
                    )
                }
            }
        }
        ThemedIconStyle.MINIMALIST -> {
            Box(
                modifier = modifier.size(sizeDp.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.label,
                        colorFilter = ColorFilter.tint(effectiveTintColor, BlendMode.SrcIn),
                        modifier = Modifier.size(sizeDp.dp),
                    )
                }
            }
        }
        ThemedIconStyle.OLED_DARK -> {
            val squircleShape = remember { RoundedCornerShape(percent = 28) }
            Box(
                modifier = modifier
                    .size(sizeDp.dp)
                    .clip(squircleShape)
                    .background(Color.Black, squircleShape)
                    .border(1.dp, Color(0xFF262626), squircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.label,
                        colorFilter = ColorFilter.tint(Color(0xFFEDEDED), BlendMode.SrcIn),
                        modifier = Modifier.size((sizeDp * 0.72f).dp),
                    )
                }
            }
        }
    }
}

internal fun generateMonochromeBitmap(drawable: Drawable, px: Int, label: String = ""): Bitmap {
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Android 13+ official monochrome adaptive icon layer
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
        val mono = drawable.monochrome
        if (mono != null) {
            val layer = mono.mutate()
            layer.setBounds(0, 0, px, px)
            layer.draw(canvas)
            convertToWhiteMask(bitmap)
            if (hasSufficientVisiblePixels(bitmap)) {
                return autoFrameGlyphBitmap(bitmap, px)
            }
        }
    }

    // 2. Adaptive icon foreground layer fallback
    if (drawable is AdaptiveIconDrawable) {
        bitmap.eraseColor(AndroidColor.TRANSPARENT)
        val fg = drawable.foreground.mutate()
        fg.setBounds(0, 0, px, px)
        fg.draw(canvas)
        stripCornerBackground(bitmap)
        convertToWhiteMask(bitmap)
        if (hasSufficientVisiblePixels(bitmap)) {
            return autoFrameGlyphBitmap(bitmap, px)
        }
    }

    // 3. Fallback for non-adaptive drawables (legacy or custom icons)
    bitmap.eraseColor(AndroidColor.TRANSPARENT)
    val target = drawable.mutate()
    target.setBounds(0, 0, px, px)
    target.draw(canvas)
    stripCornerBackground(bitmap)
    convertToWhiteMask(bitmap)
    if (hasSufficientVisiblePixels(bitmap)) {
        return autoFrameGlyphBitmap(bitmap, px)
    }

    // 4. Absolute fallback: Monogram with first letter of the app label
    return generateMonogramBitmap(label, px)
}

private fun stripCornerBackground(bitmap: Bitmap) {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 8 || h < 8) return

    val margin = 2
    val c1 = bitmap.getPixel(margin, margin)
    val c2 = bitmap.getPixel(w - margin - 1, margin)
    val c3 = bitmap.getPixel(margin, h - margin - 1)
    val c4 = bitmap.getPixel(w - margin - 1, h - margin - 1)

    val a1 = AndroidColor.alpha(c1)
    val a2 = AndroidColor.alpha(c2)
    val a3 = AndroidColor.alpha(c3)
    val a4 = AndroidColor.alpha(c4)

    val corners = intArrayOf(c1, c2, c3, c4)
    val alphas = intArrayOf(a1, a2, a3, a4)
    var opaqueCorners = 0
    for (a in alphas) {
        if (a > 180) opaqueCorners++
    }

    if (opaqueCorners < 3) return

    var totalR = 0
    var totalG = 0
    var totalB = 0
    var count = 0
    for (i in 0 until 4) {
        if (alphas[i] > 180) {
            totalR += AndroidColor.red(corners[i])
            totalG += AndroidColor.green(corners[i])
            totalB += AndroidColor.blue(corners[i])
            count++
        }
    }
    if (count == 0) return
    val bgR = totalR / count
    val bgG = totalG / count
    val bgB = totalB / count

    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    val toleranceSq = 42 * 42
    var strippedCount = 0

    for (i in pixels.indices) {
        val p = pixels[i]
        val a = AndroidColor.alpha(p)
        if (a > 180) {
            val dr = AndroidColor.red(p) - bgR
            val dg = AndroidColor.green(p) - bgG
            val db = AndroidColor.blue(p) - bgB
            val distSq = dr * dr + dg * dg + db * db
            if (distSq <= toleranceSq) {
                pixels[i] = 0
                strippedCount++
            }
        }
    }

    if (strippedCount < (pixels.size * 0.95)) {
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}

private fun convertToWhiteMask(bitmap: Bitmap) {
    val width = bitmap.width
    val height = bitmap.height
    val count = width * height
    val pixels = IntArray(count)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var minLum = 1.0f
    var maxLum = 0.0f
    var hasVisiblePixels = false

    val lums = FloatArray(count)
    for (i in 0 until count) {
        val pixel = pixels[i]
        val a = AndroidColor.alpha(pixel)
        if (a > 25) {
            val r = AndroidColor.red(pixel)
            val g = AndroidColor.green(pixel)
            val b = AndroidColor.blue(pixel)
            val lum = (299 * r + 587 * g + 114 * b) / 255000f
            lums[i] = lum
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
            hasVisiblePixels = true
        }
    }

    if (!hasVisiblePixels) return

    val contrast = maxLum - minLum
    val hasContrast = contrast > 0.25f

    for (i in 0 until count) {
        val pixel = pixels[i]
        val a = AndroidColor.alpha(pixel)
        if (a <= 25) {
            pixels[i] = 0
            continue
        }

        val lum = lums[i]
        val finalAlpha = if (hasContrast) {
            val normLum = ((lum - minLum) / contrast).coerceIn(0f, 1f)
            val factor = 0.35f + 0.65f * normLum
            (a * factor).toInt().coerceIn(0, 255)
        } else {
            a
        }

        pixels[i] = AndroidColor.argb(finalAlpha, 255, 255, 255)
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun hasSufficientVisiblePixels(bitmap: Bitmap): Boolean {
    val count = bitmap.width * bitmap.height
    val pixels = IntArray(count)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    var visible = 0
    for (p in pixels) {
        if (AndroidColor.alpha(p) > 30) visible++
    }
    return visible >= (count * 0.02)
}

private fun autoFrameGlyphBitmap(src: Bitmap, targetPx: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= 0 || h <= 0) return src

    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1

    for (y in 0 until h) {
        val rowOffset = y * w
        for (x in 0 until w) {
            val alpha = AndroidColor.alpha(pixels[rowOffset + x])
            if (alpha > 20) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }

    if (maxX < minX || maxY < minY) {
        return src
    }

    val cropW = maxX - minX + 1
    val cropH = maxY - minY + 1

    if (cropW < 4 || cropH < 4) {
        return src
    }

    val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val targetContentSize = targetPx * 0.86f
    val scale = minOf(targetContentSize / cropW.toFloat(), targetContentSize / cropH.toFloat())

    val destW = cropW * scale
    val destH = cropH * scale
    val destLeft = (targetPx - destW) / 2f
    val destTop = (targetPx - destH) / 2f

    val srcRect = Rect(minX, minY, maxX + 1, maxY + 1)
    val destRect = RectF(destLeft, destTop, destLeft + destW, destTop + destH)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(src, srcRect, destRect, paint)

    return output
}

private fun generateMonogramBitmap(label: String, px: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val text = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "A"

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = px * 0.65f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val yPos = (px / 2f) - ((paint.descent() + paint.ascent()) / 2f)
    canvas.drawText(text, px / 2f, yPos, paint)
    return bitmap
}

/**
 * Resolves an icon override — an icon-pack drawable or a picture from the gallery — for
 * either an app or a folder. Both use the same encoding on purpose, so a folder can take any
 * icon an app can.
 */
internal fun decodeIconOverride(
    context: Context,
    viagaraApp: ViagaraApp,
    override: String?,
): Drawable? = when {
    override == null -> null

    override.startsWith(ICON_PACK_OVERRIDE_PREFIX) -> {
        val body = override.removePrefix(ICON_PACK_OVERRIDE_PREFIX)
        val split = body.lastIndexOf(':')
        if (split <= 0) {
            null
        } else {
            viagaraApp.iconPackRepository.loadPackDrawable(
                body.substring(0, split),
                body.substring(split + 1),
            )
        }
    }

    else -> runCatching {
        val uri = Uri.parse(override)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            Drawable.createFromStream(stream, uri.toString())
        }
    }.getOrNull()
}

private fun resolveDrawable(
    context: Context,
    viagaraApp: ViagaraApp,
    app: AppInfo,
    iconPackPackage: String?,
    overrideValue: String?,
): Drawable =
    decodeIconOverride(context, viagaraApp, overrideValue)
        ?: viagaraApp.iconPackRepository.getIcon(iconPackPackage, app.componentName) {
            viagaraApp.appRepository.loadIcon(app.componentName)
        }