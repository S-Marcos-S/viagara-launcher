// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.root

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.root.AppRootInspector
import dev.viagaralauncher.root.anomaly.AnomalyEvent
import dev.viagaralauncher.root.anomaly.AnomalyHistoryRepository
import dev.viagaralauncher.root.anomaly.AnomalyType
import dev.viagaralauncher.root.anomaly.BackgroundAnomalyWatcher
import dev.viagaralauncher.root.anomaly.FlaggedAppSummary
import dev.viagaralauncher.ui.common.AppIcon
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AnomalyFilter {
    ALL,
    DEEP_SLEEP,
    NETWORK,
    CPU_RAM,
}

@Composable
fun BackgroundWatcherScreen(
    allApps: List<AppInfo>,
    onInspectApp: (AppInfo) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val repository = remember { AnomalyHistoryRepository.getInstance(context) }
    val watcher = remember { BackgroundAnomalyWatcher.getInstance(context) }

    val flaggedApps by repository.flaggedApps.collectAsState()
    val events by repository.events.collectAsState()
    val config by watcher.config.collectAsState()

    var selectedFilter by remember { mutableStateOf(AnomalyFilter.ALL) }
    var showRecentLog by remember { mutableStateOf(false) }

    // Summary statistics
    val totalDisruptions = remember(flaggedApps) { flaggedApps.sumOf { it.deepSleepDisruptions } }
    val totalBackgroundBytes = remember(flaggedApps) { flaggedApps.sumOf { it.totalEstimatedBytes } }

    val filteredApps = remember(flaggedApps, selectedFilter) {
        when (selectedFilter) {
            AnomalyFilter.ALL -> flaggedApps
            AnomalyFilter.DEEP_SLEEP -> flaggedApps.filter { it.deepSleepDisruptions > 0 }
            AnomalyFilter.NETWORK -> flaggedApps.filter { it.backgroundDataFlags > 0 }
            AnomalyFilter.CPU_RAM -> flaggedApps.filter { it.cpuFlags > 0 || it.ramFlags > 0 }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
    ) {
        // High Density Summary Row
        Surface(
            color = dynamicSurfaceColor(alpha = 0.55f, tintFraction = 0.12f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Apps Flagged
                SummaryPill(
                    icon = Icons.Filled.Warning,
                    label = stringResource(R.string.background_total_flagged),
                    value = "${flaggedApps.size}",
                    color = colorScheme.error,
                )

                // Sleep Disruptions
                SummaryPill(
                    icon = Icons.Filled.Bedtime,
                    label = stringResource(R.string.background_total_sleep_loss),
                    value = "$totalDisruptions",
                    color = colorScheme.primary,
                )

                // Screen-off Data
                SummaryPill(
                    icon = Icons.Filled.CloudDownload,
                    label = stringResource(R.string.background_total_data),
                    value = AppRootInspector.formatBytes(totalBackgroundBytes),
                    color = colorScheme.secondary,
                )
            }
        }

        // Filter Chips Row & Clear Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    CompactFilterChip(
                        selected = selectedFilter == AnomalyFilter.ALL,
                        label = stringResource(R.string.background_filter_all),
                        onClick = { selectedFilter = AnomalyFilter.ALL },
                    )
                }
                item {
                    CompactFilterChip(
                        selected = selectedFilter == AnomalyFilter.DEEP_SLEEP,
                        label = "🌙 ${stringResource(R.string.background_filter_deep_sleep)}",
                        onClick = { selectedFilter = AnomalyFilter.DEEP_SLEEP },
                    )
                }
                item {
                    CompactFilterChip(
                        selected = selectedFilter == AnomalyFilter.NETWORK,
                        label = "🌐 ${stringResource(R.string.background_filter_network)}",
                        onClick = { selectedFilter = AnomalyFilter.NETWORK },
                    )
                }
                item {
                    CompactFilterChip(
                        selected = selectedFilter == AnomalyFilter.CPU_RAM,
                        label = "⚡ ${stringResource(R.string.background_filter_cpu_ram)}",
                        onClick = { selectedFilter = AnomalyFilter.CPU_RAM },
                    )
                }
            }

            if (flaggedApps.isNotEmpty()) {
                IconButton(
                    onClick = { repository.clearAll() },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ClearAll,
                        contentDescription = "Limpar histórico",
                        tint = colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Main List of Flagged Apps
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.background_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.background_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.outline,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredApps, key = { it.packageName }) { appSummary ->
                    val matchedApp = allApps.firstOrNull { it.packageName == appSummary.packageName }

                    FlaggedAppCard(
                        summary = appSummary,
                        appInfo = matchedApp,
                        onInspect = {
                            val target = matchedApp ?: AppInfo(
                                componentName = android.content.ComponentName(appSummary.packageName, ""),
                                label = appSummary.appName,
                            )
                            onInspectApp(target)
                        },
                        onForceStop = {
                            scope.launch {
                                val root = AppRootInspector.isRootAvailable()
                                if (root) {
                                    AppRootInspector.runSuCommand("am force-stop ${appSummary.packageName}")
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.anomaly_toast_force_stopped, appSummary.appName),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", appSummary.packageName, null)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        },
                        onMute = {
                            watcher.mutePackage(appSummary.packageName, 60 * 60 * 1000L)
                            Toast.makeText(
                                context,
                                context.getString(R.string.anomaly_toast_muted, appSummary.appName),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onDismiss = {
                            repository.removeForPackage(appSummary.packageName)
                        },
                    )
                }

                item {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun CompactFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (selected) colorScheme.primary else dynamicSurfaceColor(alpha = 0.40f, tintFraction = 0.10f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) colorScheme.primary else colorScheme.outline.copy(alpha = 0.15f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun FlaggedAppCard(
    summary: FlaggedAppSummary,
    appInfo: AppInfo?,
    onInspect: () -> Unit,
    onForceStop: () -> Unit,
    onMute: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val lastDateFormatted = remember(summary.lastFlaggedTimestamp) {
        if (summary.lastFlaggedTimestamp > 0) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(summary.lastFlaggedTimestamp))
        } else ""
    }

    Surface(
        color = dynamicSurfaceColor(alpha = 0.52f, tintFraction = 0.14f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            // App info row with Icon, Name, and Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (appInfo != null) {
                    AppIcon(
                        app = appInfo,
                        sizeDp = 36,
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    Surface(
                        color = colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = summary.appName.take(1).uppercase(Locale.getDefault()),
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = summary.appName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (lastDateFormatted.isNotBlank()) {
                            Text(
                                text = "Último: $lastDateFormatted",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = colorScheme.outline,
                            )
                        }
                    }

                    Text(
                        text = summary.packageName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Violation Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (summary.deepSleepDisruptions > 0) {
                    InfractionBadge(
                        emoji = "🌙",
                        text = "${summary.deepSleepDisruptions}x sono profundo",
                        color = colorScheme.primary,
                    )
                }
                if (summary.backgroundDataFlags > 0) {
                    InfractionBadge(
                        emoji = "🌐",
                        text = AppRootInspector.formatBytes(summary.totalEstimatedBytes),
                        color = colorScheme.secondary,
                    )
                }
                if (summary.cpuFlags > 0) {
                    InfractionBadge(
                        emoji = "⚡",
                        text = "${summary.cpuFlags}x CPU",
                        color = colorScheme.error,
                    )
                }
            }

            // Detected Service description if available
            if (!summary.lastServiceOrProcess.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.background_service_label, summary.lastServiceOrProcess),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Quick Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onInspect,
                    modifier = Modifier.height(30.dp),
                ) {
                    Text(
                        text = stringResource(R.string.background_btn_inspect),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.width(4.dp))

                TextButton(
                    onClick = onMute,
                    modifier = Modifier.height(30.dp),
                ) {
                    Text(
                        text = stringResource(R.string.background_btn_mute),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.outline,
                    )
                }

                Spacer(Modifier.width(4.dp))

                Surface(
                    onClick = onForceStop,
                    color = colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.StopCircle,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.background_btn_stop),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfractionBadge(
    emoji: String,
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, fontSize = 10.sp)
            Spacer(Modifier.width(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}
