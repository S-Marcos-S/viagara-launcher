// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.viagaralauncher.network.NetSpeedSettings
import dev.viagaralauncher.network.SpeedMonitorService

/**
 * BroadcastReceiver triggered by Android OS when this application is updated/replaced.
 * Automatically cleans up any downloaded update APK files from Downloads and MediaStore.
 */
class MyPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpdateManager.cleanupDownloadedApk(context, force = true)
            if (NetSpeedSettings(context).isEnabled) {
                SpeedMonitorService.start(context)
            }
        }
    }
}
