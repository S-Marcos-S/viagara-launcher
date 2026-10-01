// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.Date
import kotlin.math.roundToInt

data class SystemStats(
    val ramAvailableGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val ramUsedGb: Float = 0f,
    val ramUsedPercent: Int = 0,
    val storageAvailableGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val storageUsedGb: Float = 0f,
    val storageUsedPercent: Int = 0,
    val batteryPercent: Int = 0,
    val batteryTempCelsius: Float = 0f,
    val isCharging: Boolean = false,
)

/**
 * Returns current device technical stats (RAM, battery level, battery temperature, charging status).
 * Efficiently updates event-driven via system battery broadcasts, minute ticks, and on-resume,
 * with ZERO background polling or battery drain.
 */
@Composable
fun rememberSystemStats(currentTime: Date): SystemStats {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var stats by remember { mutableStateOf(getSystemStats(context)) }

    // When the minute tick fires, update the RAM / stats
    LaunchedEffect(currentTime) {
        stats = getSystemStats(context)
    }

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                c?.let { stats = getSystemStats(it, intent) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val stickyIntent = context.registerReceiver(receiver, filter)
        stats = getSystemStats(context, stickyIntent)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Instantly update stats when user returns to the home screen
                stats = getSystemStats(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return stats
}

fun getSystemStats(context: Context, batteryIntent: Intent? = null): SystemStats {
    // 1. RAM Information
    var ramAvailGb = 0f
    var ramTotalGb = 0f
    var ramUsedGb = 0f
    var ramUsedPercent = 0

    try {
        val act = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        if (act != null) {
            act.getMemoryInfo(memInfo)
            val bytesInGb = 1024f * 1024f * 1024f
            ramAvailGb = (memInfo.availMem / bytesInGb)
            ramTotalGb = (memInfo.totalMem / bytesInGb)
            ramUsedGb = (ramTotalGb - ramAvailGb).coerceAtLeast(0f)
            if (ramTotalGb > 0f) {
                ramUsedPercent = ((ramUsedGb / ramTotalGb) * 100f).roundToInt().coerceIn(0, 100)
            }
        }
    } catch (_: Exception) {}

    // 2. Battery & Temperature Information
    var batteryPercent = 0
    var batteryTempCelsius = 0f
    var isCharging = false

    try {
        val intent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryPercent = ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
            }
            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            batteryTempCelsius = (tempRaw / 10f).coerceAtLeast(0f)

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL
        }
    } catch (_: Exception) {}

    // 3. Storage Information (ROM / Internal Storage)
    var storageAvailGb = 0f
    var storageTotalGb = 0f
    var storageUsedGb = 0f
    var storageUsedPercent = 0

    try {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBytes = stat.blockCountLong * blockSize
        val availBytes = stat.availableBlocksLong * blockSize
        val bytesInGb = 1024f * 1024f * 1024f

        storageAvailGb = (availBytes / bytesInGb)
        storageTotalGb = (totalBytes / bytesInGb)
        storageUsedGb = (storageTotalGb - storageAvailGb).coerceAtLeast(0f)
        if (storageTotalGb > 0f) {
            storageUsedPercent = ((storageUsedGb / storageTotalGb) * 100f).roundToInt().coerceIn(0, 100)
        }
    } catch (_: Exception) {}

    return SystemStats(
        ramAvailableGb = ramAvailGb,
        ramTotalGb = ramTotalGb,
        ramUsedGb = ramUsedGb,
        ramUsedPercent = ramUsedPercent,
        storageAvailableGb = storageAvailGb,
        storageTotalGb = storageTotalGb,
        storageUsedGb = storageUsedGb,
        storageUsedPercent = storageUsedPercent,
        batteryPercent = batteryPercent,
        batteryTempCelsius = batteryTempCelsius,
        isCharging = isCharging,
    )
}
