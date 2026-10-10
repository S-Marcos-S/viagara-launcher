// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ai

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LocalAiManager(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var isInitializing = false
    private val actionDispatcher = AiActionDispatcher(context)
    private val appManager = AppOperationsManager(context)

    companion object {
        private const val TAG = "LocalAiManager"
        const val DEFAULT_MODEL_FILENAME = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"

        /**
         * Retorna os caminhos prováveis onde o usuário pode ter colocado o modelo no aparelho.
         */
        fun findModelFile(context: Context): File? {
            val candidatePaths = listOf(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DEFAULT_MODEL_FILENAME),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DEFAULT_MODEL_FILENAME),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "VictoriaLauncher/models/$DEFAULT_MODEL_FILENAME"),
                File(context.getExternalFilesDir(null), DEFAULT_MODEL_FILENAME),
                File(context.filesDir, DEFAULT_MODEL_FILENAME)
            )
            return candidatePaths.firstOrNull { it.exists() && it.length() > 0 }
        }
    }

    /**
     * Inicializa a engine do modelo se o arquivo .task estiver presente.
     */
    suspend fun initializeEngine(customModelPath: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (llmInference != null) return@withContext true
        if (isInitializing) return@withContext false

        isInitializing = true
        try {
            val modelFile = if (!customModelPath.isNullOrBlank()) {
                File(customModelPath)
            } else {
                findModelFile(context)
            }

            if (modelFile == null || !modelFile.exists()) {
                Log.w(TAG, "Arquivo do modelo não encontrado.")
                isInitializing = false
                return@withContext false
            }

            Log.d(TAG, "Carregando modelo do MediaPipe: ${modelFile.absolutePath} (${modelFile.length() / (1024 * 1024)} MB)")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "Engine do MediaPipe LlmInference inicializada com sucesso!")
            isInitializing = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar MediaPipe LlmInference: ${e.message}", e)
            isInitializing = false
            false
        }
    }

    fun isModelReady(): Boolean = llmInference != null

    /**
     * Processa um comando em linguagem natural e executa a ação correspondente.
     */
    suspend fun processCommand(userInput: String): AiActionResult = withContext(Dispatchers.IO) {
        if (userInput.isBlank()) return@withContext AiActionResult.Error("Comando vazio.")

        if (llmInference == null) {
            val initialized = initializeEngine()
            if (!initialized) {
                return@withContext AiActionResult.Error("Modelo local não encontrado ou não carregado. Coloque o arquivo '$DEFAULT_MODEL_FILENAME' na pasta Downloads ou Documentos.")
            }
        }

        try {
            val systemPrompt = buildSystemPrompt()
            val fullPrompt = "$systemPrompt\n\nUsuário: $userInput\nAssistente:"

            Log.d(TAG, "Enviando prompt para inferência...")
            val rawOutput = llmInference?.generateResponse(fullPrompt) ?: ""
            Log.d(TAG, "Resposta bruta do modelo: $rawOutput")

            val jsonString = extractJsonFromResponse(rawOutput)
            if (jsonString != null) {
                actionDispatcher.dispatchAction(jsonString)
            } else {
                AiActionResult.Answer(rawOutput.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante a execução do comando pela IA: ${e.message}", e)
            AiActionResult.Error("Erro ao processar: ${e.message}")
        }
    }

    /**
     * Monta o prompt de sistema com contexto de tempo e catálogo de ações.
     */
    private fun buildSystemPrompt(): String {
        val today = LocalDate.now().toString()
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        return """
Você é o assistente de controle da Victoria Launcher no Android.
Contexto Atual: Data: $today, Horário: $time.

Sua tarefa é converter a ordem do usuário em um único objeto JSON estrito com a ação correspondente.
Responda APENAS com o JSON. Não adicione texto introdutório nem explicações fora do JSON.

AÇÕES DISPONÍVEIS:
1. Abrir app: {"action": "open_app", "app_name": "nome ou pacote"}
2. Desinstalar app: {"action": "uninstall_app", "app_name": "nome ou pacote"}
3. Informações do app: {"action": "app_info", "app_name": "nome"}
4. Forçar parada de app: {"action": "force_stop_app", "app_name": "nome"}
5. Lanterna: {"action": "toggle_flashlight", "state": true ou false}
6. Volume: {"action": "set_volume", "type": "media|ring|alarm|notification", "percent": 0-100}
7. Modo de som: {"action": "set_ringer_mode", "mode": "normal|vibrate|silent"}
8. Não Perturbe: {"action": "toggle_dnd", "enable": true ou false}
9. Brilho: {"action": "set_brightness", "percent": 0-100, "auto": true ou false}
10. Rotação automática: {"action": "toggle_auto_rotate", "enable": true ou false}
11. Wi-Fi: {"action": "toggle_wifi", "enable": true ou false}
12. Bluetooth: {"action": "toggle_bluetooth", "enable": true ou false}
13. Dados Móveis: {"action": "toggle_mobile_data", "enable": true ou false}
14. Modo Avião: {"action": "toggle_airplane_mode", "enable": true ou false}
15. Localização/GPS: {"action": "toggle_location", "enable": true ou false}
16. Roteador Wi-Fi (Hotspot): {"action": "toggle_hotspot", "enable": true ou false}
17. NFC: {"action": "toggle_nfc", "enable": true ou false}
18. Economia de bateria: {"action": "toggle_battery_saver", "enable": true ou false}
19. Reiniciar celular: {"action": "reboot_device", "mode": "normal|recovery|poweroff"}
20. Criar lembrete/tarefa: {"action": "create_task", "title": "titulo", "date": "YYYY-MM-DD", "time": "HH:mm", "alarm": true}
21. Definir alarme/despertador: {"action": "set_alarm", "hour": 0-23, "minute": 0-59, "message": "nome"}
22. Dúvidas gerais ou conversa: {"action": "chat", "text": "resposta amigável"}
""".trimIndent()
    }

    /**
     * Extrai o primeiro bloco JSON válido da resposta do modelo.
     */
    private fun extractJsonFromResponse(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val start = raw.indexOf("{")
        val end = raw.lastIndexOf("}")
        return if (start != -1 && end != -1 && end > start) {
            raw.substring(start, end + 1)
        } else {
            null
        }
    }
}
