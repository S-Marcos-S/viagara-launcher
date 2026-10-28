// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.common

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.viagaralauncher.R
import dev.viagaralauncher.data.Folder
import dev.viagaralauncher.ui.theme.glass.GlassDialogContainer

/**
 * Pick an existing folder for an app, or name a new one. Used from both the favorites menu
 * and the A-Z list, rendered inside a glassmorphic floating window.
 */
@Composable
fun FolderPickerDialog(
    appLabel: String,
    folders: List<Folder>,
    onPickFolder: (Folder) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(folders.isEmpty()) }
    var newName by remember { mutableStateOf("") }
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
        onDismissRequest = onDismiss,
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
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlassDialogContainer(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
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
                        .padding(24.dp),
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.folder_picker_title, appLabel),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (!creating) {
                        folders.forEach { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickFolder(folder) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(folder.name, style = MaterialTheme.typography.bodyLarge, color = colorScheme.onSurface)
                                    Text(
                                        stringResource(R.string.folder_picker_app_count, folder.apps.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { creating = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.folder_picker_new),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.primary,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.home_folder_name_label)) },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outlineVariant,
                                focusedLabelColor = colorScheme.primary,
                                unfocusedLabelColor = colorScheme.onSurfaceVariant,
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface,
                                cursorColor = colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.folder_picker_new_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        if (creating) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { if (newName.isNotBlank()) onCreateFolder(newName.trim()) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.action_create))
                            }
                        }
                    }
                }
            }
        }
    }
}
