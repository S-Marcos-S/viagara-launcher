// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.viagaralauncher.ai.AiActionResult
import dev.viagaralauncher.ai.LocalAiManager
import kotlinx.coroutines.launch

@Composable
fun AiAssistantBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiManager = remember { LocalAiManager(context) }

    var textInput by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var actionResult by remember { mutableStateOf<AiActionResult?>(null) }
    var modelReady by remember { mutableStateOf(false) }

    // Verificar se o arquivo do modelo está disponível
    LaunchedEffect(isVisible) {
        val file = LocalAiManager.findModelFile(context)
        modelReady = file != null
    }

    // Inicialização do SpeechRecognizer
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening(context, speechRecognizer, onText = { textInput = it }, onListeningState = { isListening = it })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun submitCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isBlank() || isProcessing) return

        isProcessing = true
        actionResult = null
        scope.launch {
            val result = aiManager.processCommand(trimmed)
            actionResult = result
            isProcessing = false
        }
    }

    BackHandler(enabled = isVisible, onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercepta cliques para não fechar
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF1E1F22))
                    .padding(20.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra de arraste
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                Spacer(Modifier.height(14.dp))

                // Cabeçalho
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF8AB4F8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Assistente Local",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    if (!modelReady) {
                        Text(
                            text = "Modelo não detectado",
                            fontSize = 12.sp,
                            color = Color(0xFFF28B82)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Caixa de resultado / Confirmação
                actionResult?.let { res ->
                    when (res) {
                        is AiActionResult.Success -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E3A2F))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF81C995))
                                Spacer(Modifier.width(8.dp))
                                Text(res.message, color = Color.White, fontSize = 14.sp)
                            }
                        }
                        is AiActionResult.Error -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF3C2020))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFF28B82))
                                Spacer(Modifier.width(8.dp))
                                Text(res.message, color = Color.White, fontSize = 14.sp)
                            }
                        }
                        is AiActionResult.Answer -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2B2D31))
                                    .padding(12.dp)
                            ) {
                                Text(res.text, color = Color.White, fontSize = 14.sp)
                            }
                        }
                        is AiActionResult.ConfirmationRequired -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF382E1E))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFDD663))
                                    Spacer(Modifier.width(8.dp))
                                    Text(res.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(res.description, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch { actionResult = res.onCancel() }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Cancelar", color = Color.White)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            isProcessing = true
                                            scope.launch {
                                                actionResult = res.onConfirm()
                                                isProcessing = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Confirmar Root")
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Campo de entrada de texto e botões
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (isListening) "Ouvindo sua voz..." else "Digite um comando ou toque no mic...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF8AB4F8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF141517),
                            unfocusedContainerColor = Color(0xFF141517)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            submitCommand(textInput)
                            textInput = ""
                        })
                    )

                    Spacer(Modifier.width(8.dp))

                    // Botão Microfone
                    IconButton(
                        onClick = {
                            if (isListening) {
                                speechRecognizer?.stopListening()
                                isListening = false
                            } else {
                                val hasAudioPerm = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasAudioPerm) {
                                    startListening(
                                        context,
                                        speechRecognizer,
                                        onText = {
                                            textInput = it
                                            submitCommand(it)
                                        },
                                        onListeningState = { isListening = it }
                                    )
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFFEA4335) else Color(0xFF2B2D31))
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicNone,
                            contentDescription = "Voz",
                            tint = Color.White
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // Botão Enviar
                    IconButton(
                        onClick = {
                            submitCommand(textInput)
                            textInput = ""
                        },
                        enabled = textInput.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (textInput.isNotBlank()) Color(0xFF8AB4F8) else Color(0xFF2B2D31))
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if (textInput.isNotBlank()) Color.Black else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun startListening(
    context: Context,
    recognizer: SpeechRecognizer?,
    onText: (String) -> Unit,
    onListeningState: (Boolean) -> Unit
) {
    if (recognizer == null) return

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale seu comando...")
    }

    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onListeningState(true) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { onListeningState(false) }
        override fun onError(error: Int) { onListeningState(false) }
        override fun onResults(results: Bundle?) {
            onListeningState(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                onText(matches[0])
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    recognizer.startListening(intent)
}
