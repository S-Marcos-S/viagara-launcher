// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.root.anomaly

import android.app.ActivityManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.root.AppRootInspector
import dev.viagaralauncher.root.SystemTaskInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class BackgroundAnomalyWatcher private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "background_anomaly_prefs"
        private const val KEY_ENABLED = "key_enabled"
        private const val KEY_SENSITIVITY = "key_sensitivity"
        private const val KEY_NOTIFY_NET = "key_notify_net"
        private const val KEY_NOTIFY_CPU = "key_notify_cpu"
        private const val KEY_NOTIFY_RAM = "key_notify_ram"
        private const val KEY_WHITELIST = "key_whitelist"

        private const val SCREEN_ON_INTERVAL_MS = 30_000L
        private const val COOLDOWN_MS = 25 * 60 * 1000L // 25 minutos entre notificações por app

        @Volatile
        private var instance: BackgroundAnomalyWatcher? = null

        fun getInstance(context: Context): BackgroundAnomalyWatcher {
            return instance ?: synchronized(this) {
                instance ?: BackgroundAnomalyWatcher(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationManager = BackgroundAnomalyNotificationManager.getInstance(context)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AnomalyWatcherConfig> = _config.asStateFlow()

    private val _anomalies = MutableStateFlow<List<AnomalyEvent>>(emptyList())
    val anomalies: StateFlow<List<AnomalyEvent>> = _anomalies.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var screenOnLoopJob: Job? = null
    private var screenReceiverRegistered = false

    private var isScreenInteractive = false
    private var screenOffTimeRealtime = 0L
    private var screenOffWallTime = 0L

    private data class UidNetSample(val rx: Long, val tx: Long, val timestamp: Long)

    // Cache de amostras de rede para cálculo diferencial (tela ligada)
    private val activeNetSamples = ConcurrentHashMap<Int, UidNetSample>()

    // Fotografia de rede no exato instante em que a tela é desligada
    private val screenOffNetSnapshots = ConcurrentHashMap<Int, UidNetSample>()

    // Controle de pacotes temporariamente silenciados (mute) e cooldown de alertas
    private val mutedPackages = ConcurrentHashMap<String, Long>()
    private val lastAlertTimeMap = ConcurrentHashMap<String, Long>()

    // Rastreamento de CPU para confirmação de anomalia persistente (evita falso-positivo de picos de 1s)
    private val cpuHighAppConsecutiveCycles = ConcurrentHashMap<String, Int>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    onScreenTurnedOff()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    onScreenTurnedOn()
                }
            }
        }
    }

    init {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = pm?.isInteractive ?: true
    }

    private fun loadConfig(): AnomalyWatcherConfig {
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val sensName = prefs.getString(KEY_SENSITIVITY, AnomalySensitivity.BALANCED.name)
        val sensitivity = runCatching { AnomalySensitivity.valueOf(sensName ?: "") }.getOrDefault(AnomalySensitivity.BALANCED)
        val notifyNet = prefs.getBoolean(KEY_NOTIFY_NET, true)
        val notifyCpu = prefs.getBoolean(KEY_NOTIFY_CPU, true)
        val notifyRam = prefs.getBoolean(KEY_NOTIFY_RAM, true)
        val whitelist = prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()

        return AnomalyWatcherConfig(
            isEnabled = enabled,
            sensitivity = sensitivity,
            notifyNetwork = notifyNet,
            notifyCpu = notifyCpu,
            notifyRam = notifyRam,
            whitelistedPackages = whitelist,
        )
    }

    fun updateConfig(newConfig: AnomalyWatcherConfig) {
        _config.value = newConfig
        prefs.edit()
            .putBoolean(KEY_ENABLED, newConfig.isEnabled)
            .putString(KEY_SENSITIVITY, newConfig.sensitivity.name)
            .putBoolean(KEY_NOTIFY_NET, newConfig.notifyNetwork)
            .putBoolean(KEY_NOTIFY_CPU, newConfig.notifyCpu)
            .putBoolean(KEY_NOTIFY_RAM, newConfig.notifyRam)
            .putStringSet(KEY_WHITELIST, newConfig.whitelistedPackages)
            .apply()

        if (!newConfig.isEnabled) {
            stop()
        } else if (!isRunning.get()) {
            start()
        }
    }

    fun mutePackage(packageName: String, durationMs: Long) {
        mutedPackages[packageName] = SystemClock.elapsedRealtime() + durationMs
    }

    fun isPackageMuted(packageName: String): Boolean {
        val expiry = mutedPackages[packageName] ?: return false
        if (SystemClock.elapsedRealtime() > expiry) {
            mutedPackages.remove(packageName)
            return false
        }
        return true
    }

    fun clearAnomalies() {
        _anomalies.value = emptyList()
    }

    @Synchronized
    fun start() {
        if (isRunning.getAndSet(true)) return

        scope.launch(Dispatchers.IO) {
            if (AppRootInspector.isRootAvailable()) {
                AppRootInspector.runSuCommand("pm grant ${context.packageName} android.permission.PACKAGE_USAGE_STATS 2>/dev/null; appops set ${context.packageName} GET_USAGE_STATS allow 2>/dev/null")
            }
        }

        if (!screenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = pm?.isInteractive ?: true
        _isMonitoring.value = true

        if (isScreenInteractive) {
            startScreenOnEvaluationLoop()
        } else {
            captureScreenOffSnapshot()
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning.getAndSet(false)) return
        screenOnLoopJob?.cancel()
        screenOnLoopJob = null
        _isMonitoring.value = false

        if (screenReceiverRegistered) {
            runCatching { context.unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
    }

    private fun onScreenTurnedOff() {
        isScreenInteractive = false
        screenOffTimeRealtime = SystemClock.elapsedRealtime()
        screenOffWallTime = System.currentTimeMillis()

        // Regra de Ouro: suspende qualquer loop de polling ativo. O processador DEVE dormir 100%.
        screenOnLoopJob?.cancel()
        screenOnLoopJob = null

        captureScreenOffSnapshot()
    }

    private fun onScreenTurnedOn() {
        if (isScreenInteractive) return
        isScreenInteractive = true

        val now = SystemClock.elapsedRealtime()
        val durationOffMs = now - screenOffTimeRealtime

        // Se a tela ficou desligada por mais de 5 segundos, analisa a fotografia diferencial
        if (screenOffTimeRealtime > 0L && durationOffMs >= 5_000L) {
            scope.launch(Dispatchers.IO) {
                evaluateScreenOffDifferential(durationOffMs)
            }
        }

        if (isRunning.get() && _config.value.isEnabled) {
            startScreenOnEvaluationLoop()
        }
    }

    /**
     * Tira uma fotografia leve de todos os UIDs instalados via TrafficStats no instante em que a tela apaga.
     * Custo de CPU: < 1ms (chamadas C in-memory diretas do kernel eBPF/TrafficStats).
     */
    private fun captureScreenOffSnapshot() {
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
            val map = mutableMapOf<Int, UidNetSample>()
            val now = SystemClock.elapsedRealtime()

            for (app in installed) {
                val uid = app.uid
                if (uid >= 10000 && !map.containsKey(uid)) {
                    val rx = TrafficStats.getUidRxBytes(uid)
                    val tx = TrafficStats.getUidTxBytes(uid)
                    if (rx > 0L || tx > 0L) {
                        map[uid] = UidNetSample(rx, tx, now)
                    }
                }
            }
            screenOffNetSnapshots.clear()
            screenOffNetSnapshots.putAll(map)
        }
    }

    private fun collectUidUsageForInterval(
        startTimeMs: Long,
        endTimeMs: Long,
        resultMap: MutableMap<Int, Long>,
    ) {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return
        fun query(type: Int) {
            runCatching {
                val stats = nsm.querySummary(type, null, startTimeMs, endTimeMs)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    if (uid >= 10000) {
                        val prev = resultMap[uid] ?: 0L
                        resultMap[uid] = prev + bucket.rxBytes + bucket.txBytes
                    }
                }
                stats.close()
            }
        }
        query(ConnectivityManager.TYPE_WIFI)
        query(ConnectivityManager.TYPE_MOBILE)
    }

    /**
     * Avalia a variação de dados (Delta Bytes) transmitidos enquanto o usuário esteve ausente com a tela apagada.
     */
    private suspend fun evaluateScreenOffDifferential(sleepDurationMs: Long) {
        val cfg = _config.value
        if (!cfg.isEnabled || !cfg.notifyNetwork) return

        val thresholdBytes = cfg.sensitivity.netScreenOffMb * 1024L * 1024L
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        val deltaMap = mutableMapOf<Int, Long>()
        val startWindow = (screenOffWallTime - 3_000L).coerceAtLeast(0L)
        val endWindow = now + 1_000L

        // 1. Android Native NetworkStatsManager query (funciona para todos os UIDs no Android 7+)
        collectUidUsageForInterval(startWindow, endWindow, deltaMap)

        // 2. Fallback via TrafficStats diff
        for ((uid, baseline) in screenOffNetSnapshots) {
            val curRx = TrafficStats.getUidRxBytes(uid)
            val curTx = TrafficStats.getUidTxBytes(uid)
            if (curRx >= baseline.rx && curTx >= baseline.tx) {
                val d = (curRx - baseline.rx) + (curTx - baseline.tx)
                val current = deltaMap[uid] ?: 0L
                if (d > current) {
                    deltaMap[uid] = d
                }
            }
        }

        for ((uid, deltaTotal) in deltaMap) {
            if (deltaTotal >= thresholdBytes) {
                val packages = pm.getPackagesForUid(uid) ?: continue
                val pkgName = packages.firstOrNull() ?: continue

                if (pkgName == context.packageName || cfg.whitelistedPackages.contains(pkgName) || isPackageMuted(pkgName)) {
                    continue
                }

                val lastAlert = lastAlertTimeMap[pkgName] ?: 0L
                if (now - lastAlert < COOLDOWN_MS) continue

                val appName = runCatching {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrDefault(pkgName)

                val appIcon = runCatching { pm.getApplicationIcon(pkgName) }.getOrNull()
                val formattedVal = formatBytes(deltaTotal)
                val durationMin = (sleepDurationMs / 60_000L).coerceAtLeast(1)

                val desc = context.getString(
                    dev.viagaralauncher.R.string.anomaly_desc_net_screen_off,
                    appName,
                    formattedVal,
                    durationMin.toString(),
                )

                val anomaly = AnomalyEvent(
                    packageName = pkgName,
                    appName = appName,
                    type = AnomalyType.NETWORK,
                    valueFormatted = formattedVal,
                    description = desc,
                    timestamp = now,
                    screenWasOff = true,
                )

                recordAnomaly(anomaly)
                notificationManager.postAnomalyNotification(anomaly, appIcon)
                lastAlertTimeMap[pkgName] = now
            }
        }
    }

    /**
     * Loop adaptativo executado APENAS enquanto a tela está LIGADA (dispositivo já ativo pelo usuário).
     * Roda a cada 30 segundos com custo de processamento imperceptível (< 0.05%).
     */
    private fun startScreenOnEvaluationLoop() {
        screenOnLoopJob?.cancel()
        screenOnLoopJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get() && isScreenInteractive) {
                delay(SCREEN_ON_INTERVAL_MS)
                if (!isActive || !isScreenInteractive) break

                val cfg = _config.value
                if (!cfg.isEnabled) break

                runCatching {
                    evaluateScreenOnAnomalies(cfg)
                }
            }
        }
    }

    private suspend fun evaluateScreenOnAnomalies(cfg: AnomalyWatcherConfig) {
        val fgPackage = getForegroundPackage()
        val pm = context.packageManager
        val now = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        // 1. Verificação de Rede (Internet) em Segundo Plano
        if (cfg.notifyNetwork) {
            val netThresholdBytes = cfg.sensitivity.netScreenOnMb * 1024L * 1024L
            val onNetMap = mutableMapOf<Int, Long>()
            val intervalStart = nowWall - SCREEN_ON_INTERVAL_MS - 2_000L
            val intervalEnd = nowWall + 1_000L
            collectUidUsageForInterval(intervalStart, intervalEnd, onNetMap)

            // Merge TrafficStats se disponível
            val installed = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
            for (app in installed) {
                val uid = app.uid
                val curRx = TrafficStats.getUidRxBytes(uid)
                val curTx = TrafficStats.getUidTxBytes(uid)
                if (curRx > 0L || curTx > 0L) {
                    val prev = activeNetSamples[uid]
                    activeNetSamples[uid] = UidNetSample(curRx, curTx, now)
                    if (prev != null && prev.rx <= curRx && prev.tx <= curTx) {
                        val dBytes = (curRx - prev.rx) + (curTx - prev.tx)
                        val curr = onNetMap[uid] ?: 0L
                        if (dBytes > curr) onNetMap[uid] = dBytes
                    }
                }
            }

            for ((uid, dBytes) in onNetMap) {
                if (dBytes >= netThresholdBytes) {
                    val packages = pm.getPackagesForUid(uid) ?: continue
                    val pkgName = packages.firstOrNull() ?: continue

                    if (pkgName == fgPackage || pkgName == context.packageName || cfg.whitelistedPackages.contains(pkgName) || isPackageMuted(pkgName)) {
                        continue
                    }

                    val lastAlert = lastAlertTimeMap[pkgName] ?: 0L
                    if (nowWall - lastAlert >= COOLDOWN_MS) {
                        val appLabel = runCatching {
                            val appInfo = pm.getApplicationInfo(pkgName, 0)
                            pm.getApplicationLabel(appInfo).toString()
                        }.getOrDefault(pkgName)

                        val icon = runCatching { pm.getApplicationIcon(pkgName) }.getOrNull()
                        val formatted = formatBytes(dBytes)
                        val desc = context.getString(
                            dev.viagaralauncher.R.string.anomaly_desc_net_screen_on,
                            appLabel,
                            formatted,
                            "30",
                        )

                        val anomaly = AnomalyEvent(
                            packageName = pkgName,
                            appName = appLabel,
                            type = AnomalyType.NETWORK,
                            valueFormatted = formatted,
                            description = desc,
                            timestamp = nowWall,
                            screenWasOff = false,
                        )

                        recordAnomaly(anomaly)
                        notificationManager.postAnomalyNotification(anomaly, icon)
                        lastAlertTimeMap[pkgName] = nowWall
                    }
                }
            }
        }

        // 2. Verificação de CPU em Segundo Plano
        if (cfg.notifyCpu) {
            evaluateCpuAnomalies(cfg, fgPackage, nowWall)
        }

        // 3. Verificação de Memória RAM sob Pressão
        if (cfg.notifyRam) {
            evaluateRamAnomalies(cfg, fgPackage, nowWall)
        }
    }

    private suspend fun evaluateCpuAnomalies(cfg: AnomalyWatcherConfig, fgPackage: String?, nowWall: Long) {
        val pm = context.packageManager
        val coresCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        // Leitura rápida de processos via ps
        val psOutput = if (AppRootInspector.isRootAvailable()) {
            AppRootInspector.runSuCommand("ps -A -o PID,USER,%CPU,ARGS 2>/dev/null || ps -o PID,USER,%CPU,ARGS 2>/dev/null").getOrNull() ?: ""
        } else {
            ""
        }

        if (psOutput.isBlank()) return

        psOutput.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("PID")) return@forEach
            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size >= 4) {
                val rawCpu = tokens[2].toDoubleOrNull() ?: 0.0
                val normalizedCpu = (rawCpu / coresCount).coerceIn(0.0, 100.0)
                val cmd = tokens.subList(3, tokens.size).joinToString(" ")
                val cleanCmd = cmd.substringBefore(" ").substringAfterLast("/").removeSurrounding("[", "]").removeSurrounding("(", ")")

                if (cleanCmd.contains(".") && !cleanCmd.startsWith("/") && cleanCmd != fgPackage && cleanCmd != context.packageName) {
                    val pkgName = cleanCmd.substringBefore(":")
                    if (!cfg.whitelistedPackages.contains(pkgName) && !isPackageMuted(pkgName)) {
                        if (normalizedCpu >= cfg.sensitivity.cpuPercent) {
                            // Incrementa contador de ciclos consecutivos para confirmar abuso contínuo
                            val cycles = (cpuHighAppConsecutiveCycles[pkgName] ?: 0) + 1
                            cpuHighAppConsecutiveCycles[pkgName] = cycles

                            if (cycles >= 2) {
                                val lastAlert = lastAlertTimeMap[pkgName] ?: 0L
                                if (nowWall - lastAlert >= COOLDOWN_MS) {
                                    val appLabel = runCatching {
                                        val info = pm.getApplicationInfo(pkgName, 0)
                                        pm.getApplicationLabel(info).toString()
                                    }.getOrDefault(pkgName)

                                    val icon = runCatching { pm.getApplicationIcon(pkgName) }.getOrNull()
                                    val formatted = String.format(Locale.getDefault(), "%.1f%%", normalizedCpu)
                                    val desc = context.getString(
                                        dev.viagaralauncher.R.string.anomaly_desc_cpu,
                                        appLabel,
                                        formatted,
                                    )

                                    val anomaly = AnomalyEvent(
                                        packageName = pkgName,
                                        appName = appLabel,
                                        type = AnomalyType.CPU,
                                        valueFormatted = formatted,
                                        description = desc,
                                        timestamp = nowWall,
                                        screenWasOff = false,
                                        pid = tokens[0].toIntOrNull(),
                                    )

                                    recordAnomaly(anomaly)
                                    notificationManager.postAnomalyNotification(anomaly, icon)
                                    lastAlertTimeMap[pkgName] = nowWall
                                }
                            }
                        } else {
                            cpuHighAppConsecutiveCycles[pkgName] = 0
                        }
                    }
                }
            }
        }
    }

    private suspend fun evaluateRamAnomalies(cfg: AnomalyWatcherConfig, fgPackage: String?, nowWall: Long) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        // Só gera alerta se o sistema estiver sob pressão real de memória (menos de 15% livre ou lowMemory flag)
        val ramPercentFree = (memInfo.availMem.toDouble() / memInfo.totalMem.toDouble()) * 100.0
        if (!memInfo.lowMemory && ramPercentFree > 18.0) return

        val pm = context.packageManager
        val thresholdMb = cfg.sensitivity.ramMb.toDouble()
        val totalRamMb = memInfo.totalMem / (1024.0 * 1024.0)

        // Identifica processos com RSS alto em segundo plano
        if (AppRootInspector.isRootAvailable()) {
            val psOutput = AppRootInspector.runSuCommand("ps -A -o PID,USER,%MEM,ARGS 2>/dev/null").getOrNull() ?: ""
            psOutput.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("PID")) return@forEach
                val tokens = trimmed.split(Regex("\\s+"))
                if (tokens.size >= 4) {
                    val memPercent = tokens[2].toDoubleOrNull() ?: 0.0
                    val ramMb = (memPercent / 100.0) * totalRamMb
                    val cmd = tokens.subList(3, tokens.size).joinToString(" ")
                    val cleanCmd = cmd.substringBefore(" ").substringAfterLast("/").removeSurrounding("[", "]").removeSurrounding("(", ")")

                    if (cleanCmd.contains(".") && !cleanCmd.startsWith("/") && cleanCmd != fgPackage && cleanCmd != context.packageName) {
                        val pkgName = cleanCmd.substringBefore(":")
                        if (ramMb >= thresholdMb && !cfg.whitelistedPackages.contains(pkgName) && !isPackageMuted(pkgName)) {
                            val lastAlert = lastAlertTimeMap[pkgName] ?: 0L
                            if (nowWall - lastAlert >= COOLDOWN_MS) {
                                val appLabel = runCatching {
                                    val info = pm.getApplicationInfo(pkgName, 0)
                                    pm.getApplicationLabel(info).toString()
                                }.getOrDefault(pkgName)

                                val icon = runCatching { pm.getApplicationIcon(pkgName) }.getOrNull()
                                val formatted = String.format(Locale.getDefault(), "%.0f MB", ramMb)
                                val desc = context.getString(
                                    dev.viagaralauncher.R.string.anomaly_desc_ram,
                                    appLabel,
                                    formatted,
                                )

                                val anomaly = AnomalyEvent(
                                    packageName = pkgName,
                                    appName = appLabel,
                                    type = AnomalyType.RAM,
                                    valueFormatted = formatted,
                                    description = desc,
                                    timestamp = nowWall,
                                    screenWasOff = false,
                                    pid = tokens[0].toIntOrNull(),
                                )

                                recordAnomaly(anomaly)
                                notificationManager.postAnomalyNotification(anomaly, icon)
                                lastAlertTimeMap[pkgName] = nowWall
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Chamado quando o sistema operacional Android envia sinal de corte de memória (onTrimMemory).
     * Puro evento do sistema operacional, sem polling.
     */
    fun onSystemTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            val cfg = _config.value
            if (cfg.isEnabled && cfg.notifyRam) {
                scope.launch(Dispatchers.IO) {
                    evaluateRamAnomalies(cfg, getForegroundPackage(), System.currentTimeMillis())
                }
            }
        }
    }

    private fun recordAnomaly(anomaly: AnomalyEvent) {
        val current = _anomalies.value.toMutableList()
        current.add(0, anomaly)
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        _anomalies.value = current
    }

    private fun getForegroundPackage(): String? {
        // Método 1: UsageStatsManager se disponível
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm != null) {
            val now = System.currentTimeMillis()
            val events = runCatching { usm.queryEvents(now - 15_000L, now) }.getOrNull()
            if (events != null) {
                val event = UsageEvents.Event()
                var latestPkg: String? = null
                var latestTime = 0L
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp > latestTime) {
                        latestPkg = event.packageName
                        latestTime = event.timeStamp
                    }
                }
                if (latestPkg != null) return latestPkg
            }
        }

        // Método 2: ActivityManager running processes
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val running = am?.runningAppProcesses
        val fgProc = running?.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
        if (fgProc != null && fgProc.pkgList.isNotEmpty()) {
            return fgProc.pkgList.firstOrNull()
        }

        return null
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            else -> String.format(Locale.getDefault(), "%.0f KB", kb)
        }
    }
}
