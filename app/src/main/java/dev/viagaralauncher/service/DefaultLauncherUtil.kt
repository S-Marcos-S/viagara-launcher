// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.service

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object DefaultLauncherUtil {

    private const val REQUEST_CODE_ROLE_HOME = 1001

    /**
     * Checks whether Viagara Launcher is currently the system's default home launcher.
     */
    fun isDefaultLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    /**
     * Prompts the user to set Viagara Launcher as the default home app.
     * Uses Settings.ACTION_HOME_SETTINGS as the primary and most reliable mechanism to directly
     * open the system's "Default home app" configuration screen across Android versions and OEM skins,
     * with comprehensive fallbacks for role manager, default apps, OEM settings, and app info.
     */
    fun requestSetDefaultLauncher(context: Context) {
        // 1. Primary: Direct Home App settings screen (Settings > Apps > Default Apps > Home App)
        try {
            val homeIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            return
        } catch (_: Exception) {
            // Fall through to fallback options
        }

        // 2. Default Apps management screen (Settings > Apps > Default Apps)
        try {
            val manageDefaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(manageDefaultAppsIntent)
            return
        } catch (_: Exception) {
            // Fall through
        }

        // 3. RoleManager (Android 10+) if supported and invoked from an Activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    val activity = context.findActivity()
                    if (activity != null) {
                        @Suppress("DEPRECATION")
                        activity.startActivityForResult(intent, REQUEST_CODE_ROLE_HOME)
                        return
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return
                    }
                } catch (_: Exception) {
                    // Fall through
                }
            }
        }

        // 4. OEM-specific default app settings (e.g. Xiaomi / MIUI / HyperOS)
        try {
            val miuiIntent = Intent("miui.intent.action.PREFERRED_APP_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(miuiIntent)
            return
        } catch (_: Exception) {
            // Fall through
        }

        // 5. App Info details screen for Viagara Launcher (contains "Default app" / "Home app" setting)
        try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetailsIntent)
            return
        } catch (_: Exception) {
            // Fall through
        }

        // 6. Final fallback: Main device settings screen
        try {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
        } catch (_: Exception) {
            // Ignore if nothing can be opened
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
