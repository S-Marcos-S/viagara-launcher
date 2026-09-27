// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import dev.victorialauncher.notification.AppNotificationItem
import dev.victorialauncher.notification.NotificationBus
import dev.victorialauncher.notification.NotificationMessage

class NowPlayingListenerService : NotificationListenerService() {

    private lateinit var sessionManager: MediaSessionManager
    private lateinit var componentName: ComponentName

    private var watched: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = republish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = republish()
        override fun onSessionDestroyed() {
            detach()
            republish()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachTo(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        componentName = ComponentName(this, NowPlayingListenerService::class.java)
        NotificationBus.registerCancelCallback { key ->
            runCatching { cancelNotification(key) }
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            attachTo(sessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            NowPlayingBus.update(null)
        }
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBus.registerCancelCallback(null)
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        detach()
        NowPlayingBus.update(null)
        NotificationBus.updateNotifications(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
        refreshNotifications()
    }

    private fun refreshNotifications() {
        runCatching {
            val active = activeNotifications ?: return@runCatching

            // Identify packages that have non-summary notifications
            val packagesWithChildren = active.filter { sbn ->
                val notif = sbn.notification ?: return@filter false
                (notif.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }.map { it.packageName }.toSet()

            val list = active.mapNotNull { sbn ->
                val notification = sbn.notification ?: return@mapNotNull null
                val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

                // If this package already has non-summary notifications, ignore the redundant group summary
                if (isGroupSummary && sbn.packageName in packagesWithChildren) {
                    val hasOtherNotifs = active.any { other ->
                        other.packageName == sbn.packageName && other.key != sbn.key &&
                            (other.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
                    }
                    if (hasOtherNotifs) return@mapNotNull null
                }

                val extras = notification.extras ?: return@mapNotNull null
                val title = (extras.getCharSequence(Notification.EXTRA_TITLE)
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG))?.toString().orEmpty().trim()
                val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty().trim()
                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

                // If both title and text are blank, it's not useful to show
                if (title.isBlank() && text.isBlank()) return@mapNotNull null

                val messages = extractMessages(extras)

                AppNotificationItem(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = if (title.isNotBlank()) title else text,
                    text = if (title.isNotBlank()) text else "",
                    subText = subText,
                    postTime = sbn.postTime,
                    contentIntent = notification.contentIntent,
                    isClearable = sbn.isClearable,
                    messages = messages,
                )
            }
            NotificationBus.updateNotifications(list)
        }
    }

    private fun extractMessages(extras: Bundle): List<NotificationMessage> {
        val messages = mutableListOf<NotificationMessage>()

        // 1. Historic messages (if any)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val historic = runCatching {
                extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES)
            }.getOrNull()
            if (historic != null) {
                for (item in historic) {
                    if (item is Bundle) {
                        parseMessageBundle(item)?.let { messages.add(it) }
                    }
                }
            }
        }

        // 2. EXTRA_MESSAGES (MessagingStyle)
        val rawMessages = runCatching {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }.getOrNull()
        if (rawMessages != null && rawMessages.isNotEmpty()) {
            for (item in rawMessages) {
                if (item is Bundle) {
                    parseMessageBundle(item)?.let { messages.add(it) }
                }
            }
        }

        // 3. EXTRA_TEXT_LINES (InboxStyle)
        if (messages.isEmpty()) {
            val lines = runCatching {
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            }.getOrNull()
            if (lines != null && lines.isNotEmpty()) {
                for (line in lines) {
                    val str = line?.toString()?.trim().orEmpty()
                    if (str.isNotBlank()) {
                        val colonIdx = str.indexOf(':')
                        if (colonIdx in 1..30) {
                            val sender = str.substring(0, colonIdx).trim()
                            val msgText = str.substring(colonIdx + 1).trim()
                            messages.add(NotificationMessage(sender = sender, text = msgText))
                        } else {
                            messages.add(NotificationMessage(text = str))
                        }
                    }
                }
            }
        }

        // 4. Multi-line big text
        if (messages.isEmpty()) {
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            if (bigText.contains('\n')) {
                val lines = bigText.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                if (lines.size > 1) {
                    for (str in lines) {
                        val colonIdx = str.indexOf(':')
                        if (colonIdx in 1..30) {
                            val sender = str.substring(0, colonIdx).trim()
                            val msgText = str.substring(colonIdx + 1).trim()
                            messages.add(NotificationMessage(sender = sender, text = msgText))
                        } else {
                            messages.add(NotificationMessage(text = str))
                        }
                    }
                }
            }
        }

        return messages
    }

    private fun parseMessageBundle(bundle: Bundle): NotificationMessage? {
        val text = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
        if (text.isBlank()) return null
        val time = bundle.getLong("time", 0L)
        val sender = bundle.getCharSequence("sender")?.toString()?.trim()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    bundle.getParcelable<android.app.Person>("sender_person")?.name?.toString()
                }.getOrNull()
            } else null
        return NotificationMessage(sender = sender, text = text, timestamp = time)
    }

    private fun refresh() {
        if (::sessionManager.isInitialized) {
            runCatching { attachTo(sessionManager.getActiveSessions(componentName)) }
        }
    }

    /** Follow whichever session is worth showing, and watch it for changes. */
    private fun attachTo(controllers: List<MediaController>?) {
        val next = controllers?.firstOrNull { isLive(it.playbackState) }
            ?: controllers?.firstOrNull { it.playbackState != null }
            ?: controllers?.firstOrNull()

        if (next?.sessionToken != watched?.sessionToken) {
            detach()
            watched = next
            next?.registerCallback(controllerCallback)
        }
        republish()
    }

    private fun detach() {
        watched?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        watched = null
    }

    private fun republish() {
        val controller = watched
        val state = controller?.playbackState
        if (controller == null || !isLive(state)) {
            NowPlayingBus.update(null)
            return
        }

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        if (title.isBlank() && artist.isBlank()) {
            NowPlayingBus.update(null)
            return
        }

        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val positionMs = state?.position ?: 0L
        val speed = state?.playbackSpeed ?: 1f
        val lastUpdateTime = state?.lastPositionUpdateTime?.takeIf { it > 0L } ?: SystemClock.elapsedRealtime()

        NowPlayingBus.update(
            NowPlaying(
                title = title,
                artist = artist,
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                controller = controller,
                durationMs = durationMs,
                positionMs = positionMs,
                speed = speed,
                lastUpdateTime = lastUpdateTime,
            )
        )
    }

    /** Playing, paused or buffering counts; stopped, errored or idle does not. */
    private fun isLive(state: PlaybackState?): Boolean = when (state?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_BUFFERING,
        -> true

        else -> false
    }
}