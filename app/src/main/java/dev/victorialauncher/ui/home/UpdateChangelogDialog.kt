// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.victorialauncher.ui.theme.dynamicBorderColor
import dev.victorialauncher.ui.theme.dynamicSurfaceColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.victorialauncher.R
import dev.victorialauncher.ui.common.ChangelogMarkdownViewer
import dev.victorialauncher.update.DownloadStatus
import dev.victorialauncher.update.RootInstaller
import dev.victorialauncher.update.UpdateInfo
import dev.victorialauncher.update.UpdateManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateChangelogDialog(
    update: UpdateInfo,
    onDismissRequest: () -> Unit,
    onDownload: ((useRoot: Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val downloadStatus by UpdateManager.downloadStatus.collectAsState()
    val isRootAvailable = remember { RootInstaller.isRootAvailable() }
    var useRootInstall by remember { mutableStateOf(isRootAvailable) }
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
            // Balão flutuante translúcido (frosted glass) com blur e transparência
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(24.dp),
                color = dynamicSurfaceColor(),
                border = BorderStroke(1.dp, dynamicBorderColor()),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                ) {
                    // Cabeçalho: Ícone + Título + Badge de Commit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NewReleases,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.update_dialog_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                            )
                            val versionSubtitle = if (!update.commitSha.isNullOrBlank()) {
                                "${update.displayVersion} (${update.commitSha.take(7)})"
                            } else {
                                update.displayVersion
                            }
                            Text(
                                text = versionSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        if (update.apkSize > 0) {
                            val sizeMb = update.apkSize / (1024f * 1024f)
                            Text(
                                text = String.format(Locale.getDefault(), "%.2f MB", sizeMb),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (update.publishedAtMs > 0) {
                        Spacer(Modifier.height(8.dp))
                        val dateFormatted = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
                            .format(Date(update.publishedAtMs))
                        Text(
                            text = stringResource(R.string.update_compiled_at, dateFormatted),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Caixa de texto com rolagem contendo as mudanças da versão
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorScheme.surfaceContainer.copy(alpha = 0.65f))
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ChangelogMarkdownViewer(
                            markdown = update.changelog,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (isRootAvailable) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colorScheme.surfaceContainer.copy(alpha = 0.5f))
                                .clickable { useRootInstall = !useRootInstall }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.update_root_install_option),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.update_root_install_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = useRootInstall,
                                onCheckedChange = { useRootInstall = it },
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Botões de ação
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onDismissRequest,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Text(stringResource(R.string.action_close))
                        }

                        Spacer(Modifier.width(8.dp))

                        when (val status = downloadStatus) {
                            is DownloadStatus.Downloading -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    colors = ButtonDefaults.buttonColors(
                                        disabledContainerColor = colorScheme.primary.copy(alpha = 0.6f),
                                        disabledContentColor = colorScheme.onPrimary,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(stringResource(R.string.update_downloading_progress, status.progressPercent))
                                }
                            }
                            is DownloadStatus.InstallingRoot -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    colors = ButtonDefaults.buttonColors(
                                        disabledContainerColor = colorScheme.primary.copy(alpha = 0.6f),
                                        disabledContentColor = colorScheme.onPrimary,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.update_status_installing_root))
                                }
                            }
                            is DownloadStatus.Finished -> {
                                Button(
                                    onClick = {
                                        status.fileUri?.let { uri ->
                                            UpdateManager.installUpdate(context, uri, useRoot = useRootInstall)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val label = if (useRootInstall) {
                                        stringResource(R.string.update_action_install_root)
                                    } else {
                                        stringResource(R.string.update_action_install)
                                    }
                                    Text(label)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        if (onDownload != null) {
                                            onDownload(useRootInstall)
                                        } else {
                                            UpdateManager.startDownload(context, update, autoInstallWithRoot = useRootInstall)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_download))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
