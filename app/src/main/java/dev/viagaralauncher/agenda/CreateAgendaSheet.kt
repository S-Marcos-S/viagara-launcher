// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAgendaSheet(
    activityToEdit: AgendaActivity?,
    onDismissRequest: () -> Unit,
    onSaveActivity: (AgendaActivity) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialActivity = activityToEdit ?: AgendaActivity(
        id = java.util.UUID.randomUUID().toString(),
        title = "",
        date = LocalDate.now().toString(),
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(10, 0),
        isAllDay = true,
        notificationSettings = AgendaNotificationSettings(
            isEnabled = true,
            notificationType = AgendaNotificationType.FIFTEEN_MINUTES_BEFORE
        )
    )

    var title by remember { mutableStateOf(initialActivity.title) }
    var descriptionFieldValue by remember {
        mutableStateOf(TextFieldValue(initialActivity.description ?: ""))
    }
    var selectedActivityType by remember { mutableStateOf(initialActivity.activityType) }
    var selectedPriority by remember { mutableStateOf(initialActivity.categoryColor) }
    var selectedVisibility by remember { mutableStateOf(initialActivity.visibility) }
    var selectedDate by remember {
        mutableStateOf(try { LocalDate.parse(initialActivity.date) } catch (_: Exception) { LocalDate.now() })
    }

    var hasScheduledTime by remember { mutableStateOf(!initialActivity.isAllDay && initialActivity.startTime != null) }
    var startTime by remember { mutableStateOf(initialActivity.startTime ?: LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(initialActivity.endTime ?: LocalTime.of(10, 0)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isPickingStartTime by remember { mutableStateOf(true) }

    var showDatePicker by remember { mutableStateOf(false) }
    var rollover by remember { mutableStateOf(initialActivity.rollover) }
    var showInCalendar by remember { mutableStateOf(initialActivity.showInCalendar) }

    var notificationSettings by remember { mutableStateOf(initialActivity.notificationSettings) }

    val repetitionOptions = listOf(
        "Não repetir",
        "Todos os dias",
        "Toda semana",
        "Todo mês",
        "Todo ano"
    )
    val repetitionMapping = mapOf(
        repetitionOptions[0] to "",
        repetitionOptions[1] to "DAILY",
        repetitionOptions[2] to "WEEKLY",
        repetitionOptions[3] to "MONTHLY",
        repetitionOptions[4] to "YEARLY"
    )

    var selectedRepetition by remember {
        mutableStateOf(
            when (initialActivity.recurrenceRule) {
                "DAILY" -> repetitionOptions[1]
                "WEEKLY" -> repetitionOptions[2]
                "MONTHLY" -> repetitionOptions[3]
                "YEARLY" -> repetitionOptions[4]
                else -> repetitionOptions[0]
            }
        )
    }
    var isRepetitionMenuExpanded by remember { mutableStateOf(false) }

    // Smart list handling in description (checklist & numbered list)
    val currentLine: String = remember(descriptionFieldValue) {
        val text = descriptionFieldValue.text
        val selection = descriptionFieldValue.selection
        val cursorPosition = selection.start.coerceIn(0, text.length)
        val lastNewlineIndex = text.substring(0, cursorPosition).lastIndexOf('\n')
        val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1
        val nextNewlineIndex = text.indexOf('\n', cursorPosition)
        val lineEnd = if (nextNewlineIndex == -1) text.length else nextNewlineIndex
        if (lineStart <= lineEnd) text.substring(lineStart, lineEnd) else ""
    }
    val isChecklistActive = currentLine.startsWith("[ ]") || currentLine.startsWith("[x]")
    val isNumberedListActive = """^\d+\.\s+""".toRegex().containsMatchIn(currentLine)

    fun toggleListFormat(type: String) {
        val text = descriptionFieldValue.text
        val selection = descriptionFieldValue.selection
        val cursorPosition = selection.start.coerceIn(0, text.length)
        val lastNewlineIndex = text.substring(0, cursorPosition).lastIndexOf('\n')
        val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1

        val nextNewlineIndex = text.indexOf('\n', cursorPosition)
        val lineEnd = if (nextNewlineIndex == -1) text.length else nextNewlineIndex
        val lineToFormat = text.substring(lineStart, lineEnd)

        val checklistRegex = """^\[([ x]?)]\s*(.*)$""".toRegex()
        val numberedRegex = """^(\d+)\.\s*(.*)$""".toRegex()

        val newLine: String
        val selectionOffset: Int

        if (type == "checklist") {
            val match = checklistRegex.matchEntire(lineToFormat)
            if (match != null) {
                newLine = match.groupValues[2]
                selectionOffset = -(lineToFormat.length - newLine.length)
            } else {
                val cleanLine = numberedRegex.matchEntire(lineToFormat)?.groupValues?.get(2) ?: lineToFormat
                newLine = "[ ] $cleanLine"
                selectionOffset = newLine.length - lineToFormat.length
            }
        } else {
            val match = numberedRegex.matchEntire(lineToFormat)
            if (match != null) {
                newLine = match.groupValues[2]
                selectionOffset = -(lineToFormat.length - newLine.length)
            } else {
                val cleanLine = checklistRegex.matchEntire(lineToFormat)?.groupValues?.get(2) ?: lineToFormat
                newLine = "1. $cleanLine"
                selectionOffset = newLine.length - lineToFormat.length
            }
        }

        val updatedText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val newCursorPos = (cursorPosition + selectionOffset).coerceIn(0, updatedText.length)
        descriptionFieldValue = TextFieldValue(updatedText, TextRange(newCursorPos))
    }

    val dateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "BR"))

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        val headerTitle = if (activityToEdit == null) {
                            when (selectedActivityType) {
                                AgendaActivityType.TASK -> "Nova Tarefa"
                                AgendaActivityType.EVENT -> "Novo Evento"
                                AgendaActivityType.NOTE -> "Nova Nota"
                                AgendaActivityType.BIRTHDAY -> "Novo Aniversário"
                            }
                        } else {
                            when (selectedActivityType) {
                                AgendaActivityType.TASK -> "Editar Tarefa"
                                AgendaActivityType.EVENT -> "Editar Evento"
                                AgendaActivityType.NOTE -> "Editar Nota"
                                AgendaActivityType.BIRTHDAY -> "Editar Aniversário"
                            }
                        }

                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = selectedDate.format(dateFormatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showDatePicker = true }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val updated = initialActivity.copy(
                                title = title.trim(),
                                description = descriptionFieldValue.text.trim().takeIf { it.isNotBlank() },
                                date = selectedDate.toString(),
                                categoryColor = selectedPriority,
                                activityType = selectedActivityType,
                                startTime = if (hasScheduledTime) startTime else null,
                                endTime = if (hasScheduledTime) endTime else null,
                                isAllDay = !hasScheduledTime,
                                notificationSettings = notificationSettings,
                                visibility = selectedVisibility,
                                showInCalendar = showInCalendar,
                                rollover = rollover,
                                recurrenceRule = repetitionMapping[selectedRepetition]?.takeIf { it.isNotBlank() },
                                lastModified = System.currentTimeMillis()
                            )
                            onSaveActivity(updated)
                            onDismissRequest()
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salvar")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { input ->
                        title = if (input.isNotEmpty()) {
                            input.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString() }
                        } else input
                    },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Description Field with smart lists
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = descriptionFieldValue,
                        onValueChange = { newValue ->
                            var finalValue = newValue
                            val oldText = descriptionFieldValue.text
                            val newText = newValue.text
                            val oldSelection = descriptionFieldValue.selection

                            if (newText.length == oldText.length + 1 &&
                                oldSelection.collapsed &&
                                oldSelection.start < newText.length &&
                                newText[oldSelection.start] == '\n'
                            ) {
                                val newlinePos = oldSelection.start
                                val textBeforeNewline = oldText.substring(0, newlinePos)
                                val lastNewlineIndex = textBeforeNewline.lastIndexOf('\n')
                                val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1
                                val completedLine = textBeforeNewline.substring(lineStart)

                                val checklistRegex = """^\[([ x]?)]\s*(.*)$""".toRegex()
                                val numberedRegex = """^(\d+)\.\s*(.*)$""".toRegex()

                                val checklistMatch = checklistRegex.matchEntire(completedLine)
                                val numberedMatch = numberedRegex.matchEntire(completedLine)

                                if (checklistMatch != null) {
                                    val content = checklistMatch.groupValues[2]
                                    if (content.isEmpty()) {
                                        val updatedText = oldText.substring(0, lineStart) + oldText.substring(newlinePos)
                                        finalValue = TextFieldValue(updatedText, TextRange(lineStart))
                                    } else {
                                        val prefix = "[ ] "
                                        val updatedText = newText.substring(0, newlinePos + 1) + prefix + newText.substring(newlinePos + 1)
                                        finalValue = TextFieldValue(updatedText, TextRange(newlinePos + 1 + prefix.length))
                                    }
                                } else if (numberedMatch != null) {
                                    val num = numberedMatch.groupValues[1].toInt()
                                    val content = numberedMatch.groupValues[2]
                                    if (content.isEmpty()) {
                                        val updatedText = oldText.substring(0, lineStart) + oldText.substring(newlinePos)
                                        finalValue = TextFieldValue(updatedText, TextRange(lineStart))
                                    } else {
                                        val prefix = "${num + 1}. "
                                        val updatedText = newText.substring(0, newlinePos + 1) + prefix + newText.substring(newlinePos + 1)
                                        finalValue = TextFieldValue(updatedText, TextRange(newlinePos + 1 + prefix.length))
                                    }
                                }
                            }
                            descriptionFieldValue = finalValue
                        },
                        label = { Text("Descrição (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { toggleListFormat("numbered") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatListNumbered,
                                contentDescription = "Lista numerada",
                                tint = if (isNumberedListActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { toggleListFormat("checklist") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Checklist",
                                tint = if (isChecklistActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Activity Type Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AgendaActivityType.values().forEach { type ->
                        val isSelected = selectedActivityType == type
                        TextButton(
                            onClick = { selectedActivityType = type },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = when (type) {
                                    AgendaActivityType.TASK -> "Tarefa"
                                    AgendaActivityType.EVENT -> "Evento"
                                    AgendaActivityType.NOTE -> "Nota"
                                    AgendaActivityType.BIRTHDAY -> "Aniver."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Priority Selector
                if (selectedActivityType != AgendaActivityType.NOTE && selectedActivityType != AgendaActivityType.BIRTHDAY) {
                    Column {
                        Text(
                            text = "Prioridade",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val priorities = listOf(
                                "1" to Color.White,
                                "2" to Color(0xFF2196F3),
                                "3" to Color(0xFFFFC107),
                                "4" to Color(0xFFF44336)
                            )
                            priorities.forEach { (pKey, color) ->
                                val isSelected = selectedPriority == pKey
                                Surface(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { selectedPriority = pKey },
                                    shape = CircleShape,
                                    color = color,
                                    border = if (isSelected) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }
                                ) {}
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Time Selector
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Definir horário",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = hasScheduledTime,
                            onCheckedChange = { hasScheduledTime = it }
                        )
                    }

                    if (hasScheduledTime) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    isPickingStartTime = true
                                    showTimePicker = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(String.format(Locale.getDefault(), "%02d:%02d", startTime.hour, startTime.minute))
                            }

                            Button(
                                onClick = {
                                    isPickingStartTime = false
                                    showTimePicker = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(String.format(Locale.getDefault(), "%02d:%02d", endTime.hour, endTime.minute))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Repetition Selector
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isRepetitionMenuExpanded = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Repetição: $selectedRepetition",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedRepetition != "Não repetir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = isRepetitionMenuExpanded,
                        onDismissRequest = { isRepetitionMenuExpanded = false }
                    ) {
                        repetitionOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    selectedRepetition = opt
                                    isRepetitionMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notifications
                AgendaNotificationSelectorComponent(
                    settings = notificationSettings,
                    onSettingsChanged = { notificationSettings = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Rollover (Adiar se não concluída)
                if (selectedActivityType == AgendaActivityType.TASK) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Adiar para o dia seguinte",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Se não concluída até o fim do dia, transfere para hoje",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rollover,
                            onCheckedChange = { rollover = it }
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Visibility
                var isVisibilityExpanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isVisibilityExpanded = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Visibilidade",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (selectedVisibility) {
                                AgendaVisibilityLevel.LOW -> "Baixa"
                                AgendaVisibilityLevel.MEDIUM -> "Média"
                                AgendaVisibilityLevel.HIGH -> "Alta"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = isVisibilityExpanded,
                        onDismissRequest = { isVisibilityExpanded = false }
                    ) {
                        AgendaVisibilityLevel.values().forEach { lvl ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (lvl) {
                                            AgendaVisibilityLevel.LOW -> "Baixa"
                                            AgendaVisibilityLevel.MEDIUM -> "Média"
                                            AgendaVisibilityLevel.HIGH -> "Alta"
                                        }
                                    )
                                },
                                onClick = {
                                    selectedVisibility = lvl
                                    isVisibilityExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // TimePicker Dialog
    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = if (isPickingStartTime) startTime.hour else endTime.hour,
            initialMinute = if (isPickingStartTime) startTime.minute else endTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(if (isPickingStartTime) "Horário de início" else "Horário de término") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val pickedTime = LocalTime.of(pickerState.hour, pickerState.minute)
                    if (isPickingStartTime) {
                        startTime = pickedTime
                    } else {
                        endTime = pickedTime
                    }
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DatePicker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        Dialog(
            onDismissRequest = { showDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    DatePicker(
                        state = datePickerState,
                        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val utcDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                selectedDate = utcDate
                            }
                            showDatePicker = false
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgendaNotificationSelectorComponent(
    settings: AgendaNotificationSettings,
    onSettingsChanged: (AgendaNotificationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf((settings.customMinutesBefore ?: 15).toString()) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (settings.isEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = if (settings.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Lembrete",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(
                checked = settings.isEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChanged(
                        settings.copy(
                            isEnabled = enabled,
                            notificationType = if (enabled) AgendaNotificationType.FIFTEEN_MINUTES_BEFORE else AgendaNotificationType.NONE
                        )
                    )
                }
            )
        }

        if (settings.isEnabled) {
            Spacer(modifier = Modifier.height(6.dp))
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tempo:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = settings.notificationType.getDescription(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false }
                ) {
                    AgendaNotificationType.values().forEach { notifType ->
                        DropdownMenuItem(
                            text = { Text(notifType.getDescription()) },
                            onClick = {
                                onSettingsChanged(
                                    settings.copy(
                                        notificationType = notifType,
                                        customMinutesBefore = if (notifType == AgendaNotificationType.CUSTOM) {
                                            customMinutesInput.toIntOrNull() ?: 15
                                        } else null
                                    )
                                )
                                isExpanded = false
                                if (notifType == AgendaNotificationType.CUSTOM) {
                                    showCustomDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Minutos antes") },
            text = {
                OutlinedTextField(
                    value = customMinutesInput,
                    onValueChange = { customMinutesInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = customMinutesInput.toIntOrNull() ?: 15
                    onSettingsChanged(settings.copy(customMinutesBefore = minutes))
                    showCustomDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
