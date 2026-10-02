// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import java.util.Locale

/**
 * Tracks battery drain rates and cumulative metrics across different device states.
 */
data class DrainState(
    val timestamp: Long = System.currentTimeMillis(),

    // Current battery level & metrics
    val batteryLevel: Int? = null,
    val batteryLevelMah: Double? = null,
    val batteryTemperatureC: Float? = null,

    // Device state
    val isScreenOn: Boolean = false,
    val isCharging: Boolean = false,
    val isDeepSleep: Boolean = false,
    val isDozing: Boolean = false,

    // Cumulative drain by state (mAh)
    val screenOnDrainMah: Double = 0.0,
    val screenOffDrainMah: Double = 0.0,
    val activeDrainMah: Double = 0.0,
    val idleDrainMah: Double = 0.0,
    val deepSleepDrainMah: Double = 0.0,
    val awakeDrainMah: Double = 0.0,

    // Time spent in each state (ms)
    val screenOnTimeMs: Long = 0L,
    val screenOffTimeMs: Long = 0L,
    val activeTimeMs: Long = 0L,
    val idleTimeMs: Long = 0L,
    val deepSleepTimeMs: Long = 0L,
    val awakeTimeMs: Long = 0L,

    // Drain rates (mA) - calculated averages
    val screenOnDrainRate: Double = 0.0,
    val screenOffDrainRate: Double = 0.0,
    val activeDrainRate: Double = 0.0,
    val idleDrainRate: Double = 0.0,
    val deepSleepDrainRate: Double = 0.0,
    val awakeDrainRate: Double = 0.0,

    // Battery capacity for percentage drain calculations
    val batteryCapacityMah: Double = 4000.0,

    // Session tracking
    val sessionStartTime: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = System.currentTimeMillis()
) {

    val totalDrainMah: Double
        get() = screenOnDrainMah + screenOffDrainMah

    val totalTimeMs: Long
        get() = (System.currentTimeMillis() - sessionStartTime).coerceAtLeast(0L)

    val averageDrainRate: Double
        get() = if (totalTimeMs > 0) {
            (totalDrainMah / (totalTimeMs / 3600000.0))
        } else 0.0

    val screenOnPercentage: Float
        get() = if (totalTimeMs > 0) {
            (screenOnTimeMs.toFloat() / totalTimeMs * 100f).coerceIn(0f, 100f)
        } else 0f

    val deepSleepPercentage: Float
        get() = if (screenOffTimeMs > 0) {
            (deepSleepTimeMs.toFloat() / screenOffTimeMs * 100f).coerceIn(0f, 100f)
        } else 0f

    val screenOnDrainRatePercent: Double
        get() = if (batteryCapacityMah > 0) (screenOnDrainRate / batteryCapacityMah * 100.0) else 0.0

    val screenOffDrainRatePercent: Double
        get() = if (batteryCapacityMah > 0) (screenOffDrainRate / batteryCapacityMah * 100.0) else 0.0

    val deepSleepDrainRatePercent: Double
        get() = if (batteryCapacityMah > 0) (deepSleepDrainRate / batteryCapacityMah * 100.0) else 0.0

    val awakeDrainRatePercent: Double
        get() = if (batteryCapacityMah > 0) (awakeDrainRate / batteryCapacityMah * 100.0) else 0.0

    val activeDrainRatePercent: Double
        get() = if (batteryCapacityMah > 0) (activeDrainRate / batteryCapacityMah * 100.0) else 0.0

    val idleDrainRatePercent: Double
        get() = if (batteryCapacityMah > 0) (idleDrainRate / batteryCapacityMah * 100.0) else 0.0

    val averageDrainRatePercent: Double
        get() = if (batteryCapacityMah > 0) (averageDrainRate / batteryCapacityMah * 100.0) else 0.0
}

/**
 * Snapshot of drain metrics at a point in time
 */
data class DrainSnapshot(
    val timestamp: Long,
    val elapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
    val uptimeMillis: Long = android.os.SystemClock.uptimeMillis(),
    val batteryLevel: Int?,
    val batteryMah: Double?,
    val currentMa: Int,
    val isScreenOn: Boolean,
    val isCharging: Boolean,
    val isDeepSleep: Boolean,
    val isDozing: Boolean,
    val cpuAwakeTimeMs: Long,
    val deepSleepTimeMs: Long
)

enum class DeviceState {
    SCREEN_ON_ACTIVE,
    SCREEN_ON_IDLE,
    SCREEN_OFF_AWAKE,
    SCREEN_OFF_DOZE,
    SCREEN_OFF_DEEP_SLEEP,
    CHARGING
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = (totalSeconds / 3600) % 24
    val days = totalSeconds / 86400

    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

fun formatDrainRate(rate: Double): String {
    return when {
        rate < 0.1 -> "< 0.1 mA/h"
        rate < 10 -> String.format(Locale.getDefault(), "%.1f mA/h", rate)
        else -> String.format(Locale.getDefault(), "%.0f mA/h", rate)
    }
}

fun formatDrainPercentage(rate: Double): String {
    return when {
        rate <= 0.0 -> "0.0%/h"
        rate < 0.1 -> "< 0.1%/h"
        else -> String.format(Locale.getDefault(), "%.1f%%/h", rate)
    }
}
