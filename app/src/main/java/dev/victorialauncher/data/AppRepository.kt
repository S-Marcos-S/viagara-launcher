// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.View
import dev.victorialauncher.VictoriaApp

class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun queryAllApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                AppInfo(
                    componentName = ComponentName(ai.packageName, ai.name),
                    label = ri.loadLabel(pm)?.toString() ?: ai.packageName,
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    fun loadIcon(componentName: ComponentName): Drawable {
        return try {
            pm.getActivityIcon(componentName)
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                pm.getApplicationIcon(componentName.packageName)
            } catch (e2: PackageManager.NameNotFoundException) {
                pm.defaultActivityIcon
            }
        }
    }

    /**
     * Starts an activity using Android's standard scale-up expansion and scale-down compaction animation.
     *
     * @param componentName The component to launch.
     * @param sourceBounds The bounding box of the originating icon/element on screen.
     * @param sourceView The view relative to which the animation is performed.
     * @return true if started successfully, false otherwise.
     */
    fun launch(
        componentName: ComponentName,
        sourceBounds: Rect? = null,
        sourceView: View? = null,
    ): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val targetBounds = sourceBounds ?: run {
            val dm = context.resources.displayMetrics
            val cx = dm.widthPixels / 2
            val cy = dm.heightPixels / 2
            val defaultSize = (48 * dm.density).toInt().coerceAtLeast(1)
            Rect(cx - defaultSize / 2, cy - defaultSize / 2, cx + defaultSize / 2, cy + defaultSize / 2)
        }
        intent.sourceBounds = targetBounds

        val targetView = sourceView
            ?: VictoriaApp.currentActivity?.get()?.window?.decorView

        val optionsBundle = if (targetView != null) {
            val startX = targetBounds.left.coerceAtLeast(0)
            val startY = targetBounds.top.coerceAtLeast(0)
            val width = targetBounds.width().coerceAtLeast(1)
            val height = targetBounds.height().coerceAtLeast(1)
            dev.victorialauncher.ui.transition.AppLaunchTransitionManager.onAppLaunched(componentName.packageName, targetBounds)
            ActivityOptions.makeClipRevealAnimation(targetView, startX, startY, width, height).toBundle()
        } else {
            dev.victorialauncher.ui.transition.AppLaunchTransitionManager.onAppLaunched(componentName.packageName, targetBounds)
            null
        }

        val activity = VictoriaApp.currentActivity?.get()
        return try {
            if (activity != null) {
                if (optionsBundle != null) {
                    activity.startActivity(intent, optionsBundle)
                } else {
                    activity.startActivity(intent)
                }
            } else {
                if (optionsBundle != null) {
                    context.startActivity(intent, optionsBundle)
                } else {
                    context.startActivity(intent)
                }
            }
            true
        } catch (e: Exception) {
            // App may have been uninstalled since the list was built; ignore.
            false
        }
    }

    fun openAppInfo(
        packageName: String,
        sourceBounds: Rect? = null,
        sourceView: View? = null,
    ) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val targetBounds = sourceBounds ?: run {
            val dm = context.resources.displayMetrics
            val cx = dm.widthPixels / 2
            val cy = dm.heightPixels / 2
            val defaultSize = (48 * dm.density).toInt().coerceAtLeast(1)
            Rect(cx - defaultSize / 2, cy - defaultSize / 2, cx + defaultSize / 2, cy + defaultSize / 2)
        }
        intent.sourceBounds = targetBounds

        val targetView = sourceView
            ?: VictoriaApp.currentActivity?.get()?.window?.decorView

        val optionsBundle = if (targetView != null) {
            val startX = targetBounds.left.coerceAtLeast(0)
            val startY = targetBounds.top.coerceAtLeast(0)
            val width = targetBounds.width().coerceAtLeast(1)
            val height = targetBounds.height().coerceAtLeast(1)
            ActivityOptions.makeClipRevealAnimation(targetView, startX, startY, width, height).toBundle()
        } else {
            null
        }

        val activity = VictoriaApp.currentActivity?.get()
        try {
            if (optionsBundle != null) {
                if (activity != null) {
                    activity.startActivity(intent, optionsBundle)
                } else {
                    context.startActivity(intent, optionsBundle)
                }
            } else {
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            try {
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
        }
    }
}