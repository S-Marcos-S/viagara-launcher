package dev.viagaralauncher.ui.battery

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.glass.GlassCard
import dev.viagaralauncher.R
import dev.viagaralauncher.battery.BatteryStatsParser
import dev.viagaralauncher.battery.ShellRunner
import dev.viagaralauncher.battery.LocalShowAppNames
import dev.viagaralauncher.battery.rememberAppLabel
import dev.viagaralauncher.battery.rememberAppLabelLine
import dev.viagaralauncher.battery.rememberAppIcon
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.viagaralauncher.battery.BatteryStatsViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import dev.viagaralauncher.battery.DrainState
import dev.viagaralauncher.battery.formatDuration
import dev.viagaralauncher.battery.formatDrainRate
import dev.viagaralauncher.battery.formatDrainPercentage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BatteryStatsScreen(
    onBack: () -> Unit,
    showHeader: Boolean = true,
    vm: BatteryStatsViewModel = viewModel()
) {
    var showAppNames by remember { mutableStateOf(true) }
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val deviceIdle by vm.deviceIdle.collectAsStateWithLifecycle()
    val powerManager by vm.powerManager.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val drainState by vm.drainState.collectAsStateWithLifecycle()
    val isDrainNotificationEnabled by vm.isDrainNotificationEnabled.collectAsStateWithLifecycle()
    val hasAdb by vm.hasAdb.collectAsStateWithLifecycle()
    val hasAdvanced by vm.hasAdvanced.collectAsStateWithLifecycle()
    val advMode by vm.advMode.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHost = remember { SnackbarHostState() }
    val statsResetSuccessMsg = stringResource(R.string.stats_reset_success)
    val statsResetFailedMsg = stringResource(R.string.stats_reset_failed)

    val tabs = remember {
        listOf(
            StatsTab(R.string.tab_overview, Icons.Outlined.Dashboard),
            StatsTab(R.string.tab_apps, Icons.Outlined.Apps),
            StatsTab(R.string.tab_wakelocks, Icons.Outlined.Alarm),
            StatsTab(R.string.tab_network, Icons.Outlined.Wifi),
            StatsTab(R.string.tab_alarms, Icons.Outlined.Schedule),
            StatsTab(R.string.tab_system, Icons.Outlined.SettingsApplications),
            StatsTab(R.string.settings, Icons.Outlined.Settings)
        )
    }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        vm.refresh()
    }

    Scaffold(
        modifier = if (showHeader) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        containerColor = if (showHeader) dynamicSurfaceColor(alpha = 0.40f, tintFraction = 0.16f) else Color.Transparent,
        topBar = {
            if (showHeader) {
                LargeTopAppBar(
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = dynamicSurfaceColor(alpha = 0.70f, tintFraction = 0.18f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    title = {
                        Column {
                            Text(stringResource(R.string.detailed_stats))
                            AnimatedVisibility(visible = lastRefresh > 0) {
                                val timeFormatter = remember(Locale.getDefault()) {
                                    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
                                }
                                val formatted = remember(lastRefresh) {
                                    Instant.ofEpochMilli(lastRefresh)
                                        .atZone(ZoneId.systemDefault())
                                        .format(timeFormatter)
                                }
                                val via = when (advMode) {
                                    ShellRunner.Mode.NONE -> ""
                                    else -> stringResource(R.string.via_mode, advMode.name.lowercase())
                                }
                                Text(
                                    stringResource(R.string.updated, formatted + via),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = { scope.launch { vm.refresh() } }) {
                                Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh))
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                if (vm.resetStats()) {
                                    snackbarHost.showSnackbar(statsResetSuccessMsg)
                                    vm.refresh()
                                } else {
                                    snackbarHost.showSnackbar(statsResetFailedMsg)
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.RestartAlt, stringResource(R.string.reset_stats))
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!hasAdvanced) {
                PrivilegeRequiredCard(
                    hasAdb = hasAdb,
                    onRecheck = { vm.refresh(forceRefresh = true) }
                )
            } else {
                // Tab row with frosted glass and wallpaper Monet highlights
                SecondaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(pagerState.currentPage),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = pagerState.currentPage == index
                        Tab(
                            selected = isSelected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    stringResource(tab.titleRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                HorizontalDivider()

                // Pager content
                CompositionLocalProvider(LocalShowAppNames provides showAppNames) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> OverviewTab(
                                snapshot = snapshot,
                                deviceIdle = deviceIdle,
                                powerManager = powerManager,
                                drainState = drainState,
                                isDrainNotificationEnabled = isDrainNotificationEnabled,
                                onToggleDrainNotification = { vm.toggleDrainNotification(it) },
                                onResetDrainSession = { vm.resetDrainSession() },
                            )
                            1 -> AppsTab(
                                apps = snapshot?.apps ?: emptyList(),
                                estimatedCapacityMah = snapshot?.estimatedCapacityMah ?: 0,
                                totalDischargePercent = (snapshot?.screenOnDischargePercent ?: 0f) + (snapshot?.screenOffDischargePercent ?: 0f),
                            )
                            2 -> WakelocksTab(snapshot?.wakelocks ?: emptyList(), snapshot?.kernelWakelocks ?: emptyList())
                            3 -> NetworkTab(snapshot?.network ?: emptyList())
                            4 -> AlarmsJobsTab(snapshot?.alarms ?: emptyList(), snapshot?.jobs ?: emptyList(), snapshot?.syncs ?: emptyList())
                            5 -> SystemTab(snapshot, deviceIdle, powerManager)
                            6 -> BatterySettingsTab(vm)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = error != null) {
                val message = remember(error) { error }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Error,
                            null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            message.orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = { vm.clearError() }) {
                            Icon(
                                Icons.Outlined.Close,
                                stringResource(R.string.dismiss),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }

}

private data class StatsTab(val titleRes: Int, val icon: ImageVector)

@Composable
private fun PrivilegeRequiredCard(
    hasAdb: Boolean,
    onRecheck: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            FrostedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.advanced_stats_required), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        stringResource(R.string.advanced_stats_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        StatusChip(stringResource(R.string.adb_dump), hasAdb)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRecheck) { Text(stringResource(R.string.recheck)) }
                    }
                }
            }
        }
        item {
            AdbGrantCard(context)
        }
        item {
            FrostedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.troubleshooting), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.troubleshoot_1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.troubleshoot_2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.troubleshoot_3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.troubleshoot_4), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.troubleshoot_5), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, granted: Boolean) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                stringResource(
                    R.string.granted_yes_no,
                    label,
                    stringResource(if (granted) R.string.yes else R.string.no)
                )
            )
        },
        leadingIcon = {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                null,
                tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (granted) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun AdbGrantCard(context: Context) {
    val pkg = context.packageName
    val commands = listOf(
        "adb shell pm grant $pkg android.permission.BATTERY_STATS",
        "adb shell pm grant $pkg android.permission.DUMP",
        "adb shell pm grant $pkg android.permission.PACKAGE_USAGE_STATS",
        "adb shell pm grant $pkg android.permission.INTERACT_ACROSS_USERS",
        "adb shell settings put global hidden_api_policy 1  # optional"
    )
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.adb), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.adb_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            commands.forEach { cmd ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cmd, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()))
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                    }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy), modifier = Modifier.size(18.dp)) }
                }
                HorizontalDivider(color = dynamicBorderColor(alpha = 0.20f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("adb", commands.joinToString("\n")))
                }) { Text(stringResource(R.string.copy_all)) }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.app_info)) }
            }
        }
    }
}

@Composable
private fun RealtimeDrainCard(
    drainState: DrainState,
    isNotificationEnabled: Boolean,
    onToggleNotification: (Boolean) -> Unit,
    onResetSession: () -> Unit
) {
    FrostedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.live_drain_card_title),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AnimatedVisibility(
                        visible = isNotificationEnabled,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        IconButton(
                            onClick = onResetSession,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.RestartAlt,
                                contentDescription = stringResource(R.string.reset_session),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Switch(
                        checked = isNotificationEnabled,
                        onCheckedChange = onToggleNotification,
                        modifier = Modifier.scale(0.80f)
                    )
                }
            }

            Text(
                text = stringResource(R.string.drain_notification_toggle_desc),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(
                visible = isNotificationEnabled,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = dynamicBorderColor(alpha = 0.20f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DrainMetricItem(
                            label = stringResource(R.string.screen_on),
                            rate = formatDrainPercentage(drainState.screenOnDrainRatePercent),
                            time = formatDuration(drainState.screenOnTimeMs),
                            color = Color(0xFFEAB308)
                        )
                        DrainMetricItem(
                            label = stringResource(R.string.screen_off),
                            rate = formatDrainPercentage(drainState.screenOffDrainRatePercent),
                            time = formatDuration(drainState.screenOffTimeMs),
                            color = Color(0xFF3B82F6)
                        )
                        DrainMetricItem(
                            label = stringResource(R.string.deep_sleep),
                            rate = formatDrainPercentage(drainState.deepSleepDrainRatePercent),
                            time = "${formatDuration(drainState.deepSleepTimeMs)} (${String.format(Locale.getDefault(), "%.0f%%", drainState.deepSleepPercentage)})",
                            color = Color(0xFF10B981)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DrainMetricItem(
                            label = stringResource(R.string.active),
                            rate = formatDrainPercentage(drainState.activeDrainRatePercent),
                            time = formatDuration(drainState.activeTimeMs),
                            color = Color(0xFFEF4444)
                        )
                        DrainMetricItem(
                            label = stringResource(R.string.idle),
                            rate = formatDrainPercentage(drainState.idleDrainRatePercent),
                            time = formatDuration(drainState.idleTimeMs),
                            color = Color(0xFF8B5CF6)
                        )
                        DrainMetricItem(
                            label = stringResource(R.string.total),
                            rate = String.format(Locale.getDefault(), "%.1f mAh", drainState.totalDrainMah),
                            time = formatDuration(drainState.totalTimeMs),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DrainMetricItem(
    label: String,
    rate: String,
    time: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = rate,
            fontSize = 12.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = time,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OverviewTab(
    snapshot: BatteryStatsParser.FullSnapshot?,
    deviceIdle: BatteryStatsParser.DeviceIdleInfo?,
    powerManager: BatteryStatsParser.PowerManagerInfo?,
    drainState: DrainState,
    isDrainNotificationEnabled: Boolean,
    onToggleDrainNotification: (Boolean) -> Unit,
    onResetDrainSession: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RealtimeDrainCard(
                drainState = drainState,
                isNotificationEnabled = isDrainNotificationEnabled,
                onToggleNotification = onToggleDrainNotification,
                onResetSession = onResetDrainSession
            )
        }
        item { SummaryCard(snapshot) }
        item { DischargeBreakdownCard(snapshot) }
        item { SignalQualityCard(snapshot) }
        item { DozeStatsCard(snapshot?.doze, deviceIdle) }
        item { BluetoothCard(snapshot?.bluetooth) }
        item { CurrentStateCard(deviceIdle, powerManager) }
    }
}

@Composable
private fun SummaryCard(snapshot: BatteryStatsParser.FullSnapshot?) {
    StatsCard(titleRes = R.string.summary, icon = Icons.Outlined.Summarize) {
        if (snapshot == null) {
            Text(stringResource(R.string.no_data_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val hours = snapshot.batteryRealtimeMs / 3600000.0
            val screenHours = snapshot.screenOnTimeMs / 3600000.0

            StatRow(R.string.time_on_battery, stringResource(R.string.hours_value, String.format(Locale.getDefault(), "%.1f", hours)))
            StatRow(R.string.screen_on_time, stringResource(R.string.hours_value, String.format(Locale.getDefault(), "%.1f", screenHours)))
            StatRow(R.string.estimated_capacity, "${snapshot.estimatedCapacityMah} mAh")
            StatRow(R.string.apps_tracked, "${snapshot.apps.size}")
            StatRow(R.string.wakelocks, "${snapshot.wakelocks.size}")
            StatRow(R.string.kernel_wakelocks, "${snapshot.kernelWakelocks.size}")
        }
    }
}

@Composable
private fun DischargeBreakdownCard(snapshot: BatteryStatsParser.FullSnapshot?) {
    StatsCard(titleRes = R.string.discharge_breakdown, icon = Icons.Outlined.BatteryAlert) {
        if (snapshot == null) {
            Text(stringResource(R.string.no_data_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DischargeBox(
                    labelRes = R.string.screen_on,
                    percent = snapshot.screenOnDischargePercent,
                    color = MaterialTheme.colorScheme.primary
                )
                DischargeBox(
                    labelRes = R.string.screen_off,
                    percent = snapshot.screenOffDischargePercent,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun DischargeBox(labelRes: Int, percent: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%.1f%%", percent),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
    }
}


@Composable
private fun SignalQualityCard(snapshot: BatteryStatsParser.FullSnapshot?) {
    StatsCard(titleRes = R.string.signal_quality, icon = Icons.Outlined.SignalCellularAlt) {
        if (snapshot == null || snapshot.signalStrength.isEmpty()) {
            Text(stringResource(R.string.no_signal_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val signalLabels = listOf<Int>(
                R.string.signal_none,
                R.string.signal_poor,
                R.string.signal_moderate,
                R.string.signal_good,
                R.string.signal_great
            )
            val colors = listOf(
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                MaterialTheme.colorScheme.primary
            )

            Text(stringResource(R.string.mobile_signal), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            snapshot.signalStrength.forEachIndexed { index, stat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(signalLabels.getOrNull(index) ?: R.string.signal_level, index),
                        modifier = Modifier.width(80.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { stat.percentOfTotal },
                        modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = colors.getOrNull(index) ?: MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        String.format(Locale.getDefault(), "%.0f%%", stat.percentOfTotal * 100),
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (snapshot.wifiSignal.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wifi_signal), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                snapshot.wifiSignal.forEachIndexed { index, stat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(signalLabels.getOrNull(index) ?: R.string.signal_level, index),
                            modifier = Modifier.width(80.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { stat.percentOfTotal },
                            modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                            color = colors.getOrNull(index) ?: MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            String.format(Locale.getDefault(), "%.0f%%", stat.percentOfTotal * 100),
                            modifier = Modifier.width(50.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DozeStatsCard(doze: BatteryStatsParser.DozeStats?, deviceIdle: BatteryStatsParser.DeviceIdleInfo?) {
    StatsCard(titleRes = R.string.doze_statistics, icon = Icons.Outlined.PowerSettingsNew) {
        if (doze == null && deviceIdle == null) {
            Text(stringResource(R.string.no_doze_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (deviceIdle != null) {
                StatRow(R.string.doze_state, deviceIdle.currentState)
                StatRow(R.string.light_state, deviceIdle.lightState)
            }
            if (doze != null) {
                StatRow(R.string.deep_doze_time, formatDuration(doze.deepIdleTimeMs))
                StatRow(R.string.deep_doze_count, "${doze.deepIdleCount}")
                StatRow(R.string.light_doze_time, formatDuration(doze.lightIdleTimeMs))
                StatRow(R.string.light_doze_count, "${doze.lightIdleCount}")
                StatRow(R.string.deep_idling_time, formatDuration(doze.deepIdlingTimeMs))
                StatRow(R.string.deep_idling_count, "${doze.deepIdlingCount}")
                StatRow(R.string.light_idling_time, formatDuration(doze.lightIdlingTimeMs))
                StatRow(R.string.light_idling_count, "${doze.lightIdlingCount}")
            }
        }
    }
}

@Composable
private fun BluetoothCard(bluetooth: BatteryStatsParser.BluetoothStats?) {
    StatsCard(titleRes = R.string.bluetooth, icon = Icons.Outlined.Bluetooth) {
        if (bluetooth == null) {
            Text(stringResource(R.string.no_bluetooth_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            StatRow(R.string.idle_time, formatDuration(bluetooth.idleTimeMs))
            StatRow(R.string.rx_time, formatDuration(bluetooth.rxTimeMs))
            StatRow(R.string.tx_time, formatDuration(bluetooth.txTimeMs))
            StatRow(R.string.power_usage, String.format(Locale.getDefault(), "%.2f mAh", bluetooth.powerMah))
            if (bluetooth.scanTimeMs > 0) {
                StatRow(R.string.scan_time, formatDuration(bluetooth.scanTimeMs))
            }
        }
    }
}

@Composable
private fun CurrentStateCard(
    deviceIdle: BatteryStatsParser.DeviceIdleInfo?,
    powerManager: BatteryStatsParser.PowerManagerInfo?
) {
    StatsCard(titleRes = R.string.current_state, icon = Icons.Outlined.Info) {
        if (deviceIdle != null) {
            StatRow(R.string.deep_doze_enabled, stringResource(if (deviceIdle.deepEnabled) R.string.yes else R.string.no))
            StatRow(R.string.light_doze_enabled, stringResource(if (deviceIdle.lightEnabled) R.string.yes else R.string.no))
        }

        if (powerManager != null) {
            Spacer(Modifier.height(8.dp))
            StatRow(R.string.screen, stringResource(if (powerManager.isScreenOn) R.string.on else R.string.off))
            StatRow(R.string.battery_level_label, stringResource(R.string.battery_percent, powerManager.batteryLevel))
            StatRow(R.string.battery_status, powerManager.batteryStatus)
            StatRow(R.string.low_power_mode, stringResource(if (powerManager.lowPowerMode) R.string.yes else R.string.no))
            StatRow(R.string.device_idle_mode, powerManager.deviceIdleMode)

            if (powerManager.holdingWakeLocks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(
                        R.plurals.active_wakelocks_count,
                        powerManager.holdingWakeLocks.size,
                        powerManager.holdingWakeLocks.size
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (deviceIdle == null && powerManager == null) {
            Text(stringResource(R.string.no_state_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsTab(
    apps: List<BatteryStatsParser.AppPowerStats>,
    estimatedCapacityMah: Int = 0,
    totalDischargePercent: Float = 0f,
) {
    var sortBy by remember { mutableStateOf(AppSortOption.POWER) }
    var showSystemApps by remember { mutableStateOf(false) }

    val effectiveCapacity = if (estimatedCapacityMah > 0) estimatedCapacityMah.toDouble() else 4500.0
    val totalAppsPowerMah = remember(apps) { apps.sumOf { it.powerMah } }

    val filteredApps = remember(apps, sortBy, showSystemApps) {
        apps.filter { app ->
            if (showSystemApps) true else isUserApp(app)
        }.let { list ->
            when (sortBy) {
                AppSortOption.POWER -> list.sortedByDescending { it.powerMah }
                AppSortOption.CPU -> list.sortedByDescending { it.cpuTimeMs }
                AppSortOption.WAKELOCK -> list.sortedByDescending { it.wakeLockTimeMs }
                AppSortOption.NETWORK -> list.sortedByDescending {
                    it.mobileRxBytes + it.mobileTxBytes + it.wifiRxBytes + it.wifiTxBytes
                }
                AppSortOption.FOREGROUND -> list.sortedByDescending { it.foregroundTimeMs }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppsSummaryCard(
            totalPowerMah = totalAppsPowerMah,
            capacityMah = effectiveCapacity,
            activeAppsCount = apps.count { it.powerMah > 0.05 },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        )

        // Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = showSystemApps,
                onClick = { showSystemApps = !showSystemApps },
                label = { Text(stringResource(R.string.system_apps)) }
            )

            Spacer(Modifier.weight(1f))

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                AssistChip(
                    onClick = { expanded = true },
                    label = { Text(stringResource(R.string.sort_by, stringResource(sortBy.titleRes))) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    AppSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.titleRes)) },
                            onClick = {
                                sortBy = option
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_apps_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filteredApps, key = { _, app -> app.uid }) { index, app ->
                    AppStatsCard(
                        rank = index + 1,
                        app = app,
                        capacityMah = effectiveCapacity,
                        totalSessionAppsMah = totalAppsPowerMah,
                        sessionDischargePct = totalDischargePercent,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppsSummaryCard(
    totalPowerMah: Double,
    capacityMah: Double,
    activeAppsCount: Int,
    modifier: Modifier = Modifier,
) {
    val pctOfCapacity = if (capacityMah > 0) {
        (totalPowerMah / capacityMah * 100.0).coerceAtLeast(0.0)
    } else 0.0

    FrostedCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.total_apps_power_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1f mAh", totalPowerMah),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.total_apps_power_subtitle,
                        String.format(Locale.getDefault(), "%.1f", pctOfCapacity),
                        activeAppsCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f%%", pctOfCapacity),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private enum class AppSortOption(val titleRes: Int) {
    POWER(R.string.sort_power),
    CPU(R.string.sort_cpu_time),
    WAKELOCK(R.string.sort_wakelock),
    NETWORK(R.string.sort_network),
    FOREGROUND(R.string.sort_foreground)
}

private fun isUserApp(app: BatteryStatsParser.AppPowerStats): Boolean {
    val names = if (app.packages.isNotEmpty()) app.packages else listOf(app.packageName)

    if (names.isEmpty() || app.packageName.startsWith("uid:")) {
        return BatteryStatsParser.isUserApp(app.uid, names)
    }
    return BatteryStatsParser.isUserApp(app.uid, names)
}

@Composable
private fun AppStatsCard(
    rank: Int,
    app: BatteryStatsParser.AppPowerStats,
    capacityMah: Double = 4500.0,
    totalSessionAppsMah: Double = 0.0,
    sessionDischargePct: Float = 0f,
) {
    var expanded by remember { mutableStateOf(false) }
    val appIconDrawable = rememberAppIcon(app.packageName)
    val appIconBitmap = remember(appIconDrawable) {
        appIconDrawable?.let {
            runCatching {
                it.toBitmap(width = 96, height = 96).asImageBitmap()
            }.getOrNull()
        }
    }

    val percentOfBattery = if (capacityMah > 0.0) {
        (app.powerMah / capacityMah) * 100.0
    } else 0.0

    val percentOfSession = if (sessionDischargePct > 0f && totalSessionAppsMah > 0.0) {
        (app.powerMah / totalSessionAppsMah) * sessionDischargePct.toDouble()
    } else {
        percentOfBattery
    }

    FrostedCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$rank",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val appLabel = rememberAppLabel(app.packageName)
                    if (appLabel != null) {
                        Text(
                            appLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f%%", percentOfSession),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f mAh", app.powerMah),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "UID ${app.uid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    if (app.packages.size > 1) {
                        Text(
                            pluralStringResource(
                                R.plurals.packages_share_uid,
                                app.packages.size,
                                app.packages.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = dynamicBorderColor(alpha = 0.20f))
                    Spacer(Modifier.height(8.dp))

                    if (app.packages.size > 1) {
                        Text(stringResource(R.string.packages_shared_uid, app.uid), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        app.packages.take(20).forEach { pkg ->
                            Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (app.packages.size > 20) {
                            val more = app.packages.size - 20
                            Text(
                                pluralStringResource(R.plurals.more_count, more, more),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(stringResource(R.string.power_breakdown), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    StatRow(R.string.cpu, String.format(Locale.getDefault(), "%.2f mAh", app.cpuPowerMah))
                    StatRow(R.string.wakelock, String.format(Locale.getDefault(), "%.2f mAh", app.wakeLockPowerMah))
                    StatRow(R.string.mobile_radio, String.format(Locale.getDefault(), "%.2f mAh", app.mobilePowerMah))
                    StatRow(R.string.wifi, String.format(Locale.getDefault(), "%.2f mAh", app.wifiPowerMah))
                    StatRow(R.string.gps, String.format(Locale.getDefault(), "%.2f mAh", app.gpsPowerMah))
                    StatRow(R.string.sensors, String.format(Locale.getDefault(), "%.2f mAh", app.sensorPowerMah))
                    StatRow(R.string.camera, String.format(Locale.getDefault(), "%.2f mAh", app.cameraPowerMah))
                    StatRow(R.string.bluetooth, String.format(Locale.getDefault(), "%.2f mAh", app.bluetoothPowerMah))

                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.time_usage), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    StatRow(R.string.cpu_time, formatDuration(app.cpuTimeMs))
                    StatRow(R.string.wakelock_time, formatDuration(app.wakeLockTimeMs))
                    StatRow(R.string.foreground, formatDuration(app.foregroundTimeMs))
                    if (app.foregroundServiceTimeMs > 0L) {
                        StatRow(R.string.foreground_service, formatDuration(app.foregroundServiceTimeMs))
                    }
                    StatRow(R.string.background_service, formatDuration(app.backgroundTimeMs))
                    StatRow(R.string.gps, formatDuration(app.gpsTimeMs))
                    StatRow(R.string.sensors, formatDuration(app.sensorTimeMs))

                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.network), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    StatRow(R.string.mobile_rx, formatBytes(app.mobileRxBytes))
                    StatRow(R.string.mobile_tx, formatBytes(app.mobileTxBytes))
                    StatRow(R.string.wifi_rx, formatBytes(app.wifiRxBytes))
                    StatRow(R.string.wifi_tx, formatBytes(app.wifiTxBytes))
                }
            }
        }
    }
}

@Composable
private fun WakelocksTab(
    wakelocks: List<BatteryStatsParser.WakelockStats>,
    kernelWakelocks: List<BatteryStatsParser.KernelWakelockStats>
) {
    var showKernel by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !showKernel,
                onClick = { showKernel = false },
                label = {
                    Text(
                        pluralStringResource(
                            R.plurals.app_wakelocks_count,
                            wakelocks.size,
                            wakelocks.size
                        )
                    )
                }
            )
            FilterChip(
                selected = showKernel,
                onClick = { showKernel = true },
                label = { Text(stringResource(R.string.kernel_count, kernelWakelocks.size)) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showKernel) {
                if (kernelWakelocks.isEmpty()) {
                    item {
                        Text(stringResource(R.string.no_kernel_wakelocks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(kernelWakelocks, key = { it.name }) { wl ->
                        KernelWakelockCard(wl)
                    }
                }
            } else {
                if (wakelocks.isEmpty()) {
                    item {
                        Text(stringResource(R.string.no_app_wakelocks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(wakelocks, key = { "${it.uid}_${it.tag}" }) { wl ->
                        WakelockCard(wl)
                    }
                }
            }
        }
    }
}

@Composable
private fun WakelockCard(wl: BatteryStatsParser.WakelockStats) {
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        wl.tag,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        rememberAppLabelLine(wl.packageName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AssistChip(
                    onClick = {},
                    label = { Text(wl.type.name) }
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${wl.count}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.total_time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(wl.totalTimeMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun KernelWakelockCard(wl: BatteryStatsParser.KernelWakelockStats) {
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                wl.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.count), style = MaterialTheme.typography.labelSmall)
                    Text("${wl.count}", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.active), style = MaterialTheme.typography.labelSmall)
                    Text("${wl.activeCount}", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.total_time), style = MaterialTheme.typography.labelSmall)
                    Text(formatDuration(wl.totalTimeMs), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NetworkTab(network: List<BatteryStatsParser.NetworkStats>) {
    var sortBy by remember { mutableStateOf(NetworkSortOption.TOTAL) }

    val sorted = remember(network, sortBy) {
        when (sortBy) {
            NetworkSortOption.TOTAL -> network.sortedByDescending {
                it.mobileRxBytes + it.mobileTxBytes + it.wifiRxBytes + it.wifiTxBytes
            }
            NetworkSortOption.MOBILE -> network.sortedByDescending { it.mobileRxBytes + it.mobileTxBytes }
            NetworkSortOption.WIFI -> network.sortedByDescending { it.wifiRxBytes + it.wifiTxBytes }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NetworkSortOption.entries.forEach { option ->
                FilterChip(
                    selected = sortBy == option,
                    onClick = { sortBy = option },
                    label = { Text(stringResource(option.titleRes)) }
                )
            }
        }

        if (sorted.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_network_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { it.uid }) { net ->
                    NetworkCard(net)
                }
            }
        }
    }
}

private enum class NetworkSortOption(val titleRes: Int) {
    TOTAL(R.string.total),
    MOBILE(R.string.mobile),
    WIFI(R.string.wifi)
}

@Composable
private fun NetworkCard(net: BatteryStatsParser.NetworkStats) {
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val netLabel = rememberAppLabel(net.packageName)
            if (netLabel != null) {
                Text(
                    netLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    net.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    net.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.mobile), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.rx_tx, formatBytes(net.mobileRxBytes), formatBytes(net.mobileTxBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.wifi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        stringResource(R.string.rx_tx, formatBytes(net.wifiRxBytes), formatBytes(net.wifiTxBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmsJobsTab(
    alarms: List<BatteryStatsParser.AlarmStats>,
    jobs: List<BatteryStatsParser.JobStats>,
    syncs: List<BatteryStatsParser.SyncStats>
) {
    var selected by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selected) {
            Tab(
                selected = selected == 0,
                onClick = { selected = 0 },
                text = {
                    Text(
                        pluralStringResource(R.plurals.alarms_count_plural, alarms.size, alarms.size)
                    )
                }
            )
            Tab(
                selected = selected == 1,
                onClick = { selected = 1 },
                text = { Text(pluralStringResource(R.plurals.jobs_count, jobs.size, jobs.size)) }
            )
            Tab(
                selected = selected == 2,
                onClick = { selected = 2 },
                text = { Text(pluralStringResource(R.plurals.syncs_count, syncs.size, syncs.size)) }
            )
        }

        when (selected) {
            0 -> {
                if (alarms.isEmpty()) {
                    EmptyListMessage(R.string.no_alarm_data)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alarms, key = { "${it.uid}_${it.tag}" }) { alarm ->
                            AlarmCard(alarm)
                        }
                    }
                }
            }
            1 -> {
                if (jobs.isEmpty()) {
                    EmptyListMessage(R.string.no_job_data)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(jobs, key = { "${it.uid}_${it.jobName}" }) { job ->
                            JobCard(job)
                        }
                    }
                }
            }
            2 -> {
                if (syncs.isEmpty()) {
                    EmptyListMessage(R.string.no_sync_data)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(syncs, key = { "${it.uid}_${it.authority}" }) { sync ->
                            SyncCard(sync)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(alarm: BatteryStatsParser.AlarmStats) {
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(alarm.tag, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rememberAppLabelLine(alarm.packageName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn(R.string.count, "${alarm.count}")
                StatColumn(R.string.wakeups, "${alarm.wakeups}")
                StatColumn(R.string.time, formatDuration(alarm.totalTimeMs))
            }
        }
    }
}

@Composable
private fun JobCard(job: BatteryStatsParser.JobStats) {
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(job.jobName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rememberAppLabelLine(job.packageName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn(R.string.count, "${job.count}")
                StatColumn(R.string.total_time, formatDuration(job.totalTimeMs))
            }
        }
    }
}

@Composable
private fun SyncCard(sync: BatteryStatsParser.SyncStats) {
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(sync.authority, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rememberAppLabelLine(sync.packageName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn(R.string.count, "${sync.count}")
                StatColumn(R.string.total_time, formatDuration(sync.totalTimeMs))
            }
        }
    }
}

@Composable
private fun SystemTab(
    snapshot: BatteryStatsParser.FullSnapshot?,
    deviceIdle: BatteryStatsParser.DeviceIdleInfo?,
    powerManager: BatteryStatsParser.PowerManagerInfo?
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatsCard(titleRes = R.string.process_statistics, icon = Icons.Outlined.Memory) {
                if (snapshot?.processStats.isNullOrEmpty()) {
                    Text(stringResource(R.string.no_process_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    snapshot.processStats.take(20).forEach { proc ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val procLabel = rememberAppLabel(proc.processName)
                            if (procLabel != null) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        procLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        proc.processName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    proc.processName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                formatDuration(proc.userTimeMs + proc.systemTimeMs),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            StatsCard(titleRes = R.string.sensor_usage, icon = Icons.Outlined.Sensors) {
                if (snapshot?.sensors.isNullOrEmpty()) {
                    Text(stringResource(R.string.no_sensor_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    snapshot.sensors.take(15).forEach { sensor ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {

                                Text(
                                    sensor.sensorName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    rememberAppLabelLine(sensor.packageName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                formatDuration(sensor.totalTimeMs),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            StatsCard(titleRes = R.string.doze_whitelist, icon = Icons.Outlined.BatteryChargingFull) {
                if (deviceIdle == null) {
                    Text(stringResource(R.string.no_whitelist), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        stringResource(R.string.whitelisted_apps, deviceIdle.whitelistedApps.size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))

                    if (deviceIdle.whitelistedApps.isNotEmpty()) {
                        deviceIdle.whitelistedApps.take(10).forEach { pkg ->
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (deviceIdle.whitelistedApps.size > 10) {
                            val more = deviceIdle.whitelistedApps.size - 10
                            Text(
                                pluralStringResource(R.plurals.and_more_count, more, more),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (deviceIdle.tempWhitelistedApps.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.temporarily_whitelisted, deviceIdle.tempWhitelistedApps.size),
                            style = MaterialTheme.typography.titleSmall
                        )
                        deviceIdle.tempWhitelistedApps.take(5).forEach { pkg ->
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            StatsCard(titleRes = R.string.suspend_blockers, icon = Icons.Outlined.Block) {
                if (powerManager?.suspendBlockers.isNullOrEmpty()) {
                    Text(stringResource(R.string.no_suspend_blockers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    powerManager.suspendBlockers.forEach { blocker ->
                        Text(
                            blocker,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    border: BorderStroke? = BorderStroke(1.dp, dynamicBorderColor(alpha = 0.28f, tintFraction = 0.35f)),
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadiusDp = 18.dp,
        fallbackColor = dynamicSurfaceColor(alpha = 0.72f, tintFraction = 0.18f),
        border = border,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
private fun StatsCard(
    titleRes: Int,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    FrostedCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun StatRow(labelRes: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatColumn(labelRes: Int, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyListMessage(messageRes: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(messageRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(ms: Long): String {
    if (ms < 1000) return "${ms}ms"
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB", gb)
}


@Composable
fun BatterySettingsTab(vm: BatteryStatsViewModel) {
    val autoResetChargingEnabled by vm.autoResetChargingEnabled.collectAsStateWithLifecycle()
    val autoResetPercent by vm.autoResetPercent.collectAsStateWithLifecycle()
    val autoResetRebootEnabled by vm.autoResetRebootEnabled.collectAsStateWithLifecycle()
    val notifShowRates by vm.notifShowRates.collectAsStateWithLifecycle()
    val notifShowDeepSleep by vm.notifShowDeepSleep.collectAsStateWithLifecycle()
    val notifShowActiveIdle by vm.notifShowActiveIdle.collectAsStateWithLifecycle()
    val notifShowTotalAvg by vm.notifShowTotalAvg.collectAsStateWithLifecycle()
    val notifShowTemperature by vm.notifShowTemperature.collectAsStateWithLifecycle()
    val notifShowNetwork by vm.notifShowNetwork.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FrostedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.battery_auto_reset_title), fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.battery_reset_on_charging_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.battery_reset_on_charging_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoResetChargingEnabled,
                            onCheckedChange = { vm.setAutoResetChargingEnabled(it) },
                            modifier = Modifier.scale(0.80f)
                        )
                    }

                    if (autoResetChargingEnabled) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.battery_reset_target_percent, autoResetPercent), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(
                                    if (autoResetPercent >= 100) stringResource(R.string.battery_reset_full_charge)
                                    else stringResource(R.string.battery_reset_target_label, autoResetPercent),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = autoResetPercent.toFloat(),
                                onValueChange = { vm.setAutoResetPercent(it.toInt()) },
                                valueRange = 50f..100f,
                                steps = 49
                            )
                        }
                    }

                    HorizontalDivider(color = dynamicBorderColor(alpha = 0.20f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.battery_reset_on_reboot_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (autoResetRebootEnabled) stringResource(R.string.battery_reset_on_reboot_enabled_desc)
                                else stringResource(R.string.battery_reset_on_reboot_disabled_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoResetRebootEnabled,
                            onCheckedChange = { vm.setAutoResetRebootEnabled(it) },
                            modifier = Modifier.scale(0.80f)
                        )
                    }

                    HorizontalDivider(color = dynamicBorderColor(alpha = 0.20f))

                    Text(stringResource(R.string.notif_customize_title), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.notif_opt_rates), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifShowRates, onCheckedChange = { vm.setNotifShowRates(it) }, modifier = Modifier.scale(0.80f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.notif_opt_deep_sleep), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifShowDeepSleep, onCheckedChange = { vm.setNotifShowDeepSleep(it) }, modifier = Modifier.scale(0.80f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.notif_opt_active_idle), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifShowActiveIdle, onCheckedChange = { vm.setNotifShowActiveIdle(it) }, modifier = Modifier.scale(0.80f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.notif_opt_total_avg), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifShowTotalAvg, onCheckedChange = { vm.setNotifShowTotalAvg(it) }, modifier = Modifier.scale(0.80f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.notif_opt_temperature), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifShowTemperature, onCheckedChange = { vm.setNotifShowTemperature(it) }, modifier = Modifier.scale(0.80f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.notif_opt_network), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifShowNetwork, onCheckedChange = { vm.setNotifShowNetwork(it) }, modifier = Modifier.scale(0.80f))
                    }

                }
            }
        }
    }
}
