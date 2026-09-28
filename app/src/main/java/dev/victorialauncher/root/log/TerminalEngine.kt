// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root.log

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class TerminalType(val title: String) {
    ROOT("Superusuário (Root)"),
    DIRECT_ADB("ADB / Permissão Concedida"),
    AUTO("Detecção Automática"),
}

object TerminalEngine {

    fun hasReadLogsPermission(context: Context): Boolean {
        return context.checkCallingOrSelfPermission(android.Manifest.permission.READ_LOGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
        )
        if (paths.any { File(it).exists() }) {
            return@withContext true
        }

        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exit = process.waitFor()
            exit == 0
        }.getOrDefault(false)
    }

    suspend fun grantReadLogsViaRoot(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val pkg = context.packageName
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm grant $pkg android.permission.READ_LOGS"))
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    fun getAdbCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"
    }

    suspend fun clearLogcatBuffers(context: Context, type: TerminalType) = withContext(Dispatchers.IO) {
        runCatching {
            val effectiveType = resolveEffectiveTerminal(context, type)
            if (effectiveType == TerminalType.ROOT) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -c")).waitFor()
            } else {
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
            }
        }
    }

    suspend fun startLogcatStream(context: Context, type: TerminalType): Process = withContext(Dispatchers.IO) {
        val effectiveType = resolveEffectiveTerminal(context, type)
        if (effectiveType == TerminalType.ROOT) {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -v uid -v epoch"))
        } else {
            Runtime.getRuntime().exec(arrayOf("logcat", "-v", "uid", "-v", "epoch"))
        }
    }

    suspend fun resolveEffectiveTerminal(context: Context, preferred: TerminalType): TerminalType {
        if (preferred == TerminalType.ROOT) return TerminalType.ROOT
        if (preferred == TerminalType.DIRECT_ADB) return TerminalType.DIRECT_ADB

        // AUTO
        return if (hasReadLogsPermission(context)) {
            TerminalType.DIRECT_ADB
        } else if (isRootAvailable()) {
            TerminalType.ROOT
        } else {
            TerminalType.DIRECT_ADB
        }
    }
}
