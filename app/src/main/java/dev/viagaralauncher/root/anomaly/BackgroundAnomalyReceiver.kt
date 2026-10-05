// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.root.anomaly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import dev.viagaralauncher.R
import dev.viagaralauncher.root.AppRootInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BackgroundAnomalyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val packageName = intent.getStringExtra(BackgroundAnomalyNotificationManager.EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(BackgroundAnomalyNotificationManager.EXTRA_APP_NAME) ?: packageName
        val notifId = intent.getIntExtra(BackgroundAnomalyNotificationManager.EXTRA_NOTIFICATION_ID, -1)

        if (notifId != -1) {
            BackgroundAnomalyNotificationManager.getInstance(context).cancelNotification(notifId)
        }

        when (action) {
            BackgroundAnomalyNotificationManager.ACTION_FORCE_STOP -> {
                if (packageName.isBlank()) return
                CoroutineScope(Dispatchers.IO).launch {
                    val rootAvailable = AppRootInspector.isRootAvailable()
                    if (rootAvailable) {
                        AppRootInspector.runSuCommand("am force-stop $packageName")
                        showToast(context, context.getString(R.string.anomaly_toast_force_stopped, appName))
                    } else {
                        val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(detailsIntent)
                        showToast(context, context.getString(R.string.anomaly_toast_open_settings, appName))
                    }
                }
            }

            BackgroundAnomalyNotificationManager.ACTION_MUTE_APP -> {
                if (packageName.isBlank()) return
                BackgroundAnomalyWatcher.getInstance(context).mutePackage(packageName, 60 * 60 * 1000L)
                showToast(context, context.getString(R.string.anomaly_toast_muted, appName))
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                BackgroundAnomalyWatcher.getInstance(context).start()
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
