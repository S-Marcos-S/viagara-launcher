// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.battery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import dev.viagaralauncher.battery.BatteryCycleRecord
import dev.viagaralauncher.battery.BatteryCycleRepository
import dev.viagaralauncher.battery.CycleAppUsage
import dev.viagaralauncher.battery.CycleTelemetryPoint
import dev.viagaralauncher.battery.CycleType
import dev.viagaralauncher.battery.SpeedUnit
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.ui.common.AppIcon
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import dev.viagaralauncher.ui.theme.glass.GlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CycleFilter {
    ALL,
    CHARGE,
    DISCHARGE,
}

@Composable
fun BatteryCyclesScreen(
    allApps: List<AppInfo>,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { BatteryCycleRepository.getInstance(context) }
    val cycles by repository.cyclesFlow.collectAsState()
    val hardwareCycles by repository.hardwareCycleCount.collectAsState()

    var selectedFilter by remember { mutableStateOf(CycleFilter.ALL) }
    var expandedCycleId by remember { mutableStateOf<String?>(null) }

    val filteredCycles = remember(cycles, selectedFilter) {
        when (selectedFilter) {
            CycleFilter.ALL -> cycles
            CycleFilter.CHARGE -> cycles.filter { it.type == CycleType.CHARGE }
            CycleFilter.DISCHARGE -> cycles.filter { it.type == CycleType.DISCHARGE }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CycleHeaderBanner(
                hardwareCycles = hardwareCycles,
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilter = it },
                activeCycle = cycles.firstOrNull { it.isActive },
            )
        }

        items(filteredCycles, key = { it.id }) { cycle ->
            val isExpanded = expandedCycleId == cycle.id || (expandedCycleId == null && cycle.isActive)
            CycleItemCard(
                cycle = cycle,
                allApps = allApps,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedCycleId = if (isExpanded) "" else cycle.id
                },
            )
        }
    }
}

@Composable
private fun CycleHeaderBanner(
    hardwareCycles: Int?,
    selectedFilter: CycleFilter,
    onSelectFilter: (CycleFilter) -> Unit,
    activeCycle: BatteryCycleRecord?,
) {
    val colorScheme = MaterialTheme.colorScheme

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadiusDp = 14.dp,
        fallbackColor = dynamicSurfaceColor(alpha = 0.65f, tintFraction = 0.16f),
        border = BorderStroke(1.dp, dynamicBorderColor(alpha = 0.20f, tintFraction = 0.25f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.cycles_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                }

                if (hardwareCycles != null && hardwareCycles > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.primaryContainer.copy(alpha = 0.35f))
                            .border(1.dp, colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${hardwareCycles} ${stringResource(R.string.cycles_cycles_spent).lowercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Filter pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CycleFilterPill(
                    title = stringResource(R.string.cycles_filter_all),
                    selected = selectedFilter == CycleFilter.ALL,
                    onClick = { onSelectFilter(CycleFilter.ALL) },
                    modifier = Modifier.weight(1f),
                )
                CycleFilterPill(
                    title = stringResource(R.string.cycles_filter_discharge),
                    selected = selectedFilter == CycleFilter.DISCHARGE,
                    onClick = { onSelectFilter(CycleFilter.DISCHARGE) },
                    modifier = Modifier.weight(1f),
                )
                CycleFilterPill(
                    title = stringResource(R.string.cycles_filter_charge),
                    selected = selectedFilter == CycleFilter.CHARGE,
                    onClick = { onSelectFilter(CycleFilter.CHARGE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CycleFilterPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
        label = "pillBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.75f),
        label = "pillContent",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

@Composable
private fun CycleItemCard(
    cycle: BatteryCycleRecord,
    allApps: List<AppInfo>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val isCharge = cycle.type == CycleType.CHARGE
    val accentColor = if (isCharge) Color(0xFF10B981) else Color(0xFFF59E0B)
    val colorScheme = MaterialTheme.colorScheme

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadiusDp = 14.dp,
        fallbackColor = dynamicSurfaceColor(alpha = 0.70f, tintFraction = 0.18f),
        border = BorderStroke(1.dp, dynamicBorderColor(alpha = 0.22f, tintFraction = 0.25f)),
        onClick = onToggleExpand,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // Header summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isCharge) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryAlert,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isCharge) stringResource(R.string.cycles_charge) else stringResource(R.string.cycles_discharge),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )
                        if (cycle.isActive) {
                            Spacer(Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.20f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.cycles_active),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                )
                            }
                        }
                    }

                    Text(
                        text = formatCycleTimeRange(cycle.startTimeMs, cycle.endTimeMs, cycle.isActive),
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Delta percentage & mAh
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (isCharge) "+" else "-") + "${cycle.batteryDeltaPercent}%",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "${cycle.totalMah.toInt()} mAh • ${formatDurationCompact(cycle.durationMs)}",
                        fontSize = 10.5.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.60f),
                    )
                }

                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp),
                )
            }

            // Expanded detail section
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isCharge) {
                        ChargeCycleDetail(cycle = cycle)
                    } else {
                        DischargeCycleDetail(cycle = cycle, allApps = allApps)
                    }
                }
            }
        }
    }
}

/**
 * Discharge session detail:
 * - Total battery spent (% and mAh)
 * - Discharge graph
 * - Deep sleep and Light sleep drain
 * - Temperature graph
 * - App usage breakdown
 */
@Composable
private fun DischargeCycleDetail(
    cycle: BatteryCycleRecord,
    allApps: List<AppInfo>,
) {
    val colorScheme = MaterialTheme.colorScheme

    // 1. Gasto total & métricas resumidas
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MetricChip(
            label = stringResource(R.string.cycles_total_spent),
            value = "-${cycle.batteryDeltaPercent}% • ${cycle.totalMah.toInt()} mAh",
            color = Color(0xFFF59E0B),
            modifier = Modifier.weight(1.2f),
        )
        MetricChip(
            label = stringResource(R.string.task_manager_cpu_speed),
            value = "${cycle.averageDrainOrChargeRateMa.toInt()} mA/h",
            color = colorScheme.primary,
            modifier = Modifier.weight(0.9f),
        )
        MetricChip(
            label = stringResource(R.string.task_manager_cpu_uptime),
            value = formatDurationCompact(cycle.durationMs),
            color = colorScheme.secondary,
            modifier = Modifier.weight(0.9f),
        )
    }

    // 2. Gráfico de descarga
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.cycles_discharge_graph),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Text(
                text = "${cycle.startBatteryLevel}% → ${cycle.endBatteryLevel}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF59E0B),
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(4.dp))
        DischargeCurveCanvas(
            points = cycle.telemetryPoints,
            startLevel = cycle.startBatteryLevel,
            endLevel = cycle.endBatteryLevel,
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp),
        )
    }

    // 3. Gasto em sono profundo e em sono leve (+ tela ligada)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SleepStateCard(
                icon = Icons.Filled.Bedtime,
                title = stringResource(R.string.cycles_deep_sleep),
                durationMs = cycle.deepSleepTimeMs,
                drainMah = cycle.deepSleepDrainMah,
                totalDrainMah = cycle.totalMah,
                color = Color(0xFF6366F1),
                modifier = Modifier.weight(1f),
            )
            SleepStateCard(
                icon = Icons.Filled.NightsStay,
                title = stringResource(R.string.cycles_light_sleep),
                durationMs = cycle.lightSleepTimeMs,
                drainMah = cycle.lightSleepDrainMah,
                totalDrainMah = cycle.totalMah,
                color = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
            )
            SleepStateCard(
                icon = Icons.Filled.Smartphone,
                title = stringResource(R.string.cycles_screen_on),
                durationMs = cycle.screenOnTimeMs,
                drainMah = cycle.screenOnDrainMah,
                totalDrainMah = cycle.totalMah,
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
            )
        }
    }

    // 4. Gráfico de temperatura durante a descarga
    Column(modifier = Modifier.fillMaxWidth()) {
        val minTemp = cycle.telemetryPoints.minOfOrNull { it.batteryTempC } ?: 28f
        val maxTemp = cycle.telemetryPoints.maxOfOrNull { it.batteryTempC } ?: 34f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.cycles_temp_graph),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Text(
                text = String.format(Locale.US, "%.1f°C ~ %.1f°C", minTemp, maxTemp),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444),
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(4.dp))
        TemperatureCurveCanvas(
            points = cycle.telemetryPoints,
            color = Color(0xFFEF4444),
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
        )
    }

    // 5. Uso por aplicativo
    if (cycle.appUsages.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.cycles_app_usage),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(4.dp))
            AppUsageList(usages = cycle.appUsages, allApps = allApps)
        }
    }
}

/**
 * Recharge session detail:
 * - Percentage charged
 * - mAh charged
 * - Speed graph with screen on vs off + unit picker: W, V, A
 * - Temperature graph during recharge
 * - Battery cycles spent with this charge
 */
@Composable
private fun ChargeCycleDetail(cycle: BatteryCycleRecord) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedUnit by remember { mutableStateOf(SpeedUnit.WATTS) }

    // 1. Porcentagem, mAh e Ciclos Gastos
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MetricChip(
            label = stringResource(R.string.cycles_charged_amount),
            value = "+${cycle.batteryDeltaPercent}%",
            color = Color(0xFF10B981),
            modifier = Modifier.weight(1f),
        )
        MetricChip(
            label = stringResource(R.string.task_manager_battery_title),
            value = "+${cycle.totalMah.toInt()} mAh",
            color = colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        MetricChip(
            label = stringResource(R.string.cycles_cycles_spent),
            value = String.format(Locale.US, "%.2f", cycle.totalCyclesDelta),
            color = Color(0xFF38BDF8),
            modifier = Modifier.weight(1f),
        )
    }

    // 2. Gráfico de velocidade de recarga com tela ligada e desligada (W, V, A)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.cycles_speed_graph),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface.copy(alpha = 0.75f),
            )

            // Selector W, V, A
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                UnitPickerButton(title = "W", selected = selectedUnit == SpeedUnit.WATTS) {
                    selectedUnit = SpeedUnit.WATTS
                }
                UnitPickerButton(title = "V", selected = selectedUnit == SpeedUnit.VOLTS) {
                    selectedUnit = SpeedUnit.VOLTS
                }
                UnitPickerButton(title = "A", selected = selectedUnit == SpeedUnit.AMPERES) {
                    selectedUnit = SpeedUnit.AMPERES
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        ChargingSpeedCanvas(
            points = cycle.telemetryPoints,
            unit = selectedUnit,
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
        )
    }

    // 3. Gráfico de temperatura durante a recarga
    Column(modifier = Modifier.fillMaxWidth()) {
        val minTemp = cycle.telemetryPoints.minOfOrNull { it.batteryTempC } ?: 28f
        val peakTemp = cycle.telemetryPoints.maxOfOrNull { it.batteryTempC } ?: 36f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.cycles_temp_graph),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Text(
                text = String.format(Locale.US, "%.1f°C → Pico %.1f°C", minTemp, peakTemp),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF97316),
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(4.dp))
        TemperatureCurveCanvas(
            points = cycle.telemetryPoints,
            color = Color(0xFFF97316),
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.40f))
            .border(1.dp, color.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            Text(
                text = label,
                fontSize = 9.5.sp,
                color = colorScheme.onSurface.copy(alpha = 0.55f),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SleepStateCard(
    icon: ImageVector,
    title: String,
    durationMs: Long,
    drainMah: Double,
    totalDrainMah: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val drainRatio = if (totalDrainMah > 0) (drainMah / totalDrainMah * 100f).toFloat().coerceIn(0f, 100f) else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.35f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = title,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = formatDurationCompact(durationMs),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "${drainMah.toInt()} mAh (${drainRatio.toInt()}%)",
                fontSize = 9.sp,
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun UnitPickerButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor = if (selected) colorScheme.primary else Color.Transparent
    val contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.65f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

@Composable
private fun AppUsageList(
    usages: List<CycleAppUsage>,
    allApps: List<AppInfo>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val topUsages = usages.take(5)
    val maxDrain = topUsages.maxOfOrNull { it.drainMah } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        topUsages.forEach { appUsage ->
            val appInfo = allApps.firstOrNull { it.packageName == appUsage.packageName }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.25f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (appInfo != null) {
                    AppIcon(app = appInfo, sizeDp = 22)
                } else {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = appUsage.appName.take(1).uppercase(Locale.ROOT),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appUsage.appName,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val progress = (appUsage.drainMah / maxDrain).toFloat().coerceIn(0.05f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFF59E0B),
                        trackColor = colorScheme.outline.copy(alpha = 0.12f),
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "${appUsage.drainMah.toInt()} mAh (${appUsage.drainPercent.toInt()}%)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface.copy(alpha = 0.70f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * Canvas bezier curve for battery discharge level (% vs time)
 */
@Composable
private fun DischargeCurveCanvas(
    points: List<CycleTelemetryPoint>,
    startLevel: Int,
    endLevel: Int,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val strokeColor = Color(0xFFF59E0B)
    val gridColor = colorScheme.outline.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.20f))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val width = size.width
            val height = size.height

            // Horizontal grid lines
            for (i in 0..3) {
                val y = height * (i.toFloat() / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                )
            }

            val values = if (points.size >= 2) {
                points.map { it.batteryLevel.toFloat() }
            } else {
                listOf(startLevel.toFloat(), endLevel.toFloat())
            }

            val maxVal = maxOf(values.maxOrNull() ?: 100f, 100f)
            val minVal = 0f
            val stepX = width / (values.size - 1).coerceAtLeast(1)

            val coords = values.mapIndexed { idx, v ->
                val x = idx * stepX
                val norm = ((v - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val y = height - (norm * (height - 8f)) - 4f
                Offset(x, y)
            }

            if (coords.isNotEmpty()) {
                val fillPath = Path().apply {
                    moveTo(coords.first().x, height)
                    lineTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val curr = coords[i]
                        val cx = (prev.x + curr.x) / 2f
                        cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }
                    lineTo(coords.last().x, height)
                    close()
                }

                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val curr = coords[i]
                        val cx = (prev.x + curr.x) / 2f
                        cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            strokeColor.copy(alpha = 0.40f),
                            strokeColor.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                )

                drawPath(
                    path = strokePath,
                    color = strokeColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

/**
 * Canvas curve for charging speed with Screen-On vs Screen-Off distinction
 */
@Composable
private fun ChargingSpeedCanvas(
    points: List<CycleTelemetryPoint>,
    unit: SpeedUnit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val screenOffColor = Color(0xFF10B981) // emerald for screen off (faster)
    val screenOnColor = Color(0xFFF59E0B)  // amber for screen on (throttled)
    val gridColor = colorScheme.outline.copy(alpha = 0.12f)

    val unitSymbol = when (unit) {
        SpeedUnit.WATTS -> "W"
        SpeedUnit.VOLTS -> "V"
        SpeedUnit.AMPERES -> "A"
    }

    val screenOffPoints = points.filter { !it.isScreenOn }
    val screenOnPoints = points.filter { it.isScreenOn }

    fun pointValue(p: CycleTelemetryPoint): Float = when (unit) {
        SpeedUnit.WATTS -> p.powerWatts
        SpeedUnit.VOLTS -> p.voltageV
        SpeedUnit.AMPERES -> p.currentA
    }

    val avgScreenOff = if (screenOffPoints.isNotEmpty()) screenOffPoints.map { pointValue(it) }.average() else 0.0
    val avgScreenOn = if (screenOnPoints.isNotEmpty()) screenOnPoints.map { pointValue(it) }.average() else 0.0

    Column {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.20f))
                .border(1.dp, colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
                val width = size.width
                val height = size.height

                // Horizontal grid lines
                for (i in 0..3) {
                    val y = height * (i.toFloat() / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f,
                    )
                }

                val values = if (points.isNotEmpty()) {
                    points.map { pointValue(it) }
                } else {
                    listOf(15f, 18f)
                }

                val maxVal = maxOf(values.maxOrNull() ?: 1f, 1f)
                val minVal = 0f
                val stepX = width / (values.size - 1).coerceAtLeast(1)

                val coords = values.mapIndexed { idx, v ->
                    val x = idx * stepX
                    val norm = ((v - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    val y = height - (norm * (height - 8f)) - 4f
                    Offset(x, y)
                }

                if (coords.isNotEmpty()) {
                    val fillPath = Path().apply {
                        moveTo(coords.first().x, height)
                        lineTo(coords.first().x, coords.first().y)
                        for (i in 1 until coords.size) {
                            val prev = coords[i - 1]
                            val curr = coords[i]
                            val cx = (prev.x + curr.x) / 2f
                            cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        }
                        lineTo(coords.last().x, height)
                        close()
                    }

                    val strokePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 1 until coords.size) {
                            val prev = coords[i - 1]
                            val curr = coords[i]
                            val cx = (prev.x + curr.x) / 2f
                            cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        }
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                screenOffColor.copy(alpha = 0.35f),
                                screenOffColor.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                        ),
                    )

                    drawPath(
                        path = strokePath,
                        color = screenOffColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )

                    // Draw distinct dots for screen-on periods (throttled)
                    points.forEachIndexed { idx, p ->
                        if (p.isScreenOn && idx < coords.size) {
                            drawCircle(
                                color = screenOnColor,
                                radius = 3.dp.toPx(),
                                center = coords[idx],
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Screen Off vs Screen On Speed Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(screenOffColor),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${stringResource(R.string.cycles_screen_off)}: ${String.format(Locale.US, "%.1f", avgScreenOff)} $unitSymbol",
                    fontSize = 10.sp,
                    color = screenOffColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(screenOnColor),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${stringResource(R.string.cycles_screen_on)}: ${String.format(Locale.US, "%.1f", avgScreenOn)} $unitSymbol",
                    fontSize = 10.sp,
                    color = screenOnColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Canvas curve for battery temperature (°C vs time)
 */
@Composable
private fun TemperatureCurveCanvas(
    points: List<CycleTelemetryPoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val gridColor = colorScheme.outline.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.20f))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val width = size.width
            val height = size.height

            // Horizontal grid lines
            for (i in 0..2) {
                val y = height * (i.toFloat() / 2f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                )
            }

            val values = if (points.isNotEmpty()) {
                points.map { it.batteryTempC }
            } else {
                listOf(30f, 32f)
            }

            val maxVal = maxOf(values.maxOrNull() ?: 40f, 35f)
            val minVal = minOf(values.minOrNull() ?: 25f, 25f)
            val range = (maxVal - minVal).coerceAtLeast(1f)
            val stepX = width / (values.size - 1).coerceAtLeast(1)

            val coords = values.mapIndexed { idx, v ->
                val x = idx * stepX
                val norm = ((v - minVal) / range).coerceIn(0f, 1f)
                val y = height - (norm * (height - 8f)) - 4f
                Offset(x, y)
            }

            if (coords.isNotEmpty()) {
                val fillPath = Path().apply {
                    moveTo(coords.first().x, height)
                    lineTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val curr = coords[i]
                        val cx = (prev.x + curr.x) / 2f
                        cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }
                    lineTo(coords.last().x, height)
                    close()
                }

                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val curr = coords[i]
                        val cx = (prev.x + curr.x) / 2f
                        cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.35f),
                            color.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                )

                drawPath(
                    path = strokePath,
                    color = color,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

private fun formatCycleTimeRange(startTimeMs: Long, endTimeMs: Long, isActive: Boolean): String {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val startStr = timeFmt.format(Date(startTimeMs))
    return if (isActive) {
        "$startStr • Em andamento"
    } else {
        val endStr = timeFmt.format(Date(endTimeMs))
        "$startStr - $endStr"
    }
}

private fun formatDurationCompact(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
