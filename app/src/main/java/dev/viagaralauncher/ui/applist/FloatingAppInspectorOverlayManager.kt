// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.applist

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import dev.viagaralauncher.data.AppInfo

object FloatingAppInspectorOverlayManager {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    var isShowing: Boolean = false
        private set
    private var layoutParams: WindowManager.LayoutParams? = null

    /** Simple stub implementation that adds a transparent fullscreen view as overlay. */
    fun show(context: Context, app: AppInfo, displayName: String) {
        if (isShowing) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        layoutParams = params
        // Create a dummy transparent view as placeholder overlay.
        val view = View(context).apply { setBackgroundColor(0x00000000) }
        try {
            wm.addView(view, params)
            overlayView = view
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismiss() {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        try {
            wm.removeView(view)
        } catch (ignored: Exception) { }
        overlayView = null
        isShowing = false
        windowManager = null
        layoutParams = null
    }
}
