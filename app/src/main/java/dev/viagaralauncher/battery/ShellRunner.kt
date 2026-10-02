// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShellRunner(private val context: Context) {

    companion object {
        private const val TAG = "BatteryShellRunner"
        private const val CMD_TIMEOUT_SEC = 25L
        private const val MODE_CACHE_MS = 10_000L

        fun hasDumpPermission(context: Context): Boolean {
            return context.checkCallingOrSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED
        }

        fun hasBatteryStatsPermission(context: Context): Boolean {
            return context.checkCallingOrSelfPermission(Manifest.permission.BATTERY_STATS) == PackageManager.PERMISSION_GRANTED
        }
    }

    enum class Mode { ROOT, ADB, NONE }

    data class ShellResult(
        val output: String,
        val mode: Mode
    )

    sealed class Outcome {
        data class Success(val output: String, val mode: Mode) : Outcome()
        data class Failure(val mode: Mode, val message: String) : Outcome()
    }

    private val modeLock = Mutex()

    @Volatile
    private var cachedMode: Mode? = null

    @Volatile
    private var cachedModeAt = 0L

    @Volatile
    private var hasAttemptedPermissionGrant = false

    suspend fun run(cmd: String): ShellResult? =
        (exec(cmd) as? Outcome.Success)?.let { ShellResult(it.output, it.mode) }

    suspend fun exec(cmd: String, allowEmpty: Boolean = false): Outcome = withContext(Dispatchers.IO) {
        var lastFailure: Outcome.Failure? = null

        fun usable(out: String?): Boolean =
            out != null && (allowEmpty || out.isNotBlank()) && !isErrorOutput(out)

        val isRoot = RootBatteryStatsCollector.isRootAvailable()

        // If root is available, ensure DUMP / BATTERY_STATS are granted to this app for direct fast access
        if (isRoot && !hasAttemptedPermissionGrant) {
            hasAttemptedPermissionGrant = true
            try {
                val pkg = context.packageName
                RootBatteryStatsCollector.runAsRoot("pm grant $pkg android.permission.DUMP")
                RootBatteryStatsCollector.runAsRoot("pm grant $pkg android.permission.BATTERY_STATS")
                RootBatteryStatsCollector.runAsRoot("pm grant $pkg android.permission.PACKAGE_USAGE_STATS")
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    RootBatteryStatsCollector.runAsRoot("pm grant $pkg android.permission.POST_NOTIFICATIONS")
                }
            } catch (_: Exception) {}
        }

        // If root is available, execute via su
        if (isRoot) {
            val rootOut = RootBatteryStatsCollector.runAsRoot(cmd)
            if (usable(rootOut)) {
                Log.d(TAG, "Ran via ROOT: $cmd (${rootOut!!.length} chars)")
                return@withContext Outcome.Success(rootOut, Mode.ROOT)
            }
            lastFailure = Outcome.Failure(Mode.ROOT, "Root command returned empty or unusable output")
        }

        // Try direct ADB/dump if permissions are granted
        if (hasDumpPermission(context) || hasBatteryStatsPermission(context)) {
            val directOut = runDirect(cmd)
            if (usable(directOut)) {
                Log.d(TAG, "Ran via ADB/Direct: $cmd (${directOut!!.length} chars)")
                return@withContext Outcome.Success(directOut, Mode.ADB)
            }
        }

        // Fallback: try direct run even if permission check was strict
        val fallbackOut = runDirect(cmd)
        if (usable(fallbackOut)) {
            return@withContext Outcome.Success(fallbackOut!!, Mode.ADB)
        }

        Log.w(TAG, "All runners failed for: $cmd")
        lastFailure ?: Outcome.Failure(Mode.NONE, "Acesso privilegiado (Root ou DUMP via ADB) não disponível.")
    }

    private fun runDirect(cmd: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
            process.waitFor(CMD_TIMEOUT_SEC, TimeUnit.SECONDS)
            sb.toString().trim()
        } catch (e: Exception) {
            Log.d(TAG, "runDirect failed for: $cmd", e)
            null
        }
    }

    private fun isErrorOutput(out: String): Boolean =
        out.startsWith("ERROR") || out.contains("Permission Denial:") || out.contains("SecurityException") || out.contains("Can't find service")

    suspend fun detectMode(forceRefresh: Boolean = false): Mode {
        if (!forceRefresh) {
            cachedMode?.let {
                if (SystemClock.elapsedRealtime() - cachedModeAt < MODE_CACHE_MS) return it
            }
        }
        return modeLock.withLock {
            if (!forceRefresh) {
                cachedMode?.let {
                    if (SystemClock.elapsedRealtime() - cachedModeAt < MODE_CACHE_MS) {
                        return@withLock it
                    }
                }
            }
            val mode = probeMode()
            cachedMode = mode
            cachedModeAt = SystemClock.elapsedRealtime()
            mode
        }
    }

    private suspend fun probeMode(): Mode = withContext(Dispatchers.IO) {
        if (RootBatteryStatsCollector.isRootAvailable()) return@withContext Mode.ROOT
        if (hasDumpPermission(context) || hasBatteryStatsPermission(context)) {
            val out = runDirect("dumpsys battery")
            if (!out.isNullOrBlank() && !isErrorOutput(out)) return@withContext Mode.ADB
        }
        Mode.NONE
    }

    fun invalidateMode() {
        cachedMode = null
        cachedModeAt = 0L
        RootBatteryStatsCollector.invalidateRootCache()
    }

    suspend fun hasAnyPrivilegedAccess(): Boolean = detectMode() != Mode.NONE
}
