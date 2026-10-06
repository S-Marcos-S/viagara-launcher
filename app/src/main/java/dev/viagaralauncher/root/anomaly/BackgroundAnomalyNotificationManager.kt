// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.root.anomaly

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.viagaralauncher.MainActivity
import dev.viagaralauncher.R

class BackgroundAnomalyNotificationManager private constructor(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "channel_background_anomaly"
        const val ACTION_FORCE_STOP = "dev.viagaralauncher.action.ANOMALY_FORCE_STOP"
        const val ACTION_MUTE_APP = "dev.viagaralauncher.action.ANOMALY_MUTE_APP"
        const val ACTION_INSPECT_APP = "dev.viagaralauncher.action.ANOMALY_INSPECT_APP"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        @Volatile
        private var instance: BackgroundAnomalyNotificationManager? = null

        fun getInstance(context: Context): BackgroundAnomalyNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: BackgroundAnomalyNotificationManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.anomaly_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.anomaly_channel_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 150)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun postAnomalyNotification(anomaly: AnomalyEvent, appIcon: Drawable? = null) {
        val notificationId = 7000 + (anomaly.packageName.hashCode() and 0x7FFF)

        // PendingIntent: Inspecionar (Abre a MainActivity e dispara abertura no Gerenciador de Tarefas)
        val inspectIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_INSPECT_APP
            putExtra(EXTRA_PACKAGE_NAME, anomaly.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val inspectPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            inspectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // PendingIntent: Forçar Parada
        val forceStopIntent = Intent(context, BackgroundAnomalyReceiver::class.java).apply {
            action = ACTION_FORCE_STOP
            putExtra(EXTRA_PACKAGE_NAME, anomaly.packageName)
            putExtra(EXTRA_APP_NAME, anomaly.appName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val forceStopPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            forceStopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // PendingIntent: Silenciar por 1 hora
        val muteIntent = Intent(context, BackgroundAnomalyReceiver::class.java).apply {
            action = ACTION_MUTE_APP
            putExtra(EXTRA_PACKAGE_NAME, anomaly.packageName)
            putExtra(EXTRA_APP_NAME, anomaly.appName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when (anomaly.type) {
            AnomalyType.NETWORK -> context.getString(R.string.anomaly_notif_net_title, anomaly.appName)
            AnomalyType.CPU -> context.getString(R.string.anomaly_notif_cpu_title, anomaly.appName)
            AnomalyType.RAM -> context.getString(R.string.anomaly_notif_ram_title, anomaly.appName)
            AnomalyType.DEEP_SLEEP -> context.getString(R.string.anomaly_notif_deep_sleep_title, anomaly.appName)
        }

        val largeBitmap = appIcon?.let { drawableToBitmap(it) }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(anomaly.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(anomaly.description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(inspectPendingIntent)
            .addAction(
                0,
                context.getString(R.string.anomaly_action_force_stop),
                forceStopPendingIntent,
            )
            .addAction(
                0,
                context.getString(R.string.anomaly_action_inspect),
                inspectPendingIntent,
            )
            .addAction(
                0,
                context.getString(R.string.anomaly_action_mute_1h),
                mutePendingIntent,
            )

        if (largeBitmap != null) {
            builder.setLargeIcon(largeBitmap)
        }

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }
}
