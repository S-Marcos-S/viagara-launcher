// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ai

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import dev.viagaralauncher.agenda.AgendaActivity
import dev.viagaralauncher.agenda.AgendaActivityType
import dev.viagaralauncher.agenda.AgendaNotificationSettings
import dev.viagaralauncher.agenda.AgendaNotificationType
import dev.viagaralauncher.agenda.AgendaRepository
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

sealed class AiActionResult {
    data class Success(val message: String) : AiActionResult()
    data class Error(val message: String) : AiActionResult()
    data class ConfirmationRequired(
        val title: String,
        val description: String,
        val onConfirm: suspend () -> AiActionResult,
        val onCancel: suspend () -> AiActionResult
    ) : AiActionResult()
    data class Answer(val text: String) : AiActionResult()
}

class AiActionDispatcher(private val context: Context) {

    private val settingsController = DeviceSettingsController(context)
    private val appManager = AppOperationsManager(context)
    private val agendaRepository = AgendaRepository(context)

    companion object {
        private const val TAG = "AiActionDispatcher"
    }

    /**
     * Executa a Tool solicitada pela IA.
     */
    suspend fun dispatchAction(jsonString: String): AiActionResult {
        return try {
            val root = JSONObject(jsonString)
            val action = root.optString("action", root.optString("tool", "")).lowercase()

            when (action) {
                // =============================================================
                // APPS & SISTEMA
                // =============================================================
                "open_app" -> {
                    val appName = root.optString("app_name", "")
                    val pkg = root.optString("package_name", "")
                    val target = if (pkg.isNotBlank()) pkg else appName
                    val appInfo = appManager.findAppByNameOrPackage(target)
                    if (appInfo != null) {
                        if (appManager.openApp(appInfo.packageName)) {
                            AiActionResult.Success("Abrindo ${appInfo.name}...")
                        } else {
                            AiActionResult.Error("Não foi possível abrir ${appInfo.name}.")
                        }
                    } else {
                        AiActionResult.Error("Aplicativo '$appName' não encontrado.")
                    }
                }

                "uninstall_app" -> {
                    val appName = root.optString("app_name", "")
                    val pkg = root.optString("package_name", "")
                    val target = if (pkg.isNotBlank()) pkg else appName
                    val appInfo = appManager.findAppByNameOrPackage(target)

                    if (appInfo == null) {
                        return AiActionResult.Error("Aplicativo '$appName' não encontrado para desinstalação.")
                    }

                    if (appInfo.isSystemApp) {
                        // É app de sistema! Exige confirmação especial e verificação de root
                        val hasRoot = RootUtils.isRootAvailable()
                        if (!hasRoot) {
                            return AiActionResult.Error("O aplicativo '${appInfo.name}' é um aplicativo do sistema e este dispositivo não possui acesso Root.")
                        }

                        AiActionResult.ConfirmationRequired(
                            title = "Desinstalar App de Sistema",
                            description = "O aplicativo '${appInfo.name}' (${appInfo.packageName}) faz parte do sistema Android. Tem certeza que deseja forçar a desinstalação via Root?",
                            onConfirm = {
                                val (ok, msg) = appManager.uninstallViaRoot(appInfo.packageName)
                                if (ok) AiActionResult.Success(msg) else AiActionResult.Error("Falha na desinstalação Root: $msg")
                            },
                            onCancel = {
                                AiActionResult.Answer("Ação cancelada pelo usuário.")
                            }
                        )
                    } else {
                        // App normal de usuário
                        appManager.requestUninstall(appInfo.packageName)
                        AiActionResult.Success("Solicitando desinstalação de ${appInfo.name}...")
                    }
                }

                "app_info" -> {
                    val target = root.optString("app_name", "")
                    val appInfo = appManager.findAppByNameOrPackage(target)
                    if (appInfo != null) {
                        appManager.openAppSettings(appInfo.packageName)
                        AiActionResult.Success("Abrindo informações de ${appInfo.name}.")
                    } else {
                        AiActionResult.Error("Aplicativo '$target' não encontrado.")
                    }
                }

                "force_stop_app" -> {
                    val target = root.optString("app_name", "")
                    val appInfo = appManager.findAppByNameOrPackage(target)
                    if (appInfo != null) {
                        appManager.forceStopApp(appInfo.packageName)
                        AiActionResult.Success("Forçando parada de ${appInfo.name}...")
                    } else {
                        AiActionResult.Error("Aplicativo '$target' não encontrado.")
                    }
                }

                // =============================================================
                // HARDWARE & TOGGLES
                // =============================================================
                "toggle_flashlight" -> {
                    val state = if (root.has("state")) root.optBoolean("state") else null
                    if (settingsController.toggleFlashlight(state)) {
                        val msg = if (state == true) "Lanterna ligada." else if (state == false) "Lanterna desligada." else "Lanterna alternada."
                        AiActionResult.Success(msg)
                    } else {
                        AiActionResult.Error("Não foi possível acessar a lanterna.")
                    }
                }

                "set_volume" -> {
                    val type = root.optString("type", "media")
                    val percent = root.optInt("percent", 50)
                    settingsController.setVolume(type, percent)
                    AiActionResult.Success("Volume de $type ajustado para $percent%.")
                }

                "set_ringer_mode" -> {
                    val mode = root.optString("mode", "normal")
                    settingsController.setRingerMode(mode)
                    AiActionResult.Success("Modo de som alterado para $mode.")
                }

                "toggle_dnd" -> {
                    val enable = root.optBoolean("enable", true)
                    if (settingsController.toggleDoNotDisturb(enable)) {
                        AiActionResult.Success(if (enable) "Modo Não Perturbe ativado." else "Modo Não Perturbe desativado.")
                    } else {
                        AiActionResult.Error("Permissão necessária para alterar o Modo Não Perturbe.")
                    }
                }

                "set_brightness" -> {
                    val percent = root.optInt("percent", 50)
                    val auto = root.optBoolean("auto", false)
                    if (settingsController.setScreenBrightness(percent, auto)) {
                        AiActionResult.Success(if (auto) "Brilho automático ativado." else "Brilho ajustado para $percent%.")
                    } else {
                        AiActionResult.Error("Permissão necessária para alterar configurações de brilho.")
                    }
                }

                "toggle_auto_rotate" -> {
                    val enable = root.optBoolean("enable", true)
                    if (settingsController.setAutoRotate(enable)) {
                        AiActionResult.Success(if (enable) "Rotação automática ativada." else "Rotação de tela travada.")
                    } else {
                        AiActionResult.Error("Permissão necessária para alterar rotação.")
                    }
                }

                "toggle_wifi" -> {
                    val enable = root.optBoolean("enable", true)
                    settingsController.setWifi(enable)
                    AiActionResult.Success(if (enable) "Wi-Fi ativado." else "Wi-Fi desativado.")
                }

                "toggle_bluetooth" -> {
                    val enable = root.optBoolean("enable", true)
                    settingsController.setBluetooth(enable)
                    AiActionResult.Success(if (enable) "Bluetooth ativado." else "Bluetooth desativado.")
                }

                "toggle_mobile_data" -> {
                    val enable = root.optBoolean("enable", true)
                    settingsController.setMobileData(enable)
                    AiActionResult.Success(if (enable) "Dados móveis ativados." else "Dados móveis desativados.")
                }

                "toggle_airplane_mode" -> {
                    val enable = root.optBoolean("enable", true)
                    settingsController.setAirplaneMode(enable)
                    AiActionResult.Success(if (enable) "Modo avião ativado." else "Modo avião desativado.")
                }

                "toggle_location" -> {
                    val enable = root.optBoolean("enable", true)
                    settingsController.setLocation(enable)
                    AiActionResult.Success(if (enable) "Localização ativada." else "Localização desativada.")
                }

                "toggle_hotspot" -> {
                    val enable = root.optBoolean("enable", true)
                    settingsController.setHotspot(enable)
                    AiActionResult.Success(if (enable) "Roteador Wi-Fi ativado." else "Roteador Wi-Fi desativado.")
                }

                "toggle_nfc" -> {
                    val enable = root.optBoolean("enable", true)
                    settingsController.setNfc(enable)
                    AiActionResult.Success(if (enable) "NFC ativado." else "NFC desativado.")
                }

                "toggle_battery_saver" -> {
                    val enable = root.optBoolean("enable", true)
                    settingsController.setBatterySaver(enable)
                    AiActionResult.Success(if (enable) "Economia de bateria ativada." else "Economia de bateria desativada.")
                }

                "reboot_device" -> {
                    val mode = root.optString("mode", "normal")
                    AiActionResult.ConfirmationRequired(
                        title = "Reiniciar Dispositivo",
                        description = "Deseja realmente reiniciar o aparelho em modo $mode?",
                        onConfirm = {
                            val (ok, msg) = settingsController.rebootDevice(mode)
                            if (ok) AiActionResult.Success("Reiniciando aparelho...") else AiActionResult.Error("Falha ao reiniciar: $msg")
                        },
                        onCancel = {
                            AiActionResult.Answer("Reinicialização cancelada.")
                        }
                    )
                }

                // =============================================================
                // AGENDA, TAREFAS E ALARMES
                // =============================================================
                "create_task" -> {
                    val title = root.optString("title", "Novo Lembrete")
                    val dateStr = root.optString("date", LocalDate.now().toString())
                    val timeStr = root.optString("time", "")
                    val description = root.optString("description", "")
                    val hasAlarm = root.optBoolean("alarm", true)

                    val startTime = if (timeStr.isNotBlank()) {
                        try { LocalTime.parse(timeStr) } catch (_: Exception) { null }
                    } else null

                    val activity = AgendaActivity(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        description = description.ifBlank { null },
                        date = dateStr,
                        startTime = startTime,
                        endTime = null,
                        isAllDay = startTime == null,
                        location = null,
                        categoryColor = "1",
                        activityType = AgendaActivityType.TASK,
                        recurrenceRule = root.optString("recurrence", "NONE"),
                        notificationSettings = AgendaNotificationSettings(
                            isEnabled = hasAlarm,
                            notificationType = if (hasAlarm) AgendaNotificationType.BEFORE_ACTIVITY else AgendaNotificationType.NONE,
                            notificationTime = startTime,
                            customMinutesBefore = null
                        )
                    )

                    agendaRepository.saveActivity(activity)
                    AiActionResult.Success("Lembrete '$title' agendado para $dateStr ${timeStr.ifBlank { "(dia todo)" }}.")
                }

                "set_alarm" -> {
                    val hour = root.optInt("hour", 7)
                    val minute = root.optInt("minute", 0)
                    val message = root.optString("message", "Alarme")

                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    val formattedTime = String.format("%02d:%02d", hour, minute)
                    AiActionResult.Success("Despertador definido para as $formattedTime ($message).")
                }

                // =============================================================
                // RESPOSTA GERAL
                // =============================================================
                "answer", "chat" -> {
                    val text = root.optString("text", root.optString("message", "Comando processado."))
                    AiActionResult.Answer(text)
                }

                else -> {
                    val message = root.optString("text", root.optString("message", ""))
                    if (message.isNotBlank()) {
                        AiActionResult.Answer(message)
                    } else {
                        AiActionResult.Error("Ação '$action' não reconhecida.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao despachar ação: ${e.message}", e)
            AiActionResult.Error("Erro ao processar comando: ${e.message}")
        }
    }
}
