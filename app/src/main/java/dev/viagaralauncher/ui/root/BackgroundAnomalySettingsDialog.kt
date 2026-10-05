// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.root

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.viagaralauncher.R
import dev.viagaralauncher.root.anomaly.AnomalyEvent
import dev.viagaralauncher.root.anomaly.AnomalySensitivity
import dev.viagaralauncher.root.anomaly.AnomalyType
import dev.viagaralauncher.root.anomaly.BackgroundAnomalyWatcher
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackgroundAnomalySettingsDialog(
    onDismissRequest: () -> Unit,
    onInspectPackage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val watcher = BackgroundAnomalyWatcher.getInstance(context)
    val config by watcher.config.collectAsState()
    val anomalies by watcher.anomalies.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, dynamicBorderColor(alpha = 0.25f), RoundedCornerShape(28.dp)),
            color = dynamicSurfaceColor(alpha = 0.94f, tintFraction = 0.12f),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.anomaly_dialog_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.anomaly_dialog_subtitle),
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Master Toggle
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.anomaly_enable_watcher),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = if (config.isEnabled) "Ativo • Monitoramento adaptativo ligado" else "Desativado",
                                fontSize = 12.sp,
                                color = if (config.isEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = config.isEnabled,
                            onCheckedChange = { isChecked ->
                                watcher.updateConfig(config.copy(isEnabled = isChecked))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorScheme.onPrimary,
                                checkedTrackColor = colorScheme.primary,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                AnimatedVisibility(visible = config.isEnabled) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Info Note on Battery Optimization
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 1.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Impacto zero no Deep Sleep: enquanto a tela estiver apagada, o processador repousa 100% sem alarmes repetitivos. Anomalias são checadas via fotografias diferenciais de kernel ao religar a tela.",
                                    fontSize = 11.5.sp,
                                    color = colorScheme.onSurface,
                                    lineHeight = 16.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Resource options checkboxes
                        Text(
                            text = "Categorias de Monitoramento",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))

                        ResourceOptionRow(
                            label = stringResource(R.string.anomaly_opt_network),
                            icon = "🌐",
                            checked = config.notifyNetwork,
                            onCheckedChange = { watcher.updateConfig(config.copy(notifyNetwork = it)) },
                        )

                        ResourceOptionRow(
                            label = stringResource(R.string.anomaly_opt_cpu),
                            icon = "⚡",
                            checked = config.notifyCpu,
                            onCheckedChange = { watcher.updateConfig(config.copy(notifyCpu = it)) },
                        )

                        ResourceOptionRow(
                            label = stringResource(R.string.anomaly_opt_ram),
                            icon = "💾",
                            checked = config.notifyRam,
                            onCheckedChange = { watcher.updateConfig(config.copy(notifyRam = it)) },
                        )

                        Spacer(Modifier.height(14.dp))

                        // Sensitivity Selector
                        Text(
                            text = stringResource(R.string.anomaly_sensitivity_label),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnomalySensitivity.entries.forEach { sens ->
                                val selected = config.sensitivity == sens
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            watcher.updateConfig(config.copy(sensitivity = sens))
                                        },
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = when (sens) {
                                                AnomalySensitivity.HIGH -> "Alta"
                                                AnomalySensitivity.BALANCED -> "Equilibrada"
                                                AnomalySensitivity.LOW -> "Baixa"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Recent Alerts Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.anomaly_recent_alerts),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                    )
                    if (anomalies.isNotEmpty()) {
                        IconButton(
                            onClick = { watcher.clearAnomalies() },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Limpar histórico",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (anomalies.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.anomaly_no_recent_alerts),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(anomalies, key = { it.id }) { anomaly ->
                            AnomalyItemCard(
                                anomaly = anomaly,
                                onClick = {
                                    onInspectPackage(anomaly.packageName)
                                    onDismissRequest()
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = stringResource(R.string.action_close),
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceOptionRow(
    label: String,
    icon: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colorScheme.primary),
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = icon, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = colorScheme.onSurface,
        )
    }
}

@Composable
private fun AnomalyItemCard(
    anomaly: AnomalyEvent,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(anomaly.timestamp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = anomaly.type.iconEmoji, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = anomaly.appName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = anomaly.valueFormatted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.error,
                    )
                }
                Text(
                    text = if (anomaly.screenWasOff) "Tela Apagada • $timeStr" else "Tela Ligada • $timeStr",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
