// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Advanced drain tracker that monitors real-time battery drain across different device states:
 * screen-on (active vs idle), screen-off (awake vs deep sleep), and charging.
 */
class AdvancedDrainTracker private constructor(
    private val context: Context,
    private val shellRunner: ShellRunner = ShellRunner(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val TAG = "AdvancedDrainTracker"
        private const val DEEP_SLEEP_THRESHOLD_MS = 30_000L
        private const val DEFAULT_INTERVAL_MS = 60_000L

        @Volatile
        private var instance: AdvancedDrainTracker? = null

        fun getInstance(context: Context): AdvancedDrainTracker {
            return instance ?: synchronized(this) {
                instance ?: AdvancedDrainTracker(context.applicationContext).also { instance = it }
            }
        }
    }

    private val running = AtomicBoolean(false)
    private var trackingJob: Job? = null

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val _drainState = MutableStateFlow(DrainState())
    val drainState: StateFlow<DrainState> = _drainState.asStateFlow()

    private val _snapshots = MutableStateFlow<List<DrainSnapshot>>(emptyList())
    val snapshots: StateFlow<List<DrainSnapshot>> = _snapshots.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var lastSnapshot: DrainSnapshot? = null
    private var lastScreenState: Boolean = powerManager.isInteractive
    private var lastScreenChangeTime: Long = System.currentTimeMillis()
    private var lastScreenChangeRealtime: Long = SystemClock.elapsedRealtime()
    private var sessionStartRealtime: Long = SystemClock.elapsedRealtime()
    private var sessionStartUptime: Long = SystemClock.uptimeMillis()
    private var receiverRegistered = false
    private var estimatedCapacityMah: Double = getInitialCapacity()

    private fun getInitialCapacity(): Double {
        try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val cap = powerProfileClass.getMethod("getBatteryCapacity").invoke(powerProfile) as? Double
            if (cap != null && cap > 0.0) return cap
        } catch (_: Throwable) {}

        for (path in listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/battery/charge_full"
        )) {
            try {
                val file = java.io.File(path)
                if (file.exists() && file.canRead()) {
                    val value = file.readText().trim().toLongOrNull() ?: 0L
                    if (value > 100_000L) return value / 1000.0
                    if (value in 1000..20000) return value.toDouble()
                }
            } catch (_: Throwable) {}
        }

        return 4000.0
    }

    fun updateCapacity(capacityMah: Double) {
        if (capacityMah > 0 && capacityMah != estimatedCapacityMah) {
            estimatedCapacityMah = capacityMah
            updateDrainState()
        }
    }

    // Cumulative tracking (ms)
    private var cumulativeScreenOnTime: Long = 0L
    private var cumulativeScreenOffTime: Long = 0L
    private var cumulativeDeepSleepTime: Long = 0L
    private var cumulativeAwakeTime: Long = 0L
    private var cumulativeActiveTime: Long = 0L
    private var cumulativeIdleTime: Long = 0L

    // Cumulative drain (mAh)
    private var cumulativeScreenOnDrain: Double = 0.0
    private var cumulativeScreenOffDrain: Double = 0.0
    private var cumulativeDeepSleepDrain: Double = 0.0
    private var cumulativeAwakeDrain: Double = 0.0
    private var cumulativeActiveDrain: Double = 0.0
    private var cumulativeIdleDrain: Double = 0.0

    private var sessionStartTime: Long = System.currentTimeMillis()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreenStateChanged(true)
                Intent.ACTION_SCREEN_OFF -> onScreenStateChanged(false)
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    scope.launch {
                        takeSnapshot()?.let { snapshot ->
                            lastSnapshot = snapshot
                            updateDrainState()
                        }
                    }
                }
            }
        }
    }

    fun isRunning(): Boolean = running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return

        Log.i(TAG, "Starting advanced drain tracking")
        _isTracking.value = true
        resetSession()
        registerReceivers()

        trackingJob = scope.launch {
            takeSnapshot()?.let { snapshot ->
                lastSnapshot = snapshot
            }

            while (isActive && running.get()) {
                try {
                    takeSnapshot()?.let { currentSnapshot ->
                        processSnapshot(currentSnapshot, wasScreenOn = lastScreenState)
                        lastSnapshot = currentSnapshot
                        _snapshots.update { (it + currentSnapshot).takeLast(500) }
                    }
                    updateDrainState()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in tracking loop", e)
                }
                delay(DEFAULT_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return

        Log.i(TAG, "Stopping advanced drain tracking")
        _isTracking.value = false
        trackingJob?.cancel()
        trackingJob = null
        unregisterReceivers()
    }

    fun resetSession() {
        val now = System.currentTimeMillis()
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()

        sessionStartTime = now
        sessionStartRealtime = nowRealtime
        sessionStartUptime = nowUptime

        cumulativeScreenOnTime = 0L
        cumulativeScreenOffTime = 0L
        cumulativeDeepSleepTime = 0L
        cumulativeAwakeTime = 0L
        cumulativeActiveTime = 0L
        cumulativeIdleTime = 0L

        cumulativeScreenOnDrain = 0.0
        cumulativeScreenOffDrain = 0.0
        cumulativeDeepSleepDrain = 0.0
        cumulativeAwakeDrain = 0.0
        cumulativeActiveDrain = 0.0
        cumulativeIdleDrain = 0.0

        lastScreenState = powerManager.isInteractive
        lastScreenChangeTime = now
        lastScreenChangeRealtime = nowRealtime

        _snapshots.value = emptyList()
        _drainState.value = DrainState(
            sessionStartTime = sessionStartTime,
            batteryCapacityMah = estimatedCapacityMah
        )

        scope.launch {
            lastSnapshot = takeSnapshot()
            updateDrainState()
        }

        Log.i(TAG, "Battery drain tracking session reset")
    }

    private fun registerReceivers() {
        if (receiverRegistered) return
        try {
            val screenFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(
                context,
                screenReceiver,
                screenFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screen receivers", e)
        }
    }

    private fun unregisterReceivers() {
        if (!receiverRegistered) return
        receiverRegistered = false
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister screen receivers", e)
        }
    }

    private fun onScreenStateChanged(screenOn: Boolean) {
        if (screenOn == lastScreenState) return

        val nowRealtime = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val duration = (nowRealtime - lastScreenChangeRealtime).coerceAtLeast(0L)

        val previousScreenState = lastScreenState
        if (previousScreenState) {
            cumulativeScreenOnTime += duration
        } else {
            cumulativeScreenOffTime += duration
        }

        lastScreenState = screenOn
        lastScreenChangeRealtime = nowRealtime
        lastScreenChangeTime = now

        scope.launch {
            takeSnapshot()?.let { snapshot ->
                processSnapshot(snapshot, wasScreenOn = previousScreenState)
                lastSnapshot = snapshot
                updateDrainState()
            }
        }
    }

    private suspend fun takeSnapshot(): DrainSnapshot? {
        return try {
            val level = getBatteryLevel()
            val mah = getCurrentBatteryMah()
            val currentMa = getCurrentNowMa()
            val isScreenOn = powerManager.isInteractive
            val isCharging = isCharging()
            val isDozing = powerManager.isDeviceIdleMode

            val nowRealtime = SystemClock.elapsedRealtime()
            val nowUptime = SystemClock.uptimeMillis()
            val deepSleepTime = (nowRealtime - nowUptime).coerceAtLeast(0L)

            DrainSnapshot(
                timestamp = System.currentTimeMillis(),
                elapsedRealtime = nowRealtime,
                uptimeMillis = nowUptime,
                batteryLevel = level,
                batteryMah = mah,
                currentMa = currentMa,
                isScreenOn = isScreenOn,
                isCharging = isCharging,
                isDeepSleep = !isScreenOn && !isDozing,
                isDozing = isDozing,
                cpuAwakeTimeMs = nowUptime,
                deepSleepTimeMs = deepSleepTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take snapshot", e)
            null
        }
    }

    private fun processSnapshot(current: DrainSnapshot, wasScreenOn: Boolean) {
        val previous = lastSnapshot ?: return
        if (current.isCharging || previous.isCharging) return

        val timeDelta = current.timestamp - previous.timestamp
        if (timeDelta <= 0) return

        val previousMah = previous.batteryMah ?: return
        val currentMah = current.batteryMah ?: return
        val drainMah = max(0.0, previousMah - currentMah)

        if (wasScreenOn) {
            cumulativeScreenOnDrain += drainMah
            if (abs(current.currentMa) > 200) {
                cumulativeActiveDrain += drainMah
                cumulativeActiveTime += timeDelta
            } else {
                cumulativeIdleDrain += drainMah
                cumulativeIdleTime += timeDelta
            }
        } else {
            cumulativeScreenOffDrain += drainMah
            val prevDeepSleep = previous.elapsedRealtime - previous.uptimeMillis
            val currDeepSleep = current.elapsedRealtime - current.uptimeMillis
            val deltaDeepSleep = (currDeepSleep - prevDeepSleep).coerceAtLeast(0L)
            val intervalTime = (current.elapsedRealtime - previous.elapsedRealtime).coerceAtLeast(1L)
            val sleepRatio = (deltaDeepSleep.toDouble() / intervalTime).coerceIn(0.0, 1.0)

            cumulativeDeepSleepDrain += drainMah * sleepRatio
            cumulativeAwakeDrain += drainMah * (1.0 - sleepRatio)
        }
    }

    fun updateDrainState() {
        val now = System.currentTimeMillis()
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()

        val pending = (nowRealtime - lastScreenChangeRealtime).coerceAtLeast(0L)
        val screenOnTimeTotal = cumulativeScreenOnTime + if (lastScreenState) pending else 0L
        val screenOffTimeTotal = cumulativeScreenOffTime + if (!lastScreenState) pending else 0L

        val sessionElapsed = (nowRealtime - sessionStartRealtime).coerceAtLeast(0L)
        val sessionUptime = (nowUptime - sessionStartUptime).coerceAtLeast(0L)
        val totalSessionDeepSleep = (sessionElapsed - sessionUptime).coerceAtLeast(0L)

        val deepSleepTimeMs = totalSessionDeepSleep.coerceAtMost(screenOffTimeTotal)
        val awakeTimeMs = (screenOffTimeTotal - deepSleepTimeMs).coerceAtLeast(0L)

        val activeTimeMs = cumulativeActiveTime.coerceAtMost(screenOnTimeTotal)
        val idleTimeMs = (screenOnTimeTotal - activeTimeMs).coerceAtLeast(0L)

        fun calculateRate(drainMah: Double, timeMs: Long): Double {
            return if (timeMs > 0) drainMah / (timeMs / 3600000.0) else 0.0
        }

        val screenOnDrainRate = calculateRate(cumulativeScreenOnDrain, screenOnTimeTotal)
        val screenOffDrainRate = calculateRate(cumulativeScreenOffDrain, screenOffTimeTotal)
        val activeDrainRate = calculateRate(cumulativeActiveDrain, activeTimeMs)
        val idleDrainRate = calculateRate(cumulativeIdleDrain, idleTimeMs)
        val deepSleepDrainRate = calculateRate(cumulativeDeepSleepDrain, deepSleepTimeMs)
        val awakeDrainRate = calculateRate(cumulativeAwakeDrain, awakeTimeMs)

        _drainState.value = DrainState(
            timestamp = now,
            batteryLevel = getBatteryLevel(),
            batteryLevelMah = getCurrentBatteryMah(),
            batteryCapacityMah = estimatedCapacityMah,
            isScreenOn = powerManager.isInteractive,
            isCharging = isCharging(),
            isDeepSleep = !powerManager.isInteractive && (nowRealtime - nowUptime > 0),
            isDozing = powerManager.isDeviceIdleMode,

            screenOnDrainMah = cumulativeScreenOnDrain,
            screenOffDrainMah = cumulativeScreenOffDrain,
            activeDrainMah = cumulativeActiveDrain,
            idleDrainMah = cumulativeIdleDrain,
            deepSleepDrainMah = cumulativeDeepSleepDrain,
            awakeDrainMah = cumulativeAwakeDrain,

            screenOnTimeMs = screenOnTimeTotal,
            screenOffTimeMs = screenOffTimeTotal,
            activeTimeMs = activeTimeMs,
            idleTimeMs = idleTimeMs,
            deepSleepTimeMs = deepSleepTimeMs,
            awakeTimeMs = awakeTimeMs,

            screenOnDrainRate = screenOnDrainRate,
            screenOffDrainRate = screenOffDrainRate,
            activeDrainRate = activeDrainRate,
            idleDrainRate = idleDrainRate,
            deepSleepDrainRate = deepSleepDrainRate,
            awakeDrainRate = awakeDrainRate,

            sessionStartTime = sessionStartTime,
            lastUpdateTime = now
        )
    }

    fun getBatteryLevel(): Int? {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 0..100) return level

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val raw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (raw >= 0 && scale > 0) (raw * 100 / scale) else null
    }

    fun getCurrentBatteryMah(): Double? {
        val chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return if (chargeCounter > 0) {
            chargeCounter / 1000.0
        } else {
            getBatteryLevel()?.div(100.0)?.times(estimatedCapacityMah)
        }
    }

    fun getCurrentNowMa(): Int {
        var current = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (current == 0L || current == Long.MIN_VALUE) {
            current = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }
        return (current / 1000).toInt()
    }

    fun isCharging(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }
}
