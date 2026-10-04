// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.root

import android.content.ComponentName
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.root.AppRootInspector
import dev.viagaralauncher.root.PowerImpactLevel
import dev.viagaralauncher.root.RunningTasksSnapshot
import dev.viagaralauncher.root.SystemPerformanceSnapshot
import dev.viagaralauncher.root.SystemTaskInspector
import dev.viagaralauncher.root.TaskProcessItem
import dev.viagaralauncher.ui.common.AppIcon
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
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
    onNavigateToNetworkStats: () -> Unit = {},
    onNavigateToBatteryStats: () -> Unit = {},
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val tabs = remember { TaskManagerTab.entries }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tabs.size },
    )
    val focusManager = LocalFocusManager.current

    LaunchedEffect(pagerState.currentPage) {
        focusManager.clearFocus()
    }

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
    val batteryDischargeHistory = remember { mutableStateListOf<Float>() }

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
            val battMa = kotlin.math.abs(perf.batteryCurrentNowMa ?: 0L).toFloat()

            if (cpuHistory.size >= 30) cpuHistory.removeAt(0)
            cpuHistory.add(cpuVal)

            if (ramHistory.size >= 30) ramHistory.removeAt(0)
            ramHistory.add(ramVal)

            if (netRxHistory.size >= 30) netRxHistory.removeAt(0)
            netRxHistory.add(rxKb)

            if (netTxHistory.size >= 30) netTxHistory.removeAt(0)
            netTxHistory.add(txKb)

            if (batteryDischargeHistory.size >= 30) batteryDischargeHistory.removeAt(0)
            batteryDischargeHistory.add(battMa)

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

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null) {
            val hadBlurFlag = (window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
            val prevRadius = window.attributes.blurBehindRadius
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val params = window.attributes
            params.blurBehindRadius = 45
            window.attributes = params

            onDispose {
                val p = window.attributes
                p.blurBehindRadius = prevRadius
                window.attributes = p
                if (!hadBlurFlag) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        } else {
            onDispose {}
        }
    }

    Scaffold(
        containerColor = dynamicSurfaceColor(alpha = 0.40f, tintFraction = 0.16f),
        topBar = {
            CleanTaskManagerTopBar(
                selectedTab = tabs.getOrElse(pagerState.currentPage) { TaskManagerTab.PROCESSES },
                onTabSelect = { targetTab ->
                    scope.launch {
                        pagerState.animateScrollToPage(targetTab.ordinal)
                    }
                },
                onNavigateBack = onNavigateBack,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { pageIndex -> tabs[pageIndex].name },
            ) { pageIndex ->
                when (tabs[pageIndex]) {
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
                            batteryHistory = batteryDischargeHistory,
                            onNavigateToNetworkStats = onNavigateToNetworkStats,
                            onNavigateToBatteryStats = onNavigateToBatteryStats,
                        )
                    }
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
private fun CleanTaskManagerTopBar(
    selectedTab: TaskManagerTab,
    onTabSelect: (TaskManagerTab) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        color = dynamicSurfaceColor(alpha = 0.65f, tintFraction = 0.18f),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
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

            Spacer(Modifier.width(8.dp))

            // Cabeçalho com os nomes Processos e Desempenho
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(3.dp),
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
            .border(1.dp, dynamicBorderColor(alpha = 0.25f, tintFraction = 0.30f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = dynamicSurfaceColor(alpha = 0.70f, tintFraction = 0.18f),
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
                Spacer(Modifier.height(2.dp))
                // Power Impact Metric
                val (impactColor, impactText) = when (item.powerImpact) {
                    PowerImpactLevel.VERY_HIGH -> Pair(Color(0xFFEF4444), "⚡ Muito Alto")
                    PowerImpactLevel.HIGH -> Pair(Color(0xFFF97316), "⚡ Alto")
                    PowerImpactLevel.MEDIUM -> Pair(Color(0xFFF59E0B), "⚡ Médio")
                    PowerImpactLevel.LOW -> Pair(Color(0xFF10B981), "⚡ Baixo")
                    PowerImpactLevel.MINIMAL -> Pair(colorScheme.onSurface.copy(alpha = 0.45f), "⚡ Mínimo")
                }
                Text(
                    text = impactText,
                    fontSize = 9.5.sp,
                    color = impactColor,
                    fontWeight = FontWeight.SemiBold,
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
    batteryHistory: List<Float> = emptyList(),
    onNavigateToNetworkStats: () -> Unit = {},
    onNavigateToBatteryStats: () -> Unit = {},
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
            // 1. CPU WAVEFORM CARD (Waveform chart with dual invisible columns for 8 cores)
            val cpuUsage = snapshot?.totalCpuPercent ?: 0.0
            CpuWaveformCard(
                cpuUsage = cpuUsage,
                coreFrequencies = snapshot?.cpuFrequenciesMhz ?: emptyList(),
                cpuHistory = cpuHistory,
                snapshot = snapshot,
            )
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
                headline = "↓ ${formatSpeed(rxSpeed)}  ↑ ${formatSpeed(txSpeed)}",
                subHeadline = "Tipo de conexão: ${snapshot?.networkType ?: "Conectado"}",
                history = netRxHistory,
                chartColor = Color(0xFF06B6D4),
                actionButton = {
                    Text(
                        text = "Ver mais",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF06B6D4),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onNavigateToNetworkStats)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                },
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
            // 4. BATTERY & POWER RESOURCE CARD
            val battPct = snapshot?.batteryPercent ?: 0
            val isCharging = snapshot?.isCharging ?: false
            val currentMa = snapshot?.batteryCurrentNowMa
            val powerWatts = snapshot?.batteryPowerWatts
            val voltageMv = snapshot?.batteryVoltageMv
            val tempC = snapshot?.batteryTempCelsius ?: 0f
            val health = snapshot?.batteryHealth ?: "Boa"

            val battAccent = when {
                isCharging -> Color(0xFF10B981) // Green
                battPct <= 20 -> Color(0xFFEF4444) // Red
                battPct <= 40 -> Color(0xFFF59E0B) // Amber
                else -> Color(0xFF10B981) // Green
            }

            val headline = "$battPct% · ${if (isCharging) "Carregando" else "Em Descarga"}"
            val subHeadline = if (currentMa != null && currentMa != 0L) {
                val sign = if (currentMa > 0 && isCharging) "+" else ""
                val wattStr = if (powerWatts != null) " (${String.format(Locale.US, "%.2f", powerWatts)} W)" else ""
                "$sign${currentMa} mA$wattStr"
            } else {
                "Alimentação via Bateria"
            }

            PerformanceResourceCard(
                title = stringResource(R.string.task_manager_battery_title),
                icon = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                accentColor = battAccent,
                headline = headline,
                subHeadline = subHeadline,
                history = batteryHistory,
                chartColor = battAccent,
                actionButton = {
                    Text(
                        text = "Ver mais",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = battAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onNavigateToBatteryStats)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SpecItem(
                        label = stringResource(R.string.task_manager_battery_voltage),
                        value = if (voltageMv != null) "${String.format(Locale.US, "%.2f", voltageMv / 1000.0)} V" else "—",
                    )
                    SpecItem(
                        label = stringResource(R.string.task_manager_battery_temp),
                        value = "${String.format(Locale.US, "%.1f", tempC)} °C",
                    )
                    SpecItem(
                        label = stringResource(R.string.task_manager_battery_health),
                        value = health,
                    )
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
private fun CpuWaveformCard(
    cpuUsage: Double,
    coreFrequencies: List<Long>,
    cpuHistory: List<Float>,
    snapshot: SystemPerformanceSnapshot?,
) {
    val colorScheme = MaterialTheme.colorScheme

    val accentColor = when {
        cpuUsage > 75.0 -> Color(0xFFEF4444)
        cpuUsage > 45.0 -> Color(0xFFF59E0B)
        else -> Color(0xFF38BDF8) // Electric Cyan
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, dynamicBorderColor(alpha = 0.28f, tintFraction = 0.35f), RoundedCornerShape(20.dp)),
        color = dynamicSurfaceColor(alpha = 0.72f, tintFraction = 0.18f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header Row: Title & Percentage
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
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Processador (CPU)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "Utilização total do processador",
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                Text(
                    text = "${String.format(Locale.US, "%.1f", cpuUsage)}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Container do Gráfico de Ondas de CPU com as Duas Colunas Invisíveis sobrepostas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.22f))
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
            ) {
                // 1. Gráfico de Ondas de CPU em movimento fluido
                CpuFluidWaveformGraph(
                    history = cpuHistory,
                    currentCpu = cpuUsage.toFloat(),
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize(),
                )

                // 2. Duas colunas invisíveis sobrepostas ao gráfico com os 8 núcleos
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Coluna 1 (Invisível): Núcleos 0 a 3
                    Column(
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        for (i in 0..3) {
                            val freq = coreFrequencies.getOrNull(i) ?: 0L
                            Text(
                                text = "CPU $i: ${if (freq > 0) "$freq MHz" else "Ocioso"}",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface.copy(alpha = 0.92f),
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(
                                        color = colorScheme.surface.copy(alpha = 0.85f),
                                        blurRadius = 6f,
                                    ),
                                ),
                            )
                        }
                    }

                    // Coluna 2 (Invisível): Núcleos 4 a 7
                    Column(
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        for (i in 4..7) {
                            val freq = coreFrequencies.getOrNull(i) ?: 0L
                            Text(
                                text = "CPU $i: ${if (freq > 0) "$freq MHz" else "Ocioso"}",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface.copy(alpha = 0.92f),
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(
                                        color = colorScheme.surface.copy(alpha = 0.85f),
                                        blurRadius = 6f,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            HorizontalDivider(
                color = colorScheme.outline.copy(alpha = 0.1f),
                thickness = 1.dp,
            )

            Spacer(Modifier.height(12.dp))

            // Informações adicionais
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpecItem(
                    label = stringResource(R.string.task_manager_cpu_cores),
                    value = "${snapshot?.cpuCores ?: 1} Núcleos",
                )

                val uptimeSec = (snapshot?.uptimeMillis ?: 0L) / 1000L
                val hours = uptimeSec / 3600
                val minutes = (uptimeSec % 3600) / 60
                val seconds = uptimeSec % 60
                SpecItem(
                    label = stringResource(R.string.task_manager_cpu_uptime),
                    value = "${hours}h ${minutes}m ${seconds}s",
                )

                SpecItem(
                    label = stringResource(R.string.task_manager_cpu_threads),
                    value = "${snapshot?.totalProcesses ?: 0} / ${snapshot?.totalThreads ?: 0}",
                )
            }
        }
    }
}

@Composable
private fun CpuFluidWaveformGraph(
    history: List<Float>,
    currentCpu: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val gridColor = colorScheme.outline.copy(alpha = 0.12f)

    // Smooth animation for the latest CPU usage value to create fluid transitions
    val animatedCpu by animateFloatAsState(
        targetValue = currentCpu.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "animatedCpu",
    )

    // Continuous wave phase animation for live motion effect
    val infiniteTransition = rememberInfiniteTransition(label = "waveShift")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // 1. Draw horizontal guide lines (0%, 25%, 50%, 75%, 100%)
        val steps = 4
        for (i in 0..steps) {
            val y = height * (i.toFloat() / steps)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f,
            )
        }

        // Fixed buffer size so stepX is constant and wave flows smoothly from right to left
        val maxPoints = 35
        val workingList = history.toMutableList()
        if (workingList.isNotEmpty()) {
            workingList[workingList.size - 1] = animatedCpu
        } else {
            workingList.add(animatedCpu)
        }

        val paddedPoints = if (workingList.size < maxPoints) {
            val pad = List(maxPoints - workingList.size) { 0f }
            pad + workingList
        } else {
            workingList.takeLast(maxPoints)
        }

        val stepX = width / (maxPoints - 1)
        val fillPath = Path()
        val strokePath = Path()

        // Map points to canvas coordinates with organic fluid wave ripple
        val coords = paddedPoints.mapIndexed { index, value ->
            val x = index * stepX
            val rawY = height - (value.coerceIn(0f, 100f) / 100f * (height - 8f)) - 4f
            val rippleScale = (value / 100f).coerceIn(0.05f, 1f)
            val waveMod = kotlin.math.sin(wavePhase + index * 0.45f).toFloat() * (2.5f * rippleScale)
            val y = (rawY + waveMod).coerceIn(0f, height)
            Offset(x, y)
        }

        fillPath.moveTo(coords.first().x, height)
        fillPath.lineTo(coords.first().x, coords.first().y)
        strokePath.moveTo(coords.first().x, coords.first().y)

        for (i in 1 until coords.size) {
            val prev = coords[i - 1]
            val curr = coords[i]
            val midX = (prev.x + curr.x) / 2f
            fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            strokePath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
        }

        fillPath.lineTo(coords.last().x, height)
        fillPath.close()

        // 2. Draw Rich Gradient Fill Under the Wave
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.55f),
                    accentColor.copy(alpha = 0.30f),
                    accentColor.copy(alpha = 0.15f),
                ),
            ),
        )

        // 3. Draw Outer Wave Line
        drawPath(
            path = strokePath,
            color = accentColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // 4. Draw Leading Point Pulse
        val lastCoord = coords.last()
        drawCircle(
            color = accentColor,
            radius = 3.5.dp.toPx(),
            center = lastCoord,
        )
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
    actionButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, dynamicBorderColor(alpha = 0.28f, tintFraction = 0.35f), RoundedCornerShape(20.dp)),
        color = dynamicSurfaceColor(alpha = 0.72f, tintFraction = 0.18f),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subHeadline,
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = headline,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    if (actionButton != null) {
                        Spacer(Modifier.height(1.dp))
                        actionButton()
                    }
                }
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
internal fun RealtimeTelemetryGraph(
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

            val maxPoints = 30
            val paddedHistory = if (history.size < maxPoints) {
                List(maxPoints - history.size) { 0f } + history
            } else {
                history.takeLast(maxPoints)
            }

            // Compute min/max for normalization
            val maxVal = maxOf(paddedHistory.maxOrNull() ?: 1f, 100f)
            val minVal = 0f
            val stepX = width / (maxPoints - 1)

            val points = paddedHistory.mapIndexed { index, value ->
                val x = index * stepX
                val normalized = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val y = height - (normalized * (height - 6f)) - 3f
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

                // Draw Rich Gradient Fill under the curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.55f),
                            color.copy(alpha = 0.30f),
                            color.copy(alpha = 0.15f),
                        ),
                    ),
                )

                // Draw Outer Stroke Line
                drawPath(
                    path = strokePath,
                    color = color,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
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
