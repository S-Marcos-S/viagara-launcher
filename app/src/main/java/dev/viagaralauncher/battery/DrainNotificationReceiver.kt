// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.viagaralauncher.network.NetSpeedSettings
import dev.viagaralauncher.network.SpeedMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for battery drain notification actions (Reset session, etc.)
 */
class DrainNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RESET = "dev.viagaralauncher.battery.ACTION_RESET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESET -> {
                CoroutineScope(Dispatchers.Default).launch {
                    val tracker = AdvancedDrainTracker.getInstance(context)
                    tracker.resetSession()
                    DrainNotificationManager.getInstance(context).updateNow()
                }
            }
            Intent.ACTION_POWER_CONNECTED -> {
                val tracker = AdvancedDrainTracker.getInstance(context)
                tracker.onPowerConnected()
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val tracker = AdvancedDrainTracker.getInstance(context)
                tracker.onPowerDisconnected()
                DrainNotificationManager.getInstance(context).updateNow()
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                CoroutineScope(Dispatchers.Default).launch {
                    val tracker = AdvancedDrainTracker.getInstance(context)
                    val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
                    val resetOnReboot = prefs.getBoolean("auto_reset_reboot_enabled", true)
                    if (resetOnReboot) {
                        tracker.clearSavedSession()
                        tracker.resetSession()
                    } else {
                        tracker.restoreSession()
                    }
                    DrainNotificationManager.getInstance(context).updateNow()
                }
                if (NetSpeedSettings(context).isEnabled) {
                    SpeedMonitorService.start(context)
                }
            }
        }
    }
}
