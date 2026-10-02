// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            Intent.ACTION_BOOT_COMPLETED -> {
                CoroutineScope(Dispatchers.Default).launch {
                    val tracker = AdvancedDrainTracker.getInstance(context)
                    tracker.clearSavedSession()
                    tracker.resetSession()
                }
            }
        }
    }
}
