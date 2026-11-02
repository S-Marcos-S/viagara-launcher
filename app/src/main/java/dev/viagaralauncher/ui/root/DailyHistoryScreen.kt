// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.root

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.history.AppDailyLaunchStat
import dev.viagaralauncher.history.BatteryTimelinePoint
import dev.viagaralauncher.history.DailyBatteryConsumer
import dev.viagaralauncher.history.DailyHistoryRepository
import dev.viagaralauncher.history.DailyUsageRecord
import dev.viagaralauncher.history.HourlyNetUsagePoint
import dev.viagaralauncher.root.AppRootInspector
import dev.viagaralauncher.ui.theme.LocalGlassThemeActive
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import dev.viagaralauncher.ui.theme.glass.GlassCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DailyHistoryScreen(
    allApps: List<AppInfo>,
    onNavigateToBackgroundTab: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { DailyHistoryRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val recordsMap by repository.recordsFlow.collectAsState()
    val isRefreshing by repository.isRefreshing.collectAsState()

    var selectedYearMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var selectedDateKey by remember { mutableStateOf(LocalDate.now().format(DailyHistoryRepository.DATE_FORMATTER)) }

    val daysListState = rememberLazyListState()
    val cardsListState = rememberLazyListState()

    LaunchedEffect(selectedYearMonth) {
        repository.loadMonthRecords(selectedYearMonth.year, selectedYearMonth.monthValue)
    }

    // Days in selected month up to today
    val daysInMonth = remember(selectedYearMonth, recordsMap) {
        val now = LocalDate.now()
        val daysCount = selectedYearMonth.lengthOfMonth()
        val list = mutableListOf<LocalDate>()
        for (day in 1..daysCount) {
            val date = selectedYearMonth.withDayOfMonth(day)
            if (!date.isAfter(now)) {
                list.add(date)
            }
        }
        list.reversed() // Most recent first
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        // Month Header & Navigation Selector
        MonthHeaderRow(
            currentMonth = selectedYearMonth,
            isRefreshing = isRefreshing,
            onPrevMonth = {
                selectedYearMonth = selectedYearMonth.minusMonths(1)
            },
            onNextMonth = {
                val next = selectedYearMonth.plusMonths(1)
                if (!next.isAfter(LocalDate.now().withDayOfMonth(1))) {
                    selectedYearMonth = next
                }
            },
            onRefresh = {
                scope.launch {
                    repository.refreshDay(LocalDate.now())
                    repository.loadMonthRecords(selectedYearMonth.year, selectedYearMonth.monthValue)
                }
            },
            onJumpToToday = {
                selectedYearMonth = LocalDate.now().withDayOfMonth(1)
                selectedDateKey = LocalDate.now().format(DailyHistoryRepository.DATE_FORMATTER)
                scope.launch {
                    daysListState.animateScrollToItem(0)
                    cardsListState.animateScrollToItem(0)
                }
            },
        )

        // Compact Day Capsules Carousel
        LazyRow(
            state = daysListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(daysInMonth, key = { it.toString() }) { date ->
                val dateKey = date.format(DailyHistoryRepository.DATE_FORMATTER)
                val isSelected = dateKey == selectedDateKey
                val record = recordsMap[dateKey]

                DayPillCapsule(
                    date = date,
                    isSelected = isSelected,
                    hasData = record != null && (record.batteryDrainPercent > 0 || record.screenOnTimeMs > 0),
                    onClick = {
                        selectedDateKey = dateKey
                        val targetIndex = daysInMonth.indexOf(date)
                        if (targetIndex >= 0) {
                            scope.launch {
                                cardsListState.animateScrollToItem(targetIndex)
                            }
                        }
                    },
                )
            }
        }

        // Vertical List with full Daily Cards
        LazyColumn(
            state = cardsListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(daysInMonth, key = { it.toString() }) { date ->
                val dateKey = date.format(DailyHistoryRepository.DATE_FORMATTER)
                val record = recordsMap[dateKey] ?: DailyUsageRecord(
                    dateKey = dateKey,
                    timestamp = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                )

                CompactDayCard(
                    date = date,
                    record = record,
                    isSelected = dateKey == selectedDateKey,
                    onNavigateToBackground = onNavigateToBackgroundTab,
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MonthHeaderRow(
    currentMonth: LocalDate,
    isRefreshing: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRefresh: () -> Unit,
    onJumpToToday: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val monthName = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val year = currentMonth.year

    GlassCard(
        fallbackColor = dynamicSurfaceColor(alpha = 0.55f, tintFraction = 0.12f),
        cornerRadiusDp = 14.dp,
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevMonth, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Mês anterior",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Text(
                    text = "$monthName $year",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )

                val canGoNext = !currentMonth.plusMonths(1).isAfter(LocalDate.now().withDayOfMonth(1))
                IconButton(
                    onClick = onNextMonth,
                    enabled = canGoNext,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Próximo mês",
                        tint = if (canGoNext) colorScheme.onSurface else colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Today Button
                Surface(
                    onClick = onJumpToToday,
                    color = colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.history_today_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.size(32.dp),
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
                            contentDescription = stringResource(R.string.refresh),
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPillCapsule(
    date: LocalDate,
    isSelected: Boolean,
    hasData: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isToday = date == LocalDate.now()
    val dayOfMonth = String.format(Locale.US, "%02d", date.dayOfMonth)
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .uppercase(Locale.getDefault()).take(3)

    val bgColor = when {
        isSelected -> colorScheme.primary
        isToday -> colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> dynamicSurfaceColor(alpha = 0.40f, tintFraction = 0.10f)
    }

    val textColor = when {
        isSelected -> colorScheme.onPrimary
        isToday -> colorScheme.primary
        else -> colorScheme.onSurface
    }

    val borderColor = if (isSelected) {
        colorScheme.primary
    } else {
        colorScheme.outline.copy(alpha = 0.15f)
    }

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .width(46.dp)
            .height(54.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = dayOfWeek,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.8f),
            )
            Text(
                text = dayOfMonth,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            if (hasData) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) colorScheme.onPrimary else colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun CompactDayCard(
    date: LocalDate,
    record: DailyUsageRecord,
    isSelected: Boolean,
    onNavigateToBackground: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isToday = date == LocalDate.now()
    var isExpanded by remember { mutableStateOf(false) }

    val formattedDate = remember(date) {
        val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val dayOfMonth = date.dayOfMonth
        val monthShort = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        if (isToday) "Hoje, $dayOfMonth $monthShort" else "$dayName, $dayOfMonth $monthShort"
    }

    val cardBorder = if (isSelected) {
        BorderStroke(1.5.dp, colorScheme.primary.copy(alpha = 0.7f))
    } else {
        BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.12f))
    }

    GlassCard(
        fallbackColor = dynamicSurfaceColor(alpha = 0.52f, tintFraction = 0.14f),
        cornerRadiusDp = 18.dp,
        border = cardBorder,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // Header Row: Date & Fast Metrics Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    if (isToday) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = colorScheme.primary,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "HOJE",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Summary Badge Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (record.batteryDrainPercent > 0) {
                        MetricMiniTag(
                            icon = Icons.Filled.BatteryAlert,
                            text = "${record.batteryDrainPercent}%",
                            color = colorScheme.error,
                        )
                    }
                    if (record.screenOnTimeMs > 0) {
                        MetricMiniTag(
                            icon = Icons.Filled.Schedule,
                            text = formatDuration(record.screenOnTimeMs),
                            color = colorScheme.primary,
                        )
                    }
                    if (record.totalInternetBytes > 0) {
                        MetricMiniTag(
                            icon = Icons.Filled.Wifi,
                            text = AppRootInspector.formatBytes(record.totalInternetBytes),
                            color = colorScheme.secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 2x2 Bento Grid: Ultra-dense and compact
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Row 1: Battery Drain & Screen Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Box 1: Battery Drain & Top Consumers
                    BentoBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.BatteryChargingFull,
                        title = stringResource(R.string.history_day_battery),
                        primaryValue = "${record.batteryDrainPercent}%",
                        secondaryText = if (record.topBatteryConsumers.isNotEmpty()) {
                            record.topBatteryConsumers.take(2).joinToString(" • ") { "${it.appName} ${it.drainPercent.toInt()}%" }
                        } else {
                            stringResource(R.string.history_no_high_drain)
                        },
                        accentColor = colorScheme.error,
                    )

                    // Box 2: Screen Time & Screen Off
                    BentoBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.TouchApp,
                        title = stringResource(R.string.history_day_screen_time),
                        primaryValue = formatDuration(record.screenOnTimeMs),
                        secondaryText = stringResource(R.string.history_screen_off_format, formatDuration(record.screenOffTimeMs)),
                        accentColor = colorScheme.primary,
                    )
                }

                // Compact 24h Battery Usage Line Chart (same compact size as internet chart)
                BatteryUsageTimelineCard(
                    date = date,
                    timelinePoints = record.batteryTimeline,
                )

                // Row 2: Reboots / RAM Average & Top App Launches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Box 3: Reboots & Average RAM
                    BentoBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Memory,
                        title = stringResource(R.string.history_system_ram_title),
                        primaryValue = "${record.averageRamPercent}% RAM",
                        secondaryText = stringResource(R.string.history_reboots_cycles_format, record.totalRebootsAndShutdowns),
                        accentColor = colorScheme.tertiary,
                        extraContent = {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (record.averageRamPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = colorScheme.tertiary,
                                trackColor = colorScheme.surfaceVariant,
                            )
                        },
                    )

                    // Box 4: App Opens / Launches
                    val topApp = record.appLaunches.firstOrNull()
                    val totalOpens = record.appLaunches.sumOf { it.launchCount }
                    BentoBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.RestartAlt,
                        title = stringResource(R.string.history_day_app_launches),
                        primaryValue = stringResource(R.string.history_app_launches_count, totalOpens),
                        secondaryText = if (topApp != null) "${topApp.appName} (${topApp.launchCount}x)" else stringResource(R.string.history_no_apps_opened),
                        accentColor = colorScheme.secondary,
                    )
                }

                // Internet Usage & 24h Hourly Distribution Chart
                InternetUsageHourlyCard(
                    totalWifi = record.totalWifiBytes,
                    totalMobile = record.totalMobileBytes,
                    hourlyPoints = record.hourlyNetUsage,
                )

                // Background anomaly alert badge if any were flagged
                if (record.backgroundAnomaliesCount > 0 || record.deepSleepDisruptionsCount > 0) {
                    Surface(
                        onClick = onNavigateToBackground,
                        color = colorScheme.errorContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.history_anomalies_summary_format,
                                    record.backgroundAnomaliesCount,
                                    record.deepSleepDisruptionsCount
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            // Expandable breakdown toggle (App launch list & full consumers)
            if (record.appLaunches.isNotEmpty() || record.topBatteryConsumers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isExpanded) "Ocultar detalhes de apps" else "Ver lista completa de apps abertos (${record.appLaunches.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        record.appLaunches.take(20).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.screenTimeMs > 0) {
                                        Text(
                                            text = formatDuration(item.screenTimeMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.outline,
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                    }
                                    Surface(
                                        color = colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = "${item.launchCount}x",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp),
            )
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

@Composable
private fun BentoBox(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    primaryValue: String,
    secondaryText: String,
    accentColor: Color,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isGlassActive = LocalGlassThemeActive.current

    Surface(
        color = colorScheme.surfaceContainerHigh.copy(alpha = if (isGlassActive) 0.35f else 0.55f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.08f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )

            Text(
                text = secondaryText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            extraContent?.invoke()
        }
    }
}

@Composable
private fun InternetUsageHourlyCard(
    totalWifi: Long,
    totalMobile: Long,
    hourlyPoints: List<HourlyNetUsagePoint>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isGlassActive = LocalGlassThemeActive.current
    var selectedHour by remember { mutableIntStateOf(-1) }

    val totalBytes = totalWifi + totalMobile
    val maxHourlyBytes = remember(hourlyPoints) {
        hourlyPoints.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1024L) ?: 1024L
    }

    Surface(
        color = colorScheme.surfaceContainerHigh.copy(alpha = if (isGlassActive) 0.35f else 0.55f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.NetworkCheck,
                        contentDescription = null,
                        tint = colorScheme.secondary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.history_day_network),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                }

                Text(
                    text = stringResource(R.string.history_network_wifi_mobile, AppRootInspector.formatBytes(totalWifi), AppRootInspector.formatBytes(totalMobile)),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colorScheme.outline,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Tooltip if user tapped an hour
            if (selectedHour in 0..23) {
                val point = hourlyPoints.getOrNull(selectedHour)
                if (point != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${point.label}: ${AppRootInspector.formatBytes(point.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Wi-Fi: ${AppRootInspector.formatBytes(point.wifiBytes)} | Móvel: ${AppRootInspector.formatBytes(point.mobileBytes)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Compact 24-Hour Sparkline / Bar Chart (Canvas)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                val primaryColor = colorScheme.primary
                val selectedColor = colorScheme.error
                val trackColor = colorScheme.surfaceVariant.copy(alpha = 0.4f)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(hourlyPoints) {
                            detectTapGestures { offset ->
                                val barWidthTotal = size.width / 24f
                                val tappedIndex = (offset.x / barWidthTotal).toInt().coerceIn(0, 23)
                                selectedHour = if (selectedHour == tappedIndex) -1 else tappedIndex
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val barWidth = (w / 24f) * 0.75f
                    val gap = (w / 24f) * 0.25f

                    for (i in 0 until 24) {
                        val pt = hourlyPoints.getOrNull(i)
                        val bytes = pt?.totalBytes ?: 0L
                        val ratio = (bytes.toFloat() / maxHourlyBytes.toFloat()).coerceIn(0f, 1f)
                        val barHeight = (ratio * (h - 8f)).coerceAtLeast(3f)

                        val left = i * (barWidth + gap) + gap / 2f
                        val top = h - barHeight

                        val isHighlighted = i == selectedHour
                        val barColor = if (isHighlighted) selectedColor else primaryColor.copy(alpha = if (bytes > 0) 0.85f else 0.25f)

                        // Draw background slot
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )

                        // Draw active bar
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                    }
                }
            }

            // Hour labels (00h, 06h, 12h, 18h, 23h)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "00h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
                Text(text = "06h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
                Text(text = "12h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
                Text(text = "18h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
                Text(text = "23h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
            }
        }
    }
}

@Composable
private fun BatteryUsageTimelineCard(
    date: LocalDate,
    timelinePoints: List<BatteryTimelinePoint>,
) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedPointIndex by remember(timelinePoints) { mutableIntStateOf(-1) }

    val colorScreenOn = Color(0xFF4CAF50)
    val colorDeepSleep = Color(0xFF29B6F6)
    val colorOff = Color(0xFFEF5350)

    val zoneId = remember { ZoneId.systemDefault() }
    val startOfDay = remember(date) { date.atStartOfDay(zoneId).toInstant().toEpochMilli() }
    val endOfDay = remember(date) { date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L }

    GlassCard(
        fallbackColor = dynamicSurfaceColor(alpha = 0.50f, tintFraction = 0.08f),
        cornerRadiusDp = 12.dp,
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header: Title & Compact Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.BatteryChargingFull,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.history_day_battery_chart),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                }

                // Legend dots
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colorScreenOn))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.history_battery_legend_screen_on),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colorScheme.outline,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colorDeepSleep))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.history_battery_legend_deep_sleep),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colorScheme.outline,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colorOff))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.history_battery_legend_off),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colorScheme.outline,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tooltip if user tapped/scrubbed
            if (selectedPointIndex in timelinePoints.indices) {
                val pt = timelinePoints[selectedPointIndex]
                val timeStr = remember(pt.timestamp) {
                    DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId).format(Instant.ofEpochMilli(pt.timestamp))
                }
                val stateText = when (pt.state) {
                    BatteryTimelinePoint.STATE_SCREEN_ON -> stringResource(R.string.history_battery_state_screen_on)
                    BatteryTimelinePoint.STATE_OFF -> stringResource(R.string.history_battery_state_off)
                    else -> stringResource(R.string.history_battery_state_deep_sleep)
                }
                val stateColor = when (pt.state) {
                    BatteryTimelinePoint.STATE_SCREEN_ON -> colorScreenOn
                    BatteryTimelinePoint.STATE_OFF -> colorOff
                    else -> colorDeepSleep
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_battery_tooltip_format, timeStr, pt.batteryLevel, stateText),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimaryContainer,
                    )
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(stateColor))
                }
                Spacer(Modifier.height(6.dp))
            }

            // Compact 24-Hour Line Chart Canvas (Height 52.dp - same as internet chart)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                val trackColor = colorScheme.surfaceVariant.copy(alpha = 0.35f)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(timelinePoints) {
                            detectTapGestures { offset ->
                                if (timelinePoints.isNotEmpty()) {
                                    val w = size.width
                                    val tapRatio = (offset.x / w).coerceIn(0f, 1f)
                                    val tapTimestamp = startOfDay + (tapRatio * (endOfDay - startOfDay)).toLong()
                                    var closestIndex = 0
                                    var minDiff = Long.MAX_VALUE
                                    for (i in timelinePoints.indices) {
                                        val diff = kotlin.math.abs(timelinePoints[i].timestamp - tapTimestamp)
                                        if (diff < minDiff) {
                                            minDiff = diff
                                            closestIndex = i
                                        }
                                    }
                                    selectedPointIndex = if (selectedPointIndex == closestIndex) -1 else closestIndex
                                }
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val verticalPadding = 6.dp.toPx()
                    val chartHeight = h - (verticalPadding * 2)

                    // Draw subtle horizontal guide lines (0%, 50%, 100%)
                    val y100 = verticalPadding
                    val y50 = verticalPadding + (chartHeight / 2f)
                    val y0 = verticalPadding + chartHeight

                    drawLine(
                        color = trackColor,
                        start = Offset(0f, y100),
                        end = Offset(w, y100),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = trackColor.copy(alpha = 0.2f),
                        start = Offset(0f, y50),
                        end = Offset(w, y50),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = trackColor,
                        start = Offset(0f, y0),
                        end = Offset(w, y0),
                        strokeWidth = 1.dp.toPx(),
                    )

                    if (timelinePoints.size >= 2) {
                        val duration = (endOfDay - startOfDay).toFloat().coerceAtLeast(1f)

                        fun pointOffset(pt: BatteryTimelinePoint): Offset {
                            val x = (((pt.timestamp - startOfDay).toFloat() / duration).coerceIn(0f, 1f)) * w
                            val y = verticalPadding + ((100f - pt.batteryLevel.toFloat().coerceIn(0f, 100f)) / 100f) * chartHeight
                            return Offset(x, y)
                        }

                        // Draw colored segments
                        for (i in 0 until timelinePoints.size - 1) {
                            val p1 = timelinePoints[i]
                            val p2 = timelinePoints[i + 1]
                            val off1 = pointOffset(p1)
                            val off2 = pointOffset(p2)

                            val segColor = when (p1.state) {
                                BatteryTimelinePoint.STATE_SCREEN_ON -> colorScreenOn
                                BatteryTimelinePoint.STATE_OFF -> colorOff
                                else -> colorDeepSleep
                            }

                            drawLine(
                                color = segColor,
                                start = off1,
                                end = off2,
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }

                        // Highlight selected point if any
                        if (selectedPointIndex in timelinePoints.indices) {
                            val selPt = timelinePoints[selectedPointIndex]
                            val selOff = pointOffset(selPt)
                            val selColor = when (selPt.state) {
                                BatteryTimelinePoint.STATE_SCREEN_ON -> colorScreenOn
                                BatteryTimelinePoint.STATE_OFF -> colorOff
                                else -> colorDeepSleep
                            }

                            // Vertical indicator line
                            drawLine(
                                color = selColor.copy(alpha = 0.5f),
                                start = Offset(selOff.x, verticalPadding),
                                end = Offset(selOff.x, verticalPadding + chartHeight),
                                strokeWidth = 1.dp.toPx(),
                            )

                            // Outer ring
                            drawCircle(
                                color = selColor,
                                radius = 4.5.dp.toPx(),
                                center = selOff,
                            )
                            // Inner dot
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = selOff,
                            )
                        }
                    }
                }
            }

            // Hour labels (00h, 06h, 12h, 18h, 23h)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "00h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
                Text(text = "06h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
                Text(text = "12h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
                Text(text = "18h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
                Text(text = "23h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colorScheme.outline)
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val seconds = millis / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val remMinutes = minutes % 60L

    return when {
        hours > 0 -> "${hours}h ${remMinutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
