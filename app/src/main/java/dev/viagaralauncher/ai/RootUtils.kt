// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

object RootUtils {

    private const val TAG = "RootUtils"

    /**
     * Verifica se o binário su existe no sistema.
     */
    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    /**
     * Executa um comando com privilégios de root (su).
     * Retorna um Pair(sucesso: Boolean, output: String).
     */
    suspend fun executeRootCommand(command: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val errorOutput = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            val isSuccess = exitCode == 0
            val fullResult = if (isSuccess) output.toString().trim() else errorOutput.toString().trim()

            Log.d(TAG, "Root command '$command' exited with $exitCode: $fullResult")
            Pair(isSuccess, fullResult)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao executar comando root: ${e.message}", e)
            Pair(false, e.message ?: "Erro desconhecido")
        }
    }

    /**
     * Executa um comando shell padrão sem privilégios de root.
     */
    suspend fun executeShellCommand(command: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()
            Pair(exitCode == 0, output.toString().trim())
        } catch (e: Exception) {
            Pair(false, e.message ?: "")
        }
    }
}
