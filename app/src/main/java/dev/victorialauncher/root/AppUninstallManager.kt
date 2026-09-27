// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import dev.victorialauncher.update.RootInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages app uninstallation logic, handling root-based uninstallation for superusers
 * and system app detection with proper fallback to standard PackageInstaller.
 */
object AppUninstallManager {

    /**
     * Checks whether root capability (su binary) is available on the device.
     */
    fun isRootAvailable(): Boolean {
        return RootInstaller.isRootAvailable()
    }

    /**
     * Checks if the specified package is a system application.
     */
    fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            isSystem || isUpdatedSystem
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Dispatches standard uninstallation via the system PackageInstaller UI.
     */
    fun uninstallStandard(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Fallback for older or customized OEM ROMs
            try {
                @Suppress("DEPRECATION")
                val fallbackIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Uninstalls an application silently using root shell commands.
     * Handles both regular user applications and system applications (/system, /system_ext, /product, /vendor).
     * For system applications, executing `pm uninstall <pkg>` first uninstalls updates if any,
     * then `pm uninstall --user 0 <pkg>` removes the application for the primary user cleanly without
     * causing read-only filesystem errors on modern SAR/EROFS partitions.
     */
    suspend fun uninstallViaRoot(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val shellScript = """
                am force-stop "$packageName" 2>/dev/null
                OUT_PM1=${'$'}(pm uninstall "$packageName" 2>&1)
                if echo "${'$'}OUT_PM1" | grep -iq "Success"; then
                    echo "Success"
                    exit 0
                fi
                OUT_PM2=${'$'}(pm uninstall --user 0 "$packageName" 2>&1)
                if echo "${'$'}OUT_PM2" | grep -iq "Success"; then
                    echo "Success"
                    exit 0
                fi
                echo "${'$'}OUT_PM1 | ${'$'}OUT_PM2"
                exit 1
            """.trimIndent()

            val process = ProcessBuilder("su", "-c", shellScript)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0 || output.contains("Success", ignoreCase = true)) {
                Unit
            } else {
                val errorDetails = output.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Exit code $exitCode" }
                throw RuntimeException(errorDetails)
            }
        }
    }
}
