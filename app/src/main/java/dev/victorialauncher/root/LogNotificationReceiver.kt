// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.victorialauncher.R
import java.io.File

/**
 * Handles actions from the log recording and saved log notifications.
 */
class LogNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SAVE_CURRENT_LOG = "dev.victorialauncher.action.SAVE_CURRENT_LOG"
        const val ACTION_DISCARD_CURRENT_LOG = "dev.victorialauncher.action.DISCARD_CURRENT_LOG"
        const val ACTION_DELETE_SAVED_LOG = "dev.victorialauncher.action.DELETE_SAVED_LOG"

        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_SAVE_CURRENT_LOG -> {
                AppLogCaptureService.saveLog(context)
            }
            ACTION_DISCARD_CURRENT_LOG -> {
                AppLogCaptureService.cancelCapture(context)
            }
            ACTION_DELETE_SAVED_LOG -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

                if (!filePath.isNullOrBlank()) {
                    try {
                        val file = File(filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (_: Throwable) {}
                }

                if (notificationId != -1) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(notificationId)
                }

                Toast.makeText(context, context.getString(R.string.log_toast_deleted), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
