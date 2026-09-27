// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.network

import android.graphics.drawable.Drawable

enum class StatsPeriod {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val mobileData: Long,
    val wifiData: Long,
    val totalData: Long = mobileData + wifiData,
)

data class AppUsageSegment(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val bytes: Long,
    val color: Int,
)

data class DataPoint(
    val timestamp: Long,
    val label: String,
    val mobileData: Long,
    val wifiData: Long,
    val appSegments: List<AppUsageSegment> = emptyList(),
)

data class TimePeriodStats(
    val dataPoints: List<DataPoint> = emptyList(),
    val totalMobile: Long = 0L,
    val totalWifi: Long = 0L,
    val topApps: List<AppUsageInfo> = emptyList(),
)

sealed class StatsUiState {
    data object Loading : StatsUiState()
    data object NoPermission : StatsUiState()
    data class Success(val stats: TimePeriodStats) : StatsUiState()
    data class Error(val message: String) : StatsUiState()
}
