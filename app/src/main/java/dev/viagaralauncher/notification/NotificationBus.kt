// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.notification

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Immutable
import dev.viagaralauncher.data.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Immutable
data class NotificationMessage(
    val sender: String? = null,
    val text: String,
    val timestamp: Long = 0L,
)

/**
 * Clean snapshot of an active system notification for a specific app package.
 */
@Immutable
data class AppNotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val postTime: Long = System.currentTimeMillis(),
    val contentIntent: PendingIntent? = null,
    val isClearable: Boolean = true,
    val messages: List<NotificationMessage> = emptyList(),
)

/**
 * Central event bus bridging the NotificationListenerService with the Compose UI layers.
 */
object NotificationBus {
    private val _notifications = MutableStateFlow<Map<String, List<AppNotificationItem>>>(emptyMap())
    val notifications: StateFlow<Map<String, List<AppNotificationItem>>> = _notifications

    private var cancelCallback: ((String) -> Unit)? = null

    fun registerCancelCallback(callback: ((String) -> Unit)?) {
        cancelCallback = callback
    }

    fun updateNotifications(items: List<AppNotificationItem>) {
        val grouped = items
            .sortedByDescending { it.postTime }
            .groupBy { it.packageName }
        _notifications.value = grouped
    }

    fun dismissNotification(key: String) {
        cancelCallback?.invoke(key)
    }

    fun launchNotification(
        context: Context,
        item: AppNotificationItem,
        appInfo: AppInfo?,
        onLaunchFallback: ((AppInfo) -> Unit)? = null,
    ) {
        var launched = false
        if (item.contentIntent != null) {
            try {
                val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }.toBundle()
                } else {
                    null
                }
                val fillIn = Intent().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                item.contentIntent.send(context, 0, fillIn, null, null, null, options)
                launched = true
            } catch (_: Throwable) {
                try {
                    item.contentIntent.send()
                    launched = true
                } catch (_: Throwable) {
                }
            }
        }

        if (!launched && appInfo != null) {
            if (onLaunchFallback != null) {
                onLaunchFallback(appInfo)
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
