// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.root

import android.content.ComponentName
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.root.AppRootInspector
import dev.victorialauncher.root.RunningTasksSnapshot
import dev.victorialauncher.root.SystemPerformanceSnapshot
import dev.victorialauncher.root.SystemTaskInspector
import dev.victorialauncher.root.TaskProcessItem
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.theme.dynamicBorderColor
import dev.victorialauncher.ui.theme.dynamicSurfaceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private enum class TaskManagerTab {
    PROCESSES,
    PERFORMANCE,
}

/**
 * Full-screen, Windows-inspired Task Manager designed with Material 3 Expressive guidelines.
 * Features categorized process lists (Foreground, Background, System) with one-click app inspection,
 * and a deep Performance telemetry hub with live interactive charts (CPU, RAM, GPU, Network, Disk).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskManagerScreen(
    allApps: List<AppInfo>,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(TaskManagerTab.PROCESSES) }
    var isAutoRefreshEnabled by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    var performanceSnapshot by remember { mutableStateOf<SystemPerformanceSnapshot?>(null) }
    var runningTasksSnapshot by remember { mutableStateOf<RunningTasksSnapshot?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemProcesses by remember { mutableStateOf(false) }

    // Telemetry History for Charts (capped at last 30 samples)
    val cpuHistory = remember { mutableStateListOf<Float>() }
    val ramHistory = remember { mutableStateListOf<Float>() }
    val netRxHistory = remember { mutableStateListOf<Float>() }
    val netTxHistory = remember { mutableStateListOf<Float>() }

    // Detail dialog trigger for selecting an app
    var inspectingApp by remember { mutableStateOf<AppInfo?>(null) }

    fun refreshAll(showSpinner: Boolean = false) {
        scope.launch {
            if (showSpinner) isRefreshing = true
            val perf = SystemTaskInspector.getPerformanceSnapshot(context)
            val tasks = SystemTaskInspector.getRunningTasks(context, allApps)

            performanceSnapshot = perf
            runningTasksSnapshot = tasks

            // Update chart histories
            val cpuVal = perf.totalCpuPercent.toFloat().coerceIn(0f, 100f)
            val ramVal = perf.ramUsedPercent.toFloat().coerceIn(0f, 100f)
            val rxKb = (perf.rxSpeedBps / 1024f).coerceAtLeast(0f)
            val txKb = (perf.txSpeedBps / 1024f).coerceAtLeast(0f)

            if (cpuHistory.size >= 30) cpuHistory.removeAt(0)
            cpuHistory.add(cpuVal)

            if (ramHistory.size >= 30) ramHistory.removeAt(0)
            ramHistory.add(ramVal)

            if (netRxHistory.size >= 30) netRxHistory.removeAt(0)
            netRxHistory.add(rxKb)

            if (netTxHistory.size >= 30) netTxHistory.removeAt(0)
            netTxHistory.add(txKb)

            isRefreshing = false
        }
    }

    LaunchedEffect(isAutoRefreshEnabled) {
        while (isActive) {
            refreshAll(showSpinner = false)
            if (!isAutoRefreshEnabled) break
            delay(1500L)
        }
    }

    Scaffold(
        containerColor = dynamicSurfaceColor(),
        topBar = {
            TaskManagerTopBar(
                selectedTab = selectedTab,
                onTabSelect = { selectedTab = it },
                isAutoRefreshEnabled = isAutoRefreshEnabled,
                onToggleAutoRefresh = { isAutoRefreshEnabled = !isAutoRefreshEnabled },
                isRefreshing = isRefreshing,
                onManualRefresh = { refreshAll(showSpinner = true) },
                onNavigateBack = onNavigateBack,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (selectedTab) {
                TaskManagerTab.PROCESSES -> {
                    ProcessesScreen(
                        tasksSnapshot = runningTasksSnapshot,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        showSystemProcesses = showSystemProcesses,
                        onToggleSystemProcesses = { showSystemProcesses = !showSystemProcesses },
                        onInspectApp = { item ->
                            val targetApp = item.appInfo ?: AppInfo(
                                componentName = ComponentName(item.packageName, ""),
                                label = item.appName,
                            )
                            inspectingApp = targetApp
                        },
                        onKillProcess = { item ->
                            scope.launch {
                                val killed = AppRootInspector.killProcess(item.pid)
                                if (killed) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.root_inspector_toast_kill_success, item.pid),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    refreshAll(showSpinner = false)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.root_inspector_toast_kill_error),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        onForceStop = { item ->
                            scope.launch {
                                val stopped = AppRootInspector.forceStopApp(item.packageName)
                                if (stopped) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.root_inspector_toast_stopped),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    refreshAll(showSpinner = false)
                                }
                            }
                        },
                    )
                }

                TaskManagerTab.PERFORMANCE -> {
                    PerformanceScreen(
                        snapshot = performanceSnapshot,
                        cpuHistory = cpuHistory,
                        ramHistory = ramHistory,
                        netRxHistory = netRxHistory,
                        netTxHistory = netTxHistory,
                    )
                }
            }

            // Detail Inspector Dialog for chosen App
            inspectingApp?.let { app ->
                AppRootInspectorDialog(
                    app = app,
                    displayName = app.label,
                    onDismissRequest = { inspectingApp = null },
                )
            }
        }
    }
}

@Composable
private fun TaskManagerTopBar(
    selectedTab: TaskManagerTab,
    onTabSelect: (TaskManagerTab) -> Unit,
    isAutoRefreshEnabled: Boolean,
    onToggleAutoRefresh: () -> Unit,
    isRefreshing: Boolean,
    onManualRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    val infiniteTransition = rememberInfiniteTransition(label = "pulseLive")
    val liveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liveAlpha",
    )

    Surface(
        color = dynamicSurfaceColor().copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.task_manager_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )
                        if (isAutoRefreshEnabled) {
                            Spacer(Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = liveAlpha)),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.task_manager_live_badge),
                                    color = Color(0xFF10B981),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Windows Task Manager Engine • Root Enabled",
                        fontSize = 10.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }

                // Pause / Play Auto-refresh
                IconButton(
                    onClick = onToggleAutoRefresh,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isAutoRefreshEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Toggle auto-refresh",
                        tint = if (isAutoRefreshEnabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Manual Refresh
                IconButton(
                    onClick = onManualRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_reset),
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Material Expressive Pill Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExpressiveTabPill(
                    title = stringResource(R.string.task_manager_tab_processes),
                    icon = Icons.Filled.ViewList,
                    selected = selectedTab == TaskManagerTab.PROCESSES,
                    onClick = { onTabSelect(TaskManagerTab.PROCESSES) },
                    modifier = Modifier.weight(1f),
                )
                ExpressiveTabPill(
                    title = stringResource(R.string.task_manager_tab_performance),
                    icon = Icons.Filled.Speed,
                    selected = selectedTab == TaskManagerTab.PERFORMANCE,
                    onClick = { onTabSelect(TaskManagerTab.PERFORMANCE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExpressiveTabPill(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else Color.Transparent,
        label = "tabPillBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.7f),
        label = "tabPillContent",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ProcessesScreen(
    tasksSnapshot: RunningTasksSnapshot?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSystemProcesses: Boolean,
    onToggleSystemProcesses: () -> Unit,
    onInspectApp: (TaskProcessItem) -> Unit,
    onKillProcess: (TaskProcessItem) -> Unit,
    onForceStop: (TaskProcessItem) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    val query = searchQuery.trim().lowercase(Locale.ROOT)
    val filteredFg = tasksSnapshot?.foregroundApps?.filter {
        query.isEmpty() || it.appName.lowercase(Locale.ROOT).contains(query) || it.packageName.lowercase(Locale.ROOT).contains(query) || it.pid.toString().contains(query)
    } ?: emptyList()

    val filteredBg = tasksSnapshot?.backgroundApps?.filter {
        query.isEmpty() || it.appName.lowercase(Locale.ROOT).contains(query) || it.packageName.lowercase(Locale.ROOT).contains(query) || it.pid.toString().contains(query)
    } ?: emptyList()

    val filteredSys = tasksSnapshot?.systemProcesses?.filter {
        query.isEmpty() || it.processName.lowercase(Locale.ROOT).contains(query) || it.pid.toString().contains(query)
    } ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            // Expressive Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.task_manager_search_hint),
                        fontSize = 13.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear",
                                tint = colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.18f),
                    focusedContainerColor = colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                    unfocusedContainerColor = colorScheme.surfaceContainerHigh.copy(alpha = 0.2f),
                ),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            // Quick Stats Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProcessStatBadge(
                    label = "1º Plano",
                    count = filteredFg.size,
                    color = Color(0xFF10B981),
                )
                ProcessStatBadge(
                    label = "2º Plano",
                    count = filteredBg.size,
                    color = Color(0xFF3B82F6),
                )
                ProcessStatBadge(
                    label = "Sistema",
                    count = filteredSys.size,
                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        // 1. FOREGROUND APPS
        if (filteredFg.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.task_manager_group_foreground),
                    count = filteredFg.size,
                    indicatorColor = Color(0xFF10B981),
                )
            }
            items(filteredFg, key = { "fg_${it.pid}_${it.packageName}" }) { item ->
                ProcessRowCard(
                    item = item,
                    onClick = { onInspectApp(item) },
                    onKill = { onKillProcess(item) },
                    onForceStop = { onForceStop(item) },
                )
            }
        }

        // 2. BACKGROUND APPS
        if (filteredBg.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.task_manager_group_background),
                    count = filteredBg.size,
                    indicatorColor = Color(0xFF3B82F6),
                )
            }
            items(filteredBg, key = { "bg_${it.pid}_${it.packageName}" }) { item ->
                ProcessRowCard(
                    item = item,
                    onClick = { onInspectApp(item) },
                    onKill = { onKillProcess(item) },
                    onForceStop = { onForceStop(item) },
                )
            }
        }

        // 3. SYSTEM PROCESSES (Collapsible)
        item {
            CollapsibleSystemHeader(
                count = filteredSys.size,
                expanded = showSystemProcesses,
                onToggle = onToggleSystemProcesses,
            )
        }

        if (showSystemProcesses && filteredSys.isNotEmpty()) {
            items(filteredSys, key = { "sys_${it.pid}_${it.processName}" }) { item ->
                ProcessRowCard(
                    item = item,
                    onClick = { onInspectApp(item) },
                    onKill = { onKillProcess(item) },
                    onForceStop = { onForceStop(item) },
                    isSystem = true,
                )
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    indicatorColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(indicatorColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "($count)",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CollapsibleSystemHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.35f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.task_manager_group_system),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "($count)",
            fontSize = 11.sp,
            color = colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (expanded) "Ocultar" else "Mostrar",
            fontSize = 11.sp,
            color = colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProcessRowCard(
    item: TaskProcessItem,
    onClick: () -> Unit,
    onKill: () -> Unit,
    onForceStop: () -> Unit,
    isSystem: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon or Generic System Icon
            if (item.appInfo != null) {
                AppIcon(
                    app = item.appInfo,
                    sizeDp = 38,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSystem) colorScheme.outline.copy(alpha = 0.15f) else colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isSystem) Icons.Filled.Security else Icons.Filled.Memory,
                        contentDescription = null,
                        tint = if (isSystem) colorScheme.onSurface.copy(alpha = 0.6f) else colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Name, package & PID
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.isForeground) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.16f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "FOCO",
                                color = Color(0xFF10B981),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "PID: ${item.pid} • ${item.packageName}",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Resource Metrics: CPU% and RAM MB
            Column(horizontalAlignment = Alignment.End) {
                // CPU Metric
                val cpuColor = when {
                    item.cpuPercent > 20.0 -> Color(0xFFEF4444) // Red
                    item.cpuPercent > 5.0 -> Color(0xFFF59E0B) // Amber
                    else -> colorScheme.onSurface.copy(alpha = 0.75f)
                }
                Text(
                    text = "${String.format(Locale.US, "%.1f", item.cpuPercent)}% CPU",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = cpuColor,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(2.dp))
                // RAM Metric
                Text(
                    text = "${String.format(Locale.US, "%.1f", item.ramMb)} MB",
                    fontSize = 11.sp,
                    color = Color(0xFF6366F1),
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.width(6.dp))

            // Quick End Task Action Button
            IconButton(
                onClick = onKill,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.task_manager_end_task),
                    tint = colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ProcessStatBadge(
    label: String,
    count: Int,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label: ",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Text(
            text = "$count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun PerformanceScreen(
    snapshot: SystemPerformanceSnapshot?,
    cpuHistory: List<Float>,
    ramHistory: List<Float>,
    netRxHistory: List<Float>,
    netTxHistory: List<Float>,
) {
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            // 1. CPU RESOURCE CARD (Windows Task Manager style)
            val cpuUsage = snapshot?.totalCpuPercent ?: 0.0
            PerformanceResourceCard(
                title = "Processador (CPU)",
                icon = Icons.Filled.Speed,
                accentColor = Color(0xFF3B82F6), // Electric Blue
                headline = "${String.format(Locale.US, "%.1f", cpuUsage)}%",
                subHeadline = "Utilização total do processador",
                history = cpuHistory,
                chartColor = Color(0xFF3B82F6),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SpecItem(label = stringResource(R.string.task_manager_cpu_cores), value = "${snapshot?.cpuCores ?: 1} Núcleos")
                        SpecItem(label = stringResource(R.string.task_manager_cpu_speed), value = "${snapshot?.maxCpuFrequencyMhz ?: 0} MHz Max")
                        SpecItem(label = stringResource(R.string.task_manager_cpu_threads), value = "${snapshot?.totalProcesses ?: 0} / ${snapshot?.totalThreads ?: 0}")
                    }

                    val uptimeSec = (snapshot?.uptimeMillis ?: 0L) / 1000L
                    val hours = uptimeSec / 3600
                    val minutes = (uptimeSec % 3600) / 60
                    val seconds = uptimeSec % 60
                    val uptimeFormatted = "${hours}h ${minutes}m ${seconds}s"
                    SpecItem(label = stringResource(R.string.task_manager_cpu_uptime), value = uptimeFormatted)

                    // Individual Core Frequencies (Live)
                    val freqs = snapshot?.cpuFrequenciesMhz ?: emptyList()
                    if (freqs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Frequências por Núcleo:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            freqs.forEachIndexed { index, freq ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colorScheme.surfaceContainerHigh)
                                        .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "C$index: ${freq}MHz",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurface.copy(alpha = 0.8f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            // 2. RAM & SWAP/ZRAM RESOURCE CARD
            val ramUsed = snapshot?.ramUsedMb ?: 0.0
            val ramTotal = snapshot?.ramTotalMb ?: 0.0
            val ramPercent = snapshot?.ramUsedPercent ?: 0.0
            val ramUsedGb = ramUsed / 1024.0
            val ramTotalGb = ramTotal / 1024.0

            PerformanceResourceCard(
                title = "Memória (RAM)",
                icon = Icons.Filled.Memory,
                accentColor = Color(0xFF8B5CF6), // Violet
                headline = "${String.format(Locale.US, "%.1f", ramUsedGb)} GB / ${String.format(Locale.US, "%.1f", ramTotalGb)} GB",
                subHeadline = "${String.format(Locale.US, "%.1f", ramPercent)}% em uso",
                history = ramHistory,
                chartColor = Color(0xFF8B5CF6),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SpecItem(label = stringResource(R.string.task_manager_ram_used), value = "${String.format(Locale.US, "%.1f", ramUsed)} MB")
                    SpecItem(label = stringResource(R.string.task_manager_ram_available), value = "${String.format(Locale.US, "%.1f", snapshot?.ramAvailableMb ?: 0.0)} MB")
                    SpecItem(label = stringResource(R.string.task_manager_ram_zram), value = "${String.format(Locale.US, "%.1f", snapshot?.swapUsedMb ?: 0.0)} / ${String.format(Locale.US, "%.1f", snapshot?.swapTotalMb ?: 0.0)} MB")
                }
            }
        }

        item {
            // 3. NETWORK SPEED & INTERNET RESOURCE CARD
            val rxSpeed = snapshot?.rxSpeedBps ?: 0L
            val txSpeed = snapshot?.txSpeedBps ?: 0L

            PerformanceResourceCard(
                title = "Rede & Internet",
                icon = Icons.Filled.CloudDownload,
                accentColor = Color(0xFF06B6D4), // Cyan
                headline = "↓ ${formatSpeed(rxSpeed)}   ↑ ${formatSpeed(txSpeed)}",
                subHeadline = "Tipo de conexão: ${snapshot?.networkType ?: "Conectado"}",
                history = netRxHistory,
                chartColor = Color(0xFF06B6D4),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SpecItem(label = stringResource(R.string.task_manager_net_download), value = formatSpeed(rxSpeed))
                    SpecItem(label = stringResource(R.string.task_manager_net_upload), value = formatSpeed(txSpeed))
                    SpecItem(label = "Total Trafegado", value = formatBytes(snapshot?.totalRxBytes ?: 0L))
                }
            }
        }

        item {
            // 4. GPU / GRAPHICS ENGINE CARD
            val gpuPercent = snapshot?.gpuUsagePercent
            val gpuClock = snapshot?.gpuClockMhz
            val gpuModel = snapshot?.gpuModel ?: "Adreno / Mali Graphics"

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                color = colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Unidade Gráfica (GPU)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = gpuModel,
                                fontSize = 11.sp,
                                color = colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SpecItem(
                            label = "Carga de GPU",
                            value = if (gpuPercent != null) "${String.format(Locale.US, "%.1f", gpuPercent)}%" else "Ativo via Kernel",
                        )
                        SpecItem(
                            label = "Frequência Atual",
                            value = if (gpuClock != null) "${gpuClock} MHz" else "Dinâmica",
                        )
                        SpecItem(
                            label = "Aceleração",
                            value = "Hardware Vulkan",
                        )
                    }
                }
            }
        }

        item {
            // 5. STORAGE CARD
            val storageTotal = snapshot?.storageTotalGb ?: 0.0
            val storageUsed = snapshot?.storageUsedGb ?: 0.0
            val storageAvail = snapshot?.storageAvailableGb ?: 0.0
            val storagePercent = if (storageTotal > 0) (storageUsed / storageTotal).toFloat() else 0f

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                color = colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.task_manager_storage_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "Armazenamento Interno do Sistema",
                                fontSize = 11.sp,
                                color = colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { storagePercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFF59E0B),
                        trackColor = colorScheme.surfaceContainerHighest,
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SpecItem(label = "Usado", value = "${String.format(Locale.US, "%.1f", storageUsed)} GB")
                        SpecItem(label = "Livre", value = "${String.format(Locale.US, "%.1f", storageAvail)} GB")
                        SpecItem(label = "Capacidade Total", value = "${String.format(Locale.US, "%.1f", storageTotal)} GB")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PerformanceResourceCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    headline: String,
    subHeadline: String,
    history: List<Float>,
    chartColor: Color,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
        color = colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = subHeadline,
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                Text(
                    text = headline,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Interactive Expressive Telemetry Chart
            RealtimeTelemetryGraph(
                history = history,
                color = chartColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp),
            )

            Spacer(Modifier.height(14.dp))

            HorizontalDivider(
                color = colorScheme.outline.copy(alpha = 0.1f),
                thickness = 1.dp,
            )

            Spacer(Modifier.height(12.dp))

            // Specs / Sub-metrics slot
            content()
        }
    }
}

/**
 * High-performance, anti-aliased Canvas chart inspired by the Windows Task Manager graphs,
 * enhanced with smooth Bézier curves, a delicate vertical fade gradient, and subtle dashed grid lines.
 */
@Composable
private fun RealtimeTelemetryGraph(
    history: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val gridColor = colorScheme.outline.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.25f))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
            val width = size.width
            val height = size.height

            // 1. Draw horizontal grid lines (0%, 25%, 50%, 75%, 100%)
            val gridSteps = 4
            for (i in 0..gridSteps) {
                val y = height * (i.toFloat() / gridSteps)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                )
            }

            if (history.isEmpty()) return@Canvas

            // Compute min/max for normalization
            val maxVal = maxOf(history.maxOrNull() ?: 1f, 100f)
            val minVal = 0f

            val points = history.mapIndexed { index, value ->
                val x = if (history.size > 1) {
                    (index.toFloat() / (history.size - 1)) * width
                } else {
                    width
                }
                val normalized = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val y = height - (normalized * height)
                Offset(x, y)
            }

            // Path for gradient fill
            val fillPath = Path()
            // Path for top stroke
            val strokePath = Path()

            if (points.isNotEmpty()) {
                fillPath.moveTo(points.first().x, height)
                fillPath.lineTo(points.first().x, points.first().y)
                strokePath.moveTo(points.first().x, points.first().y)

                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cx = (prev.x + curr.x) / 2f
                    fillPath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    strokePath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }

                fillPath.lineTo(points.last().x, height)
                fillPath.close()

                // Draw Gradient Fill under the curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.35f),
                            color.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                )

                // Draw Outer Stroke Line
                drawPath(
                    path = strokePath,
                    color = color,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SpecItem(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB/s", bytesPerSec / (1024.0 * 1024.0 * 1024.0))
        bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
