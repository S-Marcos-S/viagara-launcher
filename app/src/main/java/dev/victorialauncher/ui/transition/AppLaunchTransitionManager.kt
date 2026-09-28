// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.transition

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages app launch and exit transition animations, including the Android GestureNavContract
 * protocol and the icon settle/return animation on the home screen.
 */
object AppLaunchTransitionManager {
    private const val TAG = "AppTransition"

    // GestureNavContract constants from AOSP Launcher3
    const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
    const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
    const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
    const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"
    const val EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback"
    const val EXTRA_COMPONENT_NAME = "android.intent.extra.COMPONENT_NAME"
    const val EXTRA_USER = "android.intent.extra.USER"

    /** Cache of known on-screen bounding boxes of app icons */
    private val iconBoundsMap = ConcurrentHashMap<String, Rect>()

    /** The package name of the app that was most recently launched */
    @Volatile
    private var lastLaunchedPackage: String? = null
    @Volatile
    private var lastLaunchTime: Long = 0L

    /** Observable state in Compose: package name of the app currently returning/closing */
    private val _returningPackage = mutableStateOf<String?>(null)
    val returningPackage: State<String?> get() = _returningPackage

    private val mainHandler = Handler(Looper.getMainLooper())
    private var resetRunnable: Runnable? = null

    /** Messenger used to receive on-finish callback from SystemUI gesture navigation */
    private val finishMessenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == 0) {
            // SystemUI finished the exit morph animation
            Log.d(TAG, "GestureNav onFinishCallback received")
            true
        } else {
            false
        }
    })

    /**
     * Updates the known on-screen bounding box of an app's icon.
     */
    fun updateIconBounds(packageName: String, bounds: Rect) {
        iconBoundsMap[packageName] = bounds
    }

    /**
     * Retrieves the known on-screen bounding box for an app's icon, or null if unknown.
     */
    fun getIconBounds(packageName: String): Rect? {
        return iconBoundsMap[packageName]
    }

    /**
     * Called when an app is launched from any launcher surface.
     */
    fun onAppLaunched(packageName: String, bounds: Rect?) {
        lastLaunchedPackage = packageName
        lastLaunchTime = SystemClock.elapsedRealtime()
        if (bounds != null) {
            iconBoundsMap[packageName] = bounds
        }
    }

    /**
     * Handles the GestureNavContract intent sent by SystemUI during a return-to-home gesture.
     * Returns true if a gesture contract was processed.
     */
    fun handleGestureContract(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        val extras = intent.getBundleExtra(EXTRA_GESTURE_CONTRACT) ?: return false
        intent.removeExtra(EXTRA_GESTURE_CONTRACT)

        @Suppress("DEPRECATION")
        val componentName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } else {
            extras.getParcelable(EXTRA_COMPONENT_NAME)
        }

        @Suppress("DEPRECATION")
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(EXTRA_REMOTE_CALLBACK, Message::class.java)
        } else {
            extras.getParcelable(EXTRA_REMOTE_CALLBACK)
        }

        val replyMessenger = callback?.replyTo ?: return false
        val pkg = componentName?.packageName ?: lastLaunchedPackage ?: return false

        triggerReturnAnimation(pkg)

        // Find target position on screen
        val bounds = iconBoundsMap[pkg] ?: run {
            val dm = context.resources.displayMetrics
            val cx = dm.widthPixels / 2f
            val cy = dm.heightPixels / 2f
            val defaultSize = 56f * dm.density
            Rect(
                (cx - defaultSize / 2).toInt(),
                (cy - defaultSize / 2).toInt(),
                (cx + defaultSize / 2).toInt(),
                (cy + defaultSize / 2).toInt(),
            )
        }

        val targetRectF = RectF(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
        )

        val result = Bundle().apply {
            putParcelable(EXTRA_ICON_POSITION, targetRectF)
            putParcelable(EXTRA_ICON_SURFACE, null)
            putParcelable(EXTRA_ON_FINISH_CALLBACK, finishMessenger)
        }

        val response = Message.obtain().apply {
            copyFrom(callback)
            data = result
        }

        return try {
            replyMessenger.send(response)
            Log.d(TAG, "Replied to gesture nav contract with position: $targetRectF for $pkg")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reply to gesture nav contract", e)
            false
        }
    }

    /**
     * Called when MainActivity is resumed. Triggers the return animation for the
     * last launched app if returning within a reasonable time window.
     */
    fun onLauncherResume() {
        val pkg = lastLaunchedPackage
        val launchTime = lastLaunchTime
        if (pkg != null && SystemClock.elapsedRealtime() - launchTime < 30 * 60 * 1000L) {
            triggerReturnAnimation(pkg)
            lastLaunchedPackage = null
        }
    }

    /**
     * Triggers the visual return/settle animation for the given package.
     */
    fun triggerReturnAnimation(packageName: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            doTriggerReturn(packageName)
        } else {
            mainHandler.post { doTriggerReturn(packageName) }
        }
    }

    private fun doTriggerReturn(packageName: String) {
        resetRunnable?.let { mainHandler.removeCallbacks(it) }
        _returningPackage.value = packageName

        val runnable = Runnable {
            if (_returningPackage.value == packageName) {
                _returningPackage.value = null
            }
        }
        resetRunnable = runnable
        mainHandler.postDelayed(runnable, 900L)
    }

    fun isReturningApp(packageName: String): Boolean {
        return _returningPackage.value == packageName
    }
}
