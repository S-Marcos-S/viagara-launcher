// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.victorialauncher.R
import java.io.File

/**
 * Handles interactive notification actions for ongoing recordings,
 * saved logs, and real-time crash reports.
 */
class LogNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SAVE_CURRENT_LOG = "dev.victorialauncher.action.SAVE_CURRENT_LOG"
        const val ACTION_PAUSE_RESUME_CAPTURE = "dev.victorialauncher.action.PAUSE_RESUME_CAPTURE"
        const val ACTION_DISCARD_CURRENT_LOG = "dev.victorialauncher.action.DISCARD_CURRENT_LOG"
        const val ACTION_DELETE_SAVED_LOG = "dev.victorialauncher.action.DELETE_SAVED_LOG"
        const val ACTION_COPY_CRASH_STACKTRACE = "dev.victorialauncher.action.COPY_CRASH_STACKTRACE"

        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_CRASH_TEXT = "extra_crash_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_SAVE_CURRENT_LOG -> {
                AppLogCaptureService.saveLog(context)
            }
            ACTION_PAUSE_RESUME_CAPTURE -> {
                AppLogCaptureService.togglePauseResume(context)
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
            ACTION_COPY_CRASH_STACKTRACE -> {
                val crashText = intent.getStringExtra(EXTRA_CRASH_TEXT) ?: return
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Crash Stacktrace", crashText)
                cm?.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.log_toast_copied_trace), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
