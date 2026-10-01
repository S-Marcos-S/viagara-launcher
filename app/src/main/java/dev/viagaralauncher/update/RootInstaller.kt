// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootInstaller {

    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/apex/com.android.runtime/bin/su",
    )

    /**
     * Checks whether a 'su' binary exists on the system (Magisk, KernelSU, APatch, SuperSU, etc.).
     */
    fun isRootAvailable(): Boolean {
        try {
            for (p in SU_PATHS) {
                if (File(p).exists()) return true
            }
            val pathEnv = System.getenv("PATH") ?: ""
            for (dir in pathEnv.split(":")) {
                if (dir.isNotBlank() && File(dir.trim(), "su").exists()) {
                    return true
                }
            }
        } catch (_: Throwable) {}
        return false
    }

    /**
     * Attempts silent package installation via root ('pm install -r -d -g -t').
     * Bypasses the system PackageInstaller UI and Google Play Protect scan.
     */
    suspend fun installApkViaRoot(
        context: Context,
        apkUri: Uri,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "viagara_update_temp.apk")
        try {
            // 1. Copy the APK from content/file URI to internal cache
            context.contentResolver.openInputStream(apkUri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Não foi possível ler o arquivo da atualização."))

            if (!cacheFile.exists() || cacheFile.length() <= 0) {
                return@withContext Result.failure(IllegalStateException("Arquivo temporário da atualização vazio ou inválido."))
            }

            // 2. Prepare staging path in /data/local/tmp (readable by pm and system)
            val stagedApkPath = "/data/local/tmp/viagara_update_${System.currentTimeMillis()}.apk"
            val cachePath = cacheFile.absolutePath

            // 3. Execute installation command sequence via su
            val shellScript = """
                cp "$cachePath" "$stagedApkPath" && \
                chmod 644 "$stagedApkPath" && \
                pm install -r -d -g -t "$stagedApkPath"
                EXIT_CODE=$?
                rm -f "$stagedApkPath"
                exit ${'$'}EXIT_CODE
            """.trimIndent()

            val process = ProcessBuilder("su", "-c", shellScript)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.contains("Success", ignoreCase = true)) {
                // Relaunch the main launcher activity
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                } catch (_: Throwable) {}

                Result.success(Unit)
            } else {
                val errorMsg = output.lines().filter { it.isNotBlank() }.joinToString("\n").ifBlank {
                    "Erro ao executar pm install via su (exit code $exitCode)"
                }
                Result.failure(RuntimeException(errorMsg))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            try {
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
            } catch (_: Throwable) {}
        }
    }
}
