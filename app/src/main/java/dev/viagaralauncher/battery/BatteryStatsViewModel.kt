// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatteryStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val shellRunner = ShellRunner(context)
    private val collector = DetailedStatsCollector(context, shellRunner)
    private val drainTracker = AdvancedDrainTracker.getInstance(context)

    // Forward flows from collector
    val snapshot: StateFlow<BatteryStatsParser.FullSnapshot?> = collector.snapshot
    val deviceIdle: StateFlow<BatteryStatsParser.DeviceIdleInfo?> = collector.deviceIdle
    val powerManager: StateFlow<BatteryStatsParser.PowerManagerInfo?> = collector.powerManager
    val lastRefresh: StateFlow<Long> = collector.lastRefresh
    val isRefreshing: StateFlow<Boolean> = collector.isRefreshing
    val error: StateFlow<String?> = collector.error

    // Drain tracker flows
    val drainState: StateFlow<DrainState> = drainTracker.drainState

    private val _isDrainNotificationEnabled = MutableStateFlow(
        DrainNotificationManager.isNotificationEnabled(context)
    )
    val isDrainNotificationEnabled: StateFlow<Boolean> = _isDrainNotificationEnabled.asStateFlow()

    private val _hasRoot = MutableStateFlow(false)
    val hasRoot: StateFlow<Boolean> = _hasRoot.asStateFlow()

    private val _hasAdb = MutableStateFlow(false)
    val hasAdb: StateFlow<Boolean> = _hasAdb.asStateFlow()

    private val _hasAdvanced = MutableStateFlow(false)
    val hasAdvanced: StateFlow<Boolean> = _hasAdvanced.asStateFlow()

    private val _advMode = MutableStateFlow<ShellRunner.Mode>(ShellRunner.Mode.NONE)
    val advMode: StateFlow<ShellRunner.Mode> = _advMode.asStateFlow()

    private val _kernelBattery = MutableStateFlow<RootBatteryStatsCollector.KernelBatteryInfo?>(null)
    val kernelBattery: StateFlow<RootBatteryStatsCollector.KernelBatteryInfo?> = _kernelBattery.asStateFlow()

    private val _kernelWakelocks = MutableStateFlow<List<RootBatteryStatsCollector.KernelWakelockInfo>>(emptyList())
    val kernelWakelocks: StateFlow<List<RootBatteryStatsCollector.KernelWakelockInfo>> = _kernelWakelocks.asStateFlow()

    private val _thermalZones = MutableStateFlow<List<RootBatteryStatsCollector.ThermalZone>>(emptyList())
    val thermalZones: StateFlow<List<RootBatteryStatsCollector.ThermalZone>> = _thermalZones.asStateFlow()

    private val _cpuInfo = MutableStateFlow<List<RootBatteryStatsCollector.CpuInfo>>(emptyList())
    val cpuInfo: StateFlow<List<RootBatteryStatsCollector.CpuInfo>> = _cpuInfo.asStateFlow()

    init {
        // Start drain tracker in background so it maintains running state
        if (!drainTracker.isRunning()) {
            drainTracker.start()
        }
        refresh(forceRefresh = true)
    }

    fun clearError() = collector.clearError()

    fun refresh(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) shellRunner.invalidateMode()

            val rootAvailable = RootBatteryStatsCollector.isRootAvailable()
            val adbAvailable = ShellRunner.hasDumpPermission(context) || ShellRunner.hasBatteryStatsPermission(context)
            val mode = shellRunner.detectMode(forceRefresh)

            _hasRoot.value = rootAvailable
            _hasAdb.value = adbAvailable
            _advMode.value = mode
            _hasAdvanced.value = mode != ShellRunner.Mode.NONE || rootAvailable || adbAvailable

            if (_hasAdvanced.value) {
                collector.refresh()
            }

            if (rootAvailable) {
                refreshRootStats()
            }

            drainTracker.updateDrainState()
        }
    }

    fun refreshRootStats() {
        viewModelScope.launch {
            if (RootBatteryStatsCollector.isRootAvailable()) {
                _kernelBattery.value = RootBatteryStatsCollector.getKernelBatteryInfo()
                _kernelWakelocks.value = RootBatteryStatsCollector.getKernelWakelocks()
                _thermalZones.value = RootBatteryStatsCollector.getThermalZones()
                _cpuInfo.value = RootBatteryStatsCollector.getCpuInfo()
            }
        }
    }

    suspend fun resetStats(): Boolean {
        return try {
            if (_hasAdvanced.value) {
                val ok = collector.resetStats()
                if (ok) {
                    drainTracker.resetSession()
                    DrainNotificationManager.getInstance(context).updateNow()
                }
                ok
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun toggleDrainNotification(enabled: Boolean) {
        DrainNotificationManager.setNotificationEnabled(context, enabled)
        _isDrainNotificationEnabled.value = enabled
    }

    fun resetDrainSession() {
        drainTracker.resetSession()
        DrainNotificationManager.getInstance(context).updateNow()
    }

    private val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)

    private val _autoResetEnabled = MutableStateFlow(prefs.getBoolean("auto_reset_enabled", false))
    val autoResetEnabled: StateFlow<Boolean> = _autoResetEnabled.asStateFlow()

    private val _autoResetPercent = MutableStateFlow(prefs.getInt("auto_reset_percent", 100))
    val autoResetPercent: StateFlow<Int> = _autoResetPercent.asStateFlow()

    private val _notifShowRates = MutableStateFlow(prefs.getBoolean("notif_show_rates", true))
    val notifShowRates: StateFlow<Boolean> = _notifShowRates.asStateFlow()

    private val _notifShowDeepSleep = MutableStateFlow(prefs.getBoolean("notif_show_deep_sleep", true))
    val notifShowDeepSleep: StateFlow<Boolean> = _notifShowDeepSleep.asStateFlow()

    private val _notifShowActiveIdle = MutableStateFlow(prefs.getBoolean("notif_show_active_idle", true))
    val notifShowActiveIdle: StateFlow<Boolean> = _notifShowActiveIdle.asStateFlow()

    private val _notifShowTotalAvg = MutableStateFlow(prefs.getBoolean("notif_show_total_avg", true))
    val notifShowTotalAvg: StateFlow<Boolean> = _notifShowTotalAvg.asStateFlow()

    private val _notifShowTemperature = MutableStateFlow(prefs.getBoolean("notif_show_temperature", true))
    val notifShowTemperature: StateFlow<Boolean> = _notifShowTemperature.asStateFlow()

    private val _notifShowNetwork = MutableStateFlow(prefs.getBoolean("notif_show_network", true))
    val notifShowNetwork: StateFlow<Boolean> = _notifShowNetwork.asStateFlow()

    fun setAutoResetEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_reset_enabled", enabled).apply()
        _autoResetEnabled.value = enabled
    }

    fun setAutoResetPercent(percent: Int) {
        prefs.edit().putInt("auto_reset_percent", percent).apply()
        _autoResetPercent.value = percent
    }

    fun setNotifShowRates(show: Boolean) {
        prefs.edit().putBoolean("notif_show_rates", show).apply()
        _notifShowRates.value = show
        DrainNotificationManager.getInstance(context).updateNow()
    }

    fun setNotifShowDeepSleep(show: Boolean) {
        prefs.edit().putBoolean("notif_show_deep_sleep", show).apply()
        _notifShowDeepSleep.value = show
        DrainNotificationManager.getInstance(context).updateNow()
    }

    fun setNotifShowActiveIdle(show: Boolean) {
        prefs.edit().putBoolean("notif_show_active_idle", show).apply()
        _notifShowActiveIdle.value = show
        DrainNotificationManager.getInstance(context).updateNow()
    }

    fun setNotifShowTotalAvg(show: Boolean) {
        prefs.edit().putBoolean("notif_show_total_avg", show).apply()
        _notifShowTotalAvg.value = show
        DrainNotificationManager.getInstance(context).updateNow()
    }

    fun setNotifShowTemperature(show: Boolean) {
        prefs.edit().putBoolean("notif_show_temperature", show).apply()
        _notifShowTemperature.value = show
        DrainNotificationManager.getInstance(context).updateNow()
    }

    fun setNotifShowNetwork(show: Boolean) {
        prefs.edit().putBoolean("notif_show_network", show).apply()
        _notifShowNetwork.value = show
        DrainNotificationManager.getInstance(context).updateNow()
    }
}
