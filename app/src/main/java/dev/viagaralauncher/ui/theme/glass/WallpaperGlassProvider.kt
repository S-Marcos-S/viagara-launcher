// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.theme.glass

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.ColorUtils
import dev.viagaralauncher.wallpaper.WallpaperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Manages wallpaper textures and renders refractive liquid/frosted glass backdrops
 * mapped directly to the screen coordinate system.
 */
object WallpaperGlassProvider {

    private const val SHARP_WIDTH = 720
    private const val FROSTED_SAMPLE_WIDTH = 220

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _wallpaperTick = MutableStateFlow(0)
    val wallpaperTick: StateFlow<Int> = _wallpaperTick.asStateFlow()

    @Volatile
    private var cachedSharp: Bitmap? = null

    @Volatile
    private var cachedBlurred: Bitmap? = null

    @Volatile
    private var fallbackBitmap: Bitmap? = null

    private var liquidShaderInstance: Any? = null
    @Volatile
    private var appContextRef: Context? = null

    private val emptyMaskShader: BitmapShader by lazy {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        if (!initialized.compareAndSet(false, true)) return
        refreshWallpaper(appContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val wm = WallpaperManager.getInstance(appContext)
            runCatching {
                wm.addOnColorsChangedListener(
                    { _, which ->
                        if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                            refreshWallpaper(appContext)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            }
        }

        scope.launch {
            WallpaperRepository.wallpaperUpdateTick.collect { tick ->
                if (tick > 0L) {
                    refreshWallpaper(appContext)
                }
            }
        }
    }

    fun refreshWallpaper(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        scope.launch {
            val bitmap = loadWallpaperBitmap(appContext)
            if (bitmap != null) {
                processWallpaper(bitmap)
            } else {
                val dm = appContext.resources.displayMetrics
                val w = min(SHARP_WIDTH, dm.widthPixels)
                val h = max(1, (w.toFloat() * dm.heightPixels / dm.widthPixels).roundToInt())
                val fb = createFallbackWallpaper(appContext, w, h)
                fallbackBitmap = fb
                processWallpaper(fb)
            }
            withContext(Dispatchers.Main) {
                _wallpaperTick.value++
            }
        }
    }

    private fun processWallpaper(source: Bitmap) {
        val aspect = source.width.toFloat() / source.height.coerceAtLeast(1)
        val sharpW = min(SHARP_WIDTH, max(1, source.width))
        val sharpH = max(1, (sharpW / aspect).roundToInt())
        val sharp = if (source.width == sharpW && source.height == sharpH) {
            source
        } else {
            Bitmap.createScaledBitmap(source, sharpW, sharpH, true)
        }
        cachedSharp = sharp

        val blurW = min(FROSTED_SAMPLE_WIDTH, sharpW)
        val blurred = GlassShader.sampleBlur(sharp, blurW, 2)
        cachedBlurred = blurred
    }

    private fun loadWallpaperBitmap(context: Context): Bitmap? {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val d = wm.drawable
            if (d is BitmapDrawable && d.bitmap != null) {
                return d.bitmap
            }
            if (d != null && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                val b = Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                d.setBounds(0, 0, b.width, b.height)
                d.draw(c)
                return b
            }
        }

        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val d = wm.getBuiltInDrawable(WallpaperManager.FLAG_SYSTEM)
            if (d is BitmapDrawable && d.bitmap != null) {
                return d.bitmap
            }
        }

        runCatching {
            val path = "/data/system/users/0/wallpaper"
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val b = BitmapFactory.decodeFile(file.absolutePath)
                if (b != null) return b
            }
        }

        return null
    }

    private fun createFallbackWallpaper(context: Context, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val baseColor1 = if (isDark) 0xFF14171F.toInt() else 0xFFE6E9F0.toInt()
        val baseColor2 = if (isDark) 0xFF1F2430.toInt() else 0xFFD8DCE6.toInt()
        val spotColor1 = if (isDark) 0xFF2B3A4A.toInt() else 0xFFB0C4DE.toInt()
        val spotColor2 = if (isDark) 0xFF352B44.toInt() else 0xFFD2BFE3.toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), baseColor1, baseColor2, Shader.TileMode.CLAMP)
        }
        canvas.drawPaint(paint)

        val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.35f, height * 0.3f, width * 0.65f,
                spotColor1, 0, Shader.TileMode.CLAMP
            )
        }
        canvas.drawPaint(spotPaint)

        spotPaint.shader = RadialGradient(
            width * 0.7f, height * 0.75f, width * 0.55f,
            spotColor2, 0, Shader.TileMode.CLAMP
        )
        canvas.drawPaint(spotPaint)

        return bitmap
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getOrCreateLiquidShader(): RuntimeShader {
        var shader = liquidShaderInstance as? RuntimeShader
        if (shader == null) {
            shader = RuntimeShader(GlassShader.LIQUID)
            liquidShaderInstance = shader
        }
        return shader
    }

    fun getRealWindowSize(context: Context, view: View? = null): Pair<Float, Float> {
        val rootView = view?.rootView
        if (rootView != null && rootView.width > 0 && rootView.height > 0) {
            return Pair(rootView.width.toFloat(), rootView.height.toFloat())
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    return Pair(bounds.width().toFloat(), bounds.height().toFloat())
                }
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    return Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
                }
            }
        }
        val dm = context.resources.displayMetrics
        return Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
    }

    fun drawGlassElement(
        canvas: Canvas,
        bounds: RectF,
        positionInWindow: Offset,
        screenWidth: Float,
        screenHeight: Float,
        corner: Float = 0f,
        topCorner: Float = corner,
        bottomCorner: Float = corner,
        style: GlassStyle = GlassStyle.DEFAULT,
        surfaceColor: Int,
        tintOpacity: Int = 18,
        density: Float,
        night: Boolean,
    ) {
        if (bounds.isEmpty) return

        val path = Path().apply {
            if (topCorner == bottomCorner) {
                addRoundRect(bounds, topCorner, topCorner, Path.Direction.CW)
            } else {
                val radii = floatArrayOf(
                    topCorner, topCorner,
                    topCorner, topCorner,
                    bottomCorner, bottomCorner,
                    bottomCorner, bottomCorner,
                )
                addRoundRect(bounds, radii, Path.Direction.CW)
            }
        }

        // Draw shadow behind the element
        GlassShader.drawShadow(canvas, path, bounds, style.shadow, density)

        val base = cachedSharp ?: fallbackBitmap
        var targetWidth = screenWidth
        var targetHeight = screenHeight
        if (targetWidth <= 0f || targetHeight <= 0f) {
            appContextRef?.let { ctx ->
                val (w, h) = getRealWindowSize(ctx)
                targetWidth = w
                targetHeight = h
            }
        }

        if (base != null && targetWidth > 0f && targetHeight > 0f) {
            val isTiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val activeBitmap = cachedBlurred ?: base
            val scale = max(targetWidth / activeBitmap.width.toFloat(), targetHeight / activeBitmap.height.toFloat())
            val scaledWidth = activeBitmap.width.toFloat() * scale
            val scaledHeight = activeBitmap.height.toFloat() * scale
            val dx = (targetWidth - scaledWidth) / 2f
            val dy = (targetHeight - scaledHeight) / 2f

            val shaderMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
                postTranslate(-positionInWindow.x, -positionInWindow.y)
            }

            if (isTiramisu) {
                val shader = getOrCreateLiquidShader()
                val imageShader = BitmapShader(activeBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(shaderMatrix)
                }
                shader.setInputShader("image", imageShader)
                shader.setFloatUniform("origin", bounds.left, bounds.top)
                shader.setFloatUniform("size", bounds.width(), bounds.height())
                GlassShader.setRadii(shader, topCorner, bottomCorner)
                GlassShader.applyStyle(shader, bounds.width(), bounds.height(), style)
                GlassShader.setDim(shader, Color.TRANSPARENT)
                shader.setInputShader("mask", emptyMaskShader)
                shader.setFloatUniform("useMask", 0f)
                shader.setFloatUniform("maskOrigin", 0f, 0f)
                shader.setFloatUniform("maskScale", 1f)
                shader.setFloatUniform("maskRange", 1f)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader
                }
                canvas.drawPath(path, paint)
            } else {
                val imageShader = BitmapShader(activeBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(shaderMatrix)
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = imageShader
                    colorFilter = GlassShader.backdropFilter(Color.TRANSPARENT, style.saturation)
                }
                canvas.drawPath(path, paint)
            }
        } else {
            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (night) 0x33FFFFFF else 0x55FFFFFF
            }
            canvas.drawPath(path, basePaint)
        }

        // Draw surface tint overlay
        if (tintOpacity > 0) {
            val tintAlpha = (tintOpacity.coerceIn(0, 100) * 255 / 100)
            val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(surfaceColor, tintAlpha)
            }
            canvas.drawPath(path, tintPaint)
        }

        // Draw outline / specular rim
        GlassShader.drawOutline(canvas, bounds, topCorner, bottomCorner, style, 1f, density)
    }
}
