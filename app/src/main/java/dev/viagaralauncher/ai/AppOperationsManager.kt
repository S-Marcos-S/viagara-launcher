// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ai

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

data class InstalledAppInfo(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean
)

class AppOperationsManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    companion object {
        private const val TAG = "AppOperationsManager"
    }

    /**
     * Retorna a lista de todos os aplicativos instalados no aparelho.
     */
    fun getInstalledApps(): List<InstalledAppInfo> {
        val apps = mutableListOf<InstalledAppInfo>()
        val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in installedPackages) {
            val name = packageManager.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            apps.add(InstalledAppInfo(name = name, packageName = appInfo.packageName, isSystemApp = isSystem))
        }
        return apps
    }

    /**
     * Encontra o pacote mais próximo com base em um nome informado pelo usuário.
     */
    fun findAppByNameOrPackage(query: String): InstalledAppInfo? {
        val cleanQuery = query.trim().lowercase()
        val allApps = getInstalledApps()

        // 1. Coincidência exata por pacote
        allApps.find { it.packageName.equals(cleanQuery, ignoreCase = true) }?.let { return it }

        // 2. Coincidência exata por nome
        allApps.find { it.name.equals(cleanQuery, ignoreCase = true) }?.let { return it }

        // 3. Nome contém a query
        allApps.find { it.name.lowercase().contains(cleanQuery) }?.let { return it }

        // 4. Pacote contém a query
        return allApps.find { it.packageName.lowercase().contains(cleanQuery) }
    }

    /**
     * Abre o aplicativo desejado.
     */
    fun openApp(packageName: String): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir app $packageName: ${e.message}")
            false
        }
    }

    /**
     * Abre a tela de informações do aplicativo nas configurações do sistema.
     */
    fun openAppSettings(packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir configurações do app: ${e.message}")
            false
        }
    }

    /**
     * Solicita a desinstalação nativa padrão de um aplicativo (abre o diálogo do sistema).
     */
    fun requestUninstall(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar desinstalação de $packageName: ${e.message}")
            false
        }
    }

    /**
     * Desinstala um aplicativo usando privilégios de Root (útil para apps de sistema).
     * Usa 'pm uninstall --user 0 <package>' que remove o app do perfil do usuário com segurança.
     */
    suspend fun uninstallViaRoot(packageName: String): Pair<Boolean, String> {
        val cmd = "pm uninstall --user 0 $packageName"
        val (success, output) = RootUtils.executeRootCommand(cmd)
        return if (success && output.contains("Success", ignoreCase = true)) {
            Pair(true, "Aplicativo $packageName desinstalado com sucesso via Root.")
        } else {
            Pair(false, output)
        }
    }

    /**
     * Força a parada do aplicativo (Force Stop) via Root ou abre a tela de detalhes.
     */
    suspend fun forceStopApp(packageName: String): Boolean {
        if (RootUtils.isRootAvailable()) {
            val (success, _) = RootUtils.executeRootCommand("am force-stop $packageName")
            if (success) return true
        }
        return openAppSettings(packageName)
    }
}
