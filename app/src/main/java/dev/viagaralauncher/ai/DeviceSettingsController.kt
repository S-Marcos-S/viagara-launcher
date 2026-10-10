// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ai

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

class DeviceSettingsController(private val context: Context) {

    companion object {
        private const val TAG = "DeviceSettingsController"
        private var isFlashlightOn = false
    }

    // =========================================================================
    // 1. LANTERNA (FLASHLIGHT / TORCH)
    // =========================================================================

    fun toggleFlashlight(desiredState: Boolean? = null): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false
            val newState = desiredState ?: !isFlashlightOn
            cameraManager.setTorchMode(cameraId, newState)
            isFlashlightOn = newState
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao controlar lanterna: ${e.message}")
            false
        }
    }

    // =========================================================================
    // 2. VOLUME E ÁUDIO
    // =========================================================================

    fun setVolume(streamType: String, percent: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val stream = when (streamType.lowercase()) {
                "media", "music", "musica" -> AudioManager.STREAM_MUSIC
                "ring", "toque", "chamada" -> AudioManager.STREAM_RING
                "alarm", "alarme", "despertador" -> AudioManager.STREAM_ALARM
                "notification", "notificacao" -> AudioManager.STREAM_NOTIFICATION
                else -> AudioManager.STREAM_MUSIC
            }

            val maxVolume = audioManager.getStreamMaxVolume(stream)
            val targetVolume = ((percent.coerceIn(0, 100) / 100f) * maxVolume).toInt()
            audioManager.setStreamVolume(stream, targetVolume, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ajustar volume: ${e.message}")
            false
        }
    }

    fun setRingerMode(mode: String): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            when (mode.lowercase()) {
                "silent", "mudo", "silencioso" -> audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                "vibrate", "vibrar", "vibracao" -> audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "normal", "som" -> audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao mudar modo de toque: ${e.message}")
            false
        }
    }

    // =========================================================================
    // 3. NÃO PERTURBE (DO NOT DISTURB / DND)
    // =========================================================================

    fun toggleDoNotDisturb(enable: Boolean): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return try {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val filter = if (enable) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL
                notificationManager.setInterruptionFilter(filter)
                true
            } else {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alterar Não Perturbe: ${e.message}")
            false
        }
    }

    // =========================================================================
    // 4. BRILHO DA TELA
    // =========================================================================

    fun setScreenBrightness(percent: Int, auto: Boolean = false): Boolean {
        return try {
            if (Settings.System.canWrite(context)) {
                val cr = context.contentResolver
                if (auto) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                } else {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    val brightnessValue = ((percent.coerceIn(0, 100) / 100f) * 255).toInt()
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
                }
                true
            } else {
                openWriteSettingsPermission()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ajustar brilho da tela: ${e.message}")
            false
        }
    }

    // =========================================================================
    // 5. ROTAÇÃO AUTOMÁTICA DA TELA
    // =========================================================================

    fun setAutoRotate(enable: Boolean): Boolean {
        return try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (enable) 1 else 0)
                true
            } else {
                openWriteSettingsPermission()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ajustar rotação automática: ${e.message}")
            false
        }
    }

    // =========================================================================
    // 6. WI-FI
    // =========================================================================

    suspend fun setWifi(enable: Boolean): Boolean {
        if (RootUtils.isRootAvailable()) {
            val cmd = if (enable) "svc wifi enable" else "svc wifi disable"
            val (success, _) = RootUtils.executeRootCommand(cmd)
            if (success) return true
        }

        // Sem root: abrir painel nativo de Wi-Fi
        return openSettingsPanel(Settings.ACTION_WIFI_SETTINGS)
    }

    // =========================================================================
    // 7. BLUETOOTH
    // =========================================================================

    suspend fun setBluetooth(enable: Boolean): Boolean {
        if (RootUtils.isRootAvailable()) {
            val cmd = if (enable) "cmd bluetooth enable" else "cmd bluetooth disable"
            val (success, _) = RootUtils.executeRootCommand(cmd)
            if (success) return true
        }

        @Suppress("DEPRECATION")
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) {
            try {
                if (enable && !adapter.isEnabled) {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return true
                }
            } catch (_: SecurityException) {}
        }

        return openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    // =========================================================================
    // 8. DADOS MÓVEIS (MOBILE DATA)
    // =========================================================================

    suspend fun setMobileData(enable: Boolean): Boolean {
        if (RootUtils.isRootAvailable()) {
            val cmd = if (enable) "svc data enable" else "svc data disable"
            val (success, _) = RootUtils.executeRootCommand(cmd)
            if (success) return true
        }
        return openSettingsPanel(Settings.ACTION_DATA_ROAMING_SETTINGS)
    }

    // =========================================================================
    // 9. MODO AVIÃO (AIRPLANE MODE)
    // =========================================================================

    suspend fun setAirplaneMode(enable: Boolean): Boolean {
        if (RootUtils.isRootAvailable()) {
            val cmd = if (enable) "cmd connectivity airplane-mode enable" else "cmd connectivity airplane-mode disable"
            val (success, _) = RootUtils.executeRootCommand(cmd)
            if (success) return true
        }
        return openSettingsPanel(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    }

    // =========================================================================
    // 10. LOCALIZAÇÃO / GPS
    // =========================================================================

    suspend fun setLocation(enable: Boolean): Boolean {
        if (RootUtils.isRootAvailable()) {
            // 3 = High accuracy (GPS + Network), 0 = Off
            val mode = if (enable) 3 else 0
            val (success, _) = RootUtils.executeRootCommand("settings put secure location_mode $mode")
            if (success) return true
        }
        return openSettingsPanel(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    // =========================================================================
    // 11. HOTSPOT / PONTO DE ACESSO
    // =========================================================================

    suspend fun setHotspot(enable: Boolean): Boolean {
        if (RootUtils.isRootAvailable()) {
            val cmd = if (enable) {
                "cmd connectivity tethering start-tethering wifi"
            } else {
                "cmd connectivity tethering stop-tethering wifi"
            }
            val (success, _) = RootUtils.executeRootCommand(cmd)
            if (success) return true
        }
        return openSettingsPanel(Settings.ACTION_WIRELESS_SETTINGS)
    }

    // =========================================================================
    // 12. NFC
    // =========================================================================

    suspend fun setNfc(enable: Boolean): Boolean {
        if (RootUtils.isRootAvailable()) {
            val cmd = if (enable) "svc nfc enable" else "svc nfc disable"
            val (success, _) = RootUtils.executeRootCommand(cmd)
            if (success) return true
        }
        return openSettingsPanel(Settings.ACTION_NFC_SETTINGS)
    }

    // =========================================================================
    // 13. ECONOMIA DE BATERIA (BATTERY SAVER)
    // =========================================================================

    suspend fun setBatterySaver(enable: Boolean): Boolean {
        if (RootUtils.isRootAvailable()) {
            val cmd = "settings put global low_power ${if (enable) 1 else 0}"
            val (success, _) = RootUtils.executeRootCommand(cmd)
            if (success) return true
        }
        return openSettingsPanel(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    }

    // =========================================================================
    // 14. ENERGIA / REINICIAR / DESLIGAR (ROOT)
    // =========================================================================

    suspend fun rebootDevice(mode: String = "normal"): Pair<Boolean, String> {
        if (!RootUtils.isRootAvailable()) {
            return Pair(false, "Permissão de Root necessária para reiniciar o dispositivo.")
        }
        val cmd = when (mode.lowercase()) {
            "recovery" -> "reboot recovery"
            "bootloader", "fastboot" -> "reboot bootloader"
            "poweroff", "desligar" -> "reboot -p"
            else -> "reboot"
        }
        return RootUtils.executeRootCommand(cmd)
    }

    // =========================================================================
    // HELPERS DE INTENT
    // =========================================================================

    fun openSettingsPanel(action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir painel $action: ${e.message}")
            false
        }
    }

    private fun openWriteSettingsPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
