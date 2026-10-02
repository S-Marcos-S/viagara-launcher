// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.app.Application
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
}
