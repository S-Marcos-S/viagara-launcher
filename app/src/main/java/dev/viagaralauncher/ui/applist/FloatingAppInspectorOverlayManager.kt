// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.applist

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.may.taskmanager.ui.root.AppRootInspectorDialog
import dev.may.taskmanager.data.AppInfo

object FloatingAppInspectorOverlayManager {
    private var windowManager: WindowManager? = null
    private var overlayView: Dialog? = null
    var isShowing: Boolean = false
        private set
    private var layoutParams: WindowManager.LayoutParams? = null
    private val isMinimizedState = mutableStateOf(false)

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
        ).apply {
            gravity = Gravity.CENTER
        }
        layoutParams = params
        val dialog = Dialog(context, DialogProperties(usePlatformDefaultWidth = false)) {
            AppRootInspectorDialog(app = app, displayName = displayName) {
                dismiss()
            }
        }
        overlayView = dialog
        isShowing = true
        try {
            wm.addView(dialog.window?.decorView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            isShowing = false
            overlayView = null
        }
    }

    fun dismiss() {
        val wm = windowManager ?: return
        val view = overlayView?.window?.decorView ?: return
        try {
            wm.removeView(view)
        } catch (ignored: Exception) { }
        overlayView = null
        isShowing = false
        windowManager = null
        layoutParams = null
    }
}
