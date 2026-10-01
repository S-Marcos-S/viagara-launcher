// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.notification

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.notification.AppNotificationItem
import dev.viagaralauncher.ui.common.AppIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A frosted-glass popup dialog with transparency and blur displaying the full notification content.
 * When a notification contains multiple messages (e.g. from MessagingStyle or InboxStyle),
 * they are presented as a clean vertical message list without duplicate headers or chips.
 */
@Composable
fun NotificationDetailDialog(
    item: AppNotificationItem,
    appInfo: AppInfo?,
    appName: String,
    onDismissRequest: () -> Unit,
    onOpen: (AppNotificationItem) -> Unit,
    onDismissNotification: (AppNotificationItem) -> Unit,
    allNotifications: List<AppNotificationItem> = emptyList(),
) {
    val allList = if (allNotifications.any { it.key == item.key }) allNotifications else (listOf(item) + allNotifications)

    // Deduplicate by title to ensure the same person/conversation is never duplicated
    val distinctList = allList
        .groupBy { it.title.trim().lowercase() }
        .map { (_, group) ->
            // Pick the notification with the most extracted messages, or the most recent
            group.maxByOrNull { it.messages.size } ?: group.first()
        }

    var currentKey by remember(item.key) { mutableStateOf(distinctList.firstOrNull { it.key == item.key }?.key ?: distinctList.firstOrNull()?.key ?: item.key) }
    val selectedItem = distinctList.find { it.key == currentKey } ?: distinctList.firstOrNull() ?: item

    val view = LocalView.current
    DisposableEffect(view) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dialogWindow != null) {
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val params = dialogWindow.attributes
            params.blurBehindRadius = 32
            dialogWindow.attributes = params
        }
        onDispose {}
    }

    val colorScheme = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Scrim background with transparency
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center,
        ) {
            val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(selectedItem.postTime))

            // Glassmorphism card
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {}, // absorb clicks inside dialog
                color = dynamicSurfaceColor(),
                border = BorderStroke(1.dp, dynamicBorderColor()),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Only show conversation switcher chips when there are 2 or more DISTINCT contacts/conversations
                    if (distinctList.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            distinctList.forEach { notif ->
                                val isSelected = notif.key == selectedItem.key
                                val countSuffix = if (notif.messages.size > 1) " (${notif.messages.size})" else ""
                                val chipTitle = (if (notif.title.isNotBlank()) notif.title else appName) + countSuffix

                                Surface(
                                    color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (isSelected) {
                                        BorderStroke(1.dp, colorScheme.primary)
                                    } else null,
                                    modifier = Modifier.clickable { currentKey = notif.key },
                                ) {
                                    Text(
                                        text = chipTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    // Header: App icon, Title (Contact name or App name), Subtitle and Timestamp
                    val hasPersonTitle = selectedItem.title.isNotBlank() && !selectedItem.title.equals(appName, ignoreCase = true)
                    val headerTitle = if (hasPersonTitle) selectedItem.title else appName
                    val headerSubtitle = if (hasPersonTitle) {
                        if (!selectedItem.subText.isNullOrBlank()) "${selectedItem.subText} • $appName" else appName
                    } else {
                        selectedItem.subText.orEmpty()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (appInfo != null) {
                            AppIcon(app = appInfo, sizeDp = 36)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (headerSubtitle.isNotBlank()) {
                                Text(
                                    text = headerSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Content area: Multiple messages in a list or single notification text
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (selectedItem.messages.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedItem.messages.forEach { msg ->
                                    Surface(
                                        color = colorScheme.surfaceContainer.copy(alpha = 0.70f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            if (!msg.sender.isNullOrBlank() && !msg.sender.equals(headerTitle, ignoreCase = true)) {
                                                Text(
                                                    text = msg.sender,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.primary,
                                                )
                                                Spacer(Modifier.height(2.dp))
                                            }
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colorScheme.onSurface,
                                                lineHeight = 20.sp,
                                            )
                                            if (msg.timestamp > 0L) {
                                                val msgTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = msgTime,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.align(Alignment.End),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (selectedItem.text.isNotBlank()) {
                                Text(
                                    text = selectedItem.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurface,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action buttons: Dismiss and Open
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectedItem.isClearable) {
                            TextButton(
                                onClick = { onDismissNotification(selectedItem) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(stringResource(R.string.notification_dismiss))
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        Button(
                            onClick = { onOpen(selectedItem) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.notification_open))
                        }
                    }
                }
            }
        }
    }
}
