// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.service

import android.content.Context
import dev.viagaralauncher.battery.RootBatteryStatsCollector
import dev.viagaralauncher.update.RootInstaller

object SystemUi {
    /**
     * Pulls down the notification shade.
     *
     * There is no public API for this. Reflection into StatusBarManager is what launchers
     * traditionally used, but it throws on Android 12+ for ordinary apps, so we prefer the
     * accessibility service (which the user has to enable once) and keep reflection as a
     * fallback for older builds.
     *
     * @return true if the shade was actually opened.
     */
    fun expandNotificationShade(context: Context): Boolean {
        if (ViagaraAccessibilityService.openNotificationShade()) return true

        return runCatching {
            val service = context.getSystemService("statusbar")
            val method = Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
            method.invoke(service)
            true
        }.getOrDefault(false)
    }

    /** Whether the reliable path is available; drives the prompt in settings. */
    fun canExpandShade(): Boolean = ViagaraAccessibilityService.isConnected

    /**
     * Locks or turns off the screen.
     *
     * If root access is available, executes `input keyevent 26` via root shell to turn off
     * the display while preserving the system's smooth display sleep animation.
     * If root is unavailable or fails, falls back transparently to [ViagaraAccessibilityService.lockScreen].
     *
     * @return true if the screen was turned off / locked successfully.
     */
    suspend fun lockScreen(): Boolean {
        if (RootInstaller.isRootAvailable() && RootBatteryStatsCollector.isRootAvailable()) {
            val rootSuccess = RootBatteryStatsCollector.lockScreen()
            if (rootSuccess) return true
        }
        return ViagaraAccessibilityService.lockScreen()
    }

    /** Synchronous fallback for accessibility screen locking. */
    fun lockScreenSync(): Boolean = ViagaraAccessibilityService.lockScreen()
}