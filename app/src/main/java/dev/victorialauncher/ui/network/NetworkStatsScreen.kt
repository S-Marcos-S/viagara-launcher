// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.network

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.R
import dev.victorialauncher.network.AppUsageInfo
import dev.victorialauncher.network.AppUsageSegment
import dev.victorialauncher.network.DataPoint
import dev.victorialauncher.network.NetworkStatsRepository
import dev.victorialauncher.network.StatsPeriod
import dev.victorialauncher.network.StatsUiState
import dev.victorialauncher.network.TimePeriodStats
import dev.victorialauncher.root.AppRootInspector
import dev.victorialauncher.ui.theme.dynamicSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

@Composable
fun NetworkStatsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { NetworkStatsRepository(context) }

    var selectedPeriod by remember { mutableStateOf(StatsPeriod.TODAY) }
    var uiState by remember { mutableStateOf<StatsUiState>(StatsUiState.Loading) }
    var showAppBreakdown by remember { mutableStateOf(false) }

    fun loadData(period: StatsPeriod) {
        scope.launch {
            uiState = StatsUiState.Loading
            val hasPerm = repository.hasUsageStatsPermission()
            if (!hasPerm) {
                uiState = StatsUiState.NoPermission
                return@launch
            }

            try {
                val (startTime, endTime) = getRangeForPeriod(period)
                val stats = repository.getStatsForPeriod(startTime, endTime)
                uiState = StatsUiState.Success(stats)
            } catch (e: Exception) {
                uiState = StatsUiState.Error(e.message ?: "Erro ao carregar telemetria de rede")
            }
        }
    }

    LaunchedEffect(selectedPeriod) {
        loadData(selectedPeriod)
    }

    Scaffold(
        containerColor = dynamicSurfaceColor(),
        topBar = {
            CleanNetworkTopBar(
                title = "Monitor de Rede por App",
                onNavigateBack = onNavigateBack,
                onRefresh = { loadData(selectedPeriod) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Seletor de período (Hoje, 7 dias, 30 dias)
                PeriodSelectorPills(
                    selectedPeriod = selectedPeriod,
                    onSelect = { selectedPeriod = it },
                )

                when (val state = uiState) {
                    is StatsUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is StatsUiState.NoPermission -> {
                        PermissionRequestCard(
                            onGrantRoot = {
                                scope.launch {
                                    AppRootInspector.runSuCommand(
                                        "pm grant ${context.packageName} android.permission.PACKAGE_USAGE_STATS 2>/dev/null; " +
                                            "appops set ${context.packageName} GET_USAGE_STATS allow 2>/dev/null",
                                    )
                                    loadData(selectedPeriod)
                                }
                            },
                            onOpenSettings = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                        )
                    }

                    is StatsUiState.Success -> {
                        StatsContentList(
                            stats = state.stats,
                            showAppBreakdown = showAppBreakdown,
                            onToggleBreakdown = { showAppBreakdown = it },
                        )
                    }

                    is StatsUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanNetworkTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        color = dynamicSurfaceColor(),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
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

            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Atualizar",
                    tint = colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PeriodSelectorPills(
    selectedPeriod: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainerHigh)
            .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val periods = listOf(
            StatsPeriod.TODAY to "Hoje",
            StatsPeriod.LAST_7_DAYS to "7 dias",
            StatsPeriod.LAST_30_DAYS to "30 dias",
        )

        periods.forEach { (period, label) ->
            val isSelected = selectedPeriod == period
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (isSelected) colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(period) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun StatsContentList(
    stats: TimePeriodStats,
    showAppBreakdown: Boolean,
    onToggleBreakdown: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            // Totais de Rede (Dados Móveis e Wi-Fi)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryCard(
                    title = "Dados Móveis",
                    value = formatBytes(stats.totalMobile),
                    icon = Icons.Filled.CloudDownload,
                    accentColor = Color(0xFF06B6D4), // Cyan
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "Rede Wi-Fi",
                    value = formatBytes(stats.totalWifi),
                    icon = Icons.Filled.Wifi,
                    accentColor = Color(0xFF8B5CF6), // Violet
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            // Gráfico de Consumo
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                color = colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Histórico de Consumo",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Por App",
                                fontSize = 12.sp,
                                color = colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = showAppBreakdown,
                                onCheckedChange = onToggleBreakdown,
                                modifier = Modifier.height(28.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (showAppBreakdown) {
                        InteractiveAppBreakdownChart(stats = stats)
                    } else {
                        NativeGroupedBarChart(stats = stats)
                    }
                }
            }
        }

        item {
            Text(
                text = "Aplicativos que mais consumiram",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        items(stats.topApps) { appInfo ->
            val maxBytes = stats.topApps.firstOrNull()?.totalData?.coerceAtLeast(1L) ?: 1L
            AppNetworkUsageItem(appInfo = appInfo, maxTotalBytes = maxBytes)
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
        color = colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun NativeGroupedBarChart(stats: TimePeriodStats) {
    val colorScheme = MaterialTheme.colorScheme
    val mobileColor = Color(0xFF06B6D4) // Cyan
    val wifiColor = Color(0xFF8B5CF6) // Violet

    val points = stats.dataPoints
    val maxBytes = points.maxOfOrNull { maxOf(it.mobileData, it.wifiData) }?.toFloat()?.coerceAtLeast(1024f * 1024f) ?: (1024f * 1024f)
    val maxMb = maxBytes / (1024f * 1024f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            // Eixo Y
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(formatMb(maxMb), fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.55f))
                Text(formatMb(maxMb / 2f), fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.55f))
                Text("0 MB", fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.55f))
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 20.dp),
                color = colorScheme.outline.copy(alpha = 0.15f),
            )

            // Barras com rolagem horizontal
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                points.forEach { point ->
                    val mobileHeightRatio = (point.mobileData.toFloat() / maxBytes).coerceIn(0f, 1f)
                    val wifiHeightRatio = (point.wifiData.toFloat() / maxBytes).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            // Barra Dados Móveis
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(mobileHeightRatio.coerceAtLeast(0.02f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(mobileColor),
                            )
                            // Barra Wi-Fi
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(wifiHeightRatio.coerceAtLeast(0.02f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(wifiColor),
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = point.label,
                            fontSize = 10.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.65f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Legenda
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(mobileColor, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text("Dados Móveis", fontSize = 11.5.sp, color = colorScheme.onSurface.copy(alpha = 0.75f))

            Spacer(Modifier.width(16.dp))

            Box(Modifier.size(10.dp).background(wifiColor, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text("Rede Wi-Fi", fontSize = 11.5.sp, color = colorScheme.onSurface.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun InteractiveAppBreakdownChart(stats: TimePeriodStats) {
    val colorScheme = MaterialTheme.colorScheme
    val points = stats.dataPoints
    val maxBytes = points.maxOfOrNull { it.mobileData + it.wifiData }?.toFloat()?.coerceAtLeast(1024f * 1024f) ?: (1024f * 1024f)
    var selectedPoint by remember { mutableStateOf<DataPoint?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                val maxMb = maxBytes / (1024f * 1024f)
                Text(formatMb(maxMb), fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.55f))
                Text(formatMb(maxMb / 2f), fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.55f))
                Text("0 MB", fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.55f))
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 20.dp),
                color = colorScheme.outline.copy(alpha = 0.15f),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                points.forEach { point ->
                    val segmentsSum = point.appSegments.sumOf { it.bytes }.toFloat()
                    val barHeightRatio = (segmentsSum / maxBytes).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(36.dp)
                            .clickable { selectedPoint = point },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(barHeightRatio.coerceAtLeast(0.04f))
                                    .clip(RoundedCornerShape(6.dp)),
                            ) {
                                point.appSegments.forEach { segment ->
                                    val segWeight = segment.bytes.toFloat().coerceAtLeast(1f)
                                    Box(
                                        modifier = Modifier
                                            .weight(segWeight)
                                            .fillMaxWidth()
                                            .background(Color(segment.color)),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = point.label,
                            fontSize = 10.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.65f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Toque em uma coluna para ver quais apps consumiram naquele intervalo.",
            fontSize = 11.sp,
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    selectedPoint?.let { point ->
        ZoomColumnDetailsDialog(
            dataPoint = point,
            onDismiss = { selectedPoint = null },
        )
    }
}

@Composable
private fun ZoomColumnDetailsDialog(
    dataPoint: DataPoint,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        title = {
            Text("Detalhes de ${dataPoint.label}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Total no intervalo:",
                        fontSize = 13.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                    Text(
                        text = formatBytes(dataPoint.mobileData + dataPoint.wifiData),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.1f))
                Spacer(Modifier.height(10.dp))

                if (dataPoint.appSegments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nenhum consumo expressivo registrado neste intervalo.",
                            fontSize = 13.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(dataPoint.appSegments) { segment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(segment.color), CircleShape),
                                )
                                Spacer(Modifier.width(8.dp))
                                segment.icon?.let {
                                    Image(
                                        bitmap = it.toBitmap(width = 48, height = 48).asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = segment.appName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = formatBytes(segment.bytes),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun AppNetworkUsageItem(
    appInfo: AppUsageInfo,
    maxTotalBytes: Long,
) {
    val colorScheme = MaterialTheme.colorScheme
    val progress = (appInfo.totalData.toFloat() / maxTotalBytes.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
        color = colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (appInfo.icon != null) {
                    Image(
                        bitmap = appInfo.icon.toBitmap(width = 64, height = 64).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = appInfo.appName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Móvel: ${formatBytes(appInfo.mobileData)}  ·  Wi-Fi: ${formatBytes(appInfo.wifiData)}",
                        fontSize = 11.5.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = formatBytes(appInfo.totalData),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF06B6D4), // Cyan
                trackColor = colorScheme.outline.copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
private fun PermissionRequestCard(
    onGrantRoot: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            tint = colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Permissão de Acesso ao Uso",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Para ler o consumo de internet detalhado por cada aplicativo, o Android exige a permissão de Acesso ao Uso.",
            fontSize = 13.5.sp,
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onGrantRoot,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text("Conceder via Root")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text("Abrir Configurações do Android")
        }
    }
}

private fun getRangeForPeriod(period: StatsPeriod): Pair<Long, Long> {
    val calendar = Calendar.getInstance()
    val end = calendar.timeInMillis

    when (period) {
        StatsPeriod.TODAY -> {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        StatsPeriod.LAST_7_DAYS -> {
            calendar.add(Calendar.DAY_OF_YEAR, -7)
        }
        StatsPeriod.LAST_30_DAYS -> {
            calendar.add(Calendar.DAY_OF_YEAR, -30)
        }
    }
    return calendar.timeInMillis to end
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatMb(mb: Float): String {
    return if (mb >= 1024f) {
        String.format(Locale.US, "%.1f GB", mb / 1024f)
    } else {
        String.format(Locale.US, "%.0f MB", mb)
    }
}
