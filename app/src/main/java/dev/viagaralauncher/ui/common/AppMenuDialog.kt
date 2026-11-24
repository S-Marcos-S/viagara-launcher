// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.common

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import dev.viagaralauncher.ui.theme.glass.GlassDialogContainer
import dev.viagaralauncher.util.AppUninstallManager

data class AppMenuItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false,
)

/**
 * Builds the standard contextual menu items for an app in the app list and search results.
 */
@Composable
fun appListMenuItems(
    app: AppInfo,
    isFavorite: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onAppInfo: () -> Unit,
    onMoveToFolder: () -> Unit,
    onHideApp: () -> Unit,
    showHideOption: Boolean = true,
): List<AppMenuItem> {
    val context = LocalContext.current
    return buildList {
        add(
            AppMenuItem(
                title = stringResource(if (isFavorite) R.string.applist_remove_favorite else R.string.applist_add_favorite),
                icon = if (isFavorite) Icons.Filled.StarBorder else Icons.Filled.Star,
                onClick = { onSetFavorite(!isFavorite) },
            )
        )
        add(
            AppMenuItem(
                title = stringResource(R.string.action_edit_icon_and_name),
                icon = Icons.Filled.Tune,
                onClick = onEdit,
            )
        )
        add(
            AppMenuItem(
                title = stringResource(R.string.action_app_info),
                icon = Icons.Filled.Info,
                onClick = onAppInfo,
            )
        )
        add(
            AppMenuItem(
                title = stringResource(R.string.action_move_to_folder),
                icon = Icons.Filled.Folder,
                onClick = onMoveToFolder,
            )
        )
        if (showHideOption) {
            add(
                AppMenuItem(
                    title = stringResource(R.string.applist_hide),
                    icon = Icons.Filled.VisibilityOff,
                    onClick = onHideApp,
                    isDestructive = true,
                )
            )
        }
        if (app.packageName != context.packageName) {
            add(
                AppMenuItem(
                    title = stringResource(R.string.action_uninstall),
                    icon = Icons.Filled.DeleteForever,
                    onClick = {
                        AppUninstallManager.uninstallStandard(context, app.packageName)
                    },
                    isDestructive = true,
                )
            )
        }
    }
}

/**
 * A modern floating popup menu for an individual app with rounded corners and frosted glass blur.
 */
@Composable
fun AppMenuDialog(
    app: AppInfo,
    displayName: String,
    onDismissRequest: () -> Unit,
    items: List<AppMenuItem>,
) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlassDialogContainer(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                cornerRadiusDp = 28.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    // App Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app = app, sizeDp = 42)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.primary.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = colorScheme.primary.copy(alpha = 0.20f))
                    Spacer(Modifier.height(10.dp))

                    // Menu action items
                    items.forEach { item ->
                        val itemColor = if (item.isDestructive) colorScheme.error else colorScheme.onSurface
                        val iconColor = if (item.isDestructive) colorScheme.error else colorScheme.primary
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onDismissRequest()
                                    item.onClick()
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (item.isDestructive) colorScheme.errorContainer.copy(alpha = 0.35f)
                                        else colorScheme.primary.copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = item.title,
                                color = itemColor,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
