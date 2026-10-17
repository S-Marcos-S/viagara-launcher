// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AgendaBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    topPaddingDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { AgendaRepository(context) }
    val recurrenceService = remember { AgendaRecurrenceService() }
    val scope = rememberCoroutineScope()

    val rawActivities by repository.activities.collectAsState(initial = emptyList())
    val completedActivities by repository.completedActivities.collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf<AgendaActivityType?>(null) }
    var editingActivity by remember { mutableStateOf<AgendaActivity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    BackHandler(enabled = isVisible, onBack = onDismiss)

    LaunchedEffect(isVisible) {
        if (isVisible) {
            dragOffsetY = 0f
            scope.launch {
                AgendaSyncService(context).syncWithLocalFile()
            }
        }
    }

    val hideRecurring by repository.hideRecurring.collectAsState(initial = false)

    val today = remember { LocalDate.now() }
    val futureLimit = remember { today.plusDays(60) }

    val allOccurrences = remember(rawActivities, today, hideRecurring) {
        val list = mutableListOf<AgendaActivity>()
        rawActivities.forEach { baseAct ->
            val isRecurring = recurrenceService.isRecurring(baseAct)
            if (isRecurring) {
                if (!hideRecurring) {
                    val instances = recurrenceService.generateRecurringInstances(baseAct, today, futureLimit)
                    list.addAll(instances)
                }
            } else {
                try {
                    val actDate = LocalDate.parse(baseAct.date)
                    if (!actDate.isBefore(today)) {
                        list.add(baseAct)
                    }
                } catch (_: Exception) {
                    list.add(baseAct)
                }
            }
        }
        list
    }

    val filteredList = remember(allOccurrences, selectedFilter) {
        if (selectedFilter == null) {
            allOccurrences
        } else {
            allOccurrences.filter { it.activityType == selectedFilter }
        }
    }

    // Group by date, starting from today
    val groupedByDate = remember(filteredList, today) {
        filteredList
            .groupBy {
                try { LocalDate.parse(it.date) } catch (_: Exception) { today }
            }
            .filterKeys { !it.isBefore(today) }
            .toSortedMap(compareBy { it })
            .mapValues { (_, items) ->
                items.sortedWith(
                    compareBy<AgendaActivity> { it.startTime ?: LocalTime.MIN }
                        .thenBy { it.title }
                )
            }
    }

    val colorScheme = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 160)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = maxOf(topPaddingDp, 8.dp))
                    .padding(horizontal = 10.dp)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, dragOffsetY.coerceAtLeast(-300f).roundToInt()) }
                        .draggable(
                            state = rememberDraggableState { delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtMost(0f)
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (velocity < -400f || dragOffsetY < -120f) {
                                    onDismiss()
                                }
                                dragOffsetY = 0f
                            }
                        )
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                        .background(colorScheme.surfaceContainer)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    // Let children handle their own clicks, but ensure gestures
                                    // inside this sheet are never dispatched to underlying layers
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp, bottom = 6.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )

                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Sua agenda",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR")))
                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Minimalist Toggle for Recurring Activities
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (hideRecurring) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (hideRecurring) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch {
                                                repository.setHideRecurring(!hideRecurring)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Repeat,
                                            contentDescription = if (hideRecurring) "Exibindo apenas pontuais" else "Ocultar recorrentes",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (hideRecurring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = if (hideRecurring) "Pontuais" else "Recorrentes",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (hideRecurring) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }

                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Filter Chips Row
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    label = "Todos",
                                    selected = selectedFilter == null,
                                    onClick = { selectedFilter = null }
                                )
                            }
                            item {
                                FilterChip(
                                    label = "Tarefas",
                                    selected = selectedFilter == AgendaActivityType.TASK,
                                    onClick = { selectedFilter = AgendaActivityType.TASK }
                                )
                            }
                            item {
                                FilterChip(
                                    label = "Eventos",
                                    selected = selectedFilter == AgendaActivityType.EVENT,
                                    onClick = { selectedFilter = AgendaActivityType.EVENT }
                                )
                            }
                            item {
                                FilterChip(
                                    label = "Notas",
                                    selected = selectedFilter == AgendaActivityType.NOTE,
                                    onClick = { selectedFilter = AgendaActivityType.NOTE }
                                )
                            }
                            item {
                                FilterChip(
                                    label = "Aniversários",
                                    selected = selectedFilter == AgendaActivityType.BIRTHDAY,
                                    onClick = { selectedFilter = AgendaActivityType.BIRTHDAY }
                                )
                            }
                        }

                        // Activities Content
                        if (groupedByDate.isEmpty()) {
                            // Empty State
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Nenhum compromisso agendado",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Toque no botão + abaixo para adicionar uma nova tarefa ou evento.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item(key = "new_event_row") {
                                    NewAgendaEventRow(onClick = { isCreatingNew = true })
                                }

                                groupedByDate.forEach { (date, itemsForDate) ->
                                    item(key = "header_${date}") {
                                        AgendaDateHeader(date = date, today = today)
                                    }

                                    items(itemsForDate, key = { it.id }) { activity ->
                                        AgendaItemRow(
                                            activity = activity,
                                            onToggleComplete = {
                                                scope.launch {
                                                    if (activity.isCompleted) {
                                                        repository.markAsIncomplete(activity)
                                                    } else {
                                                        repository.markAsCompleted(activity.id)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                editingActivity = activity
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    repository.deleteActivity(activity.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating Action Button to Add
                    FloatingActionButton(
                        onClick = { isCreatingNew = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 24.dp, bottom = 24.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Criar agendamento")
                    }
                }
            }
        }
    }
    }

    // Sheet for Creating or Editing
    if (isCreatingNew || editingActivity != null) {
        CreateAgendaSheet(
            activityToEdit = editingActivity,
            onDismissRequest = {
                isCreatingNew = false
                editingActivity = null
            },
            onSaveActivity = { saved ->
                scope.launch {
                    repository.saveActivity(saved)
                }
                isCreatingNew = false
                editingActivity = null
            }
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AgendaDateHeader(date: LocalDate, today: LocalDate) {
    val difference = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
    val relativeDay = when (difference) {
        0 -> "Hoje"
        1 -> "Amanhã"
        -1 -> "Ontem"
        else -> if (difference > 0) "em $difference dias" else "há ${-difference} dias"
    }

    val locale = Locale("pt", "BR")
    val formattedDate = try {
        date.format(DateTimeFormatter.ofPattern("EEE, d 'de' MMM", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    } catch (_: Exception) {
        date.toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formattedDate,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = relativeDay,
            fontSize = 13.sp,
            color = if (difference == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun NewAgendaEventRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Novo agendamento",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun AgendaItemRow(
    activity: AgendaActivity,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val indicatorColor = when (activity.categoryColor) {
        "2" -> Color(0xFF2196F3)
        "3" -> Color(0xFFFFC107)
        "4" -> Color(0xFFF44336)
        else -> when (activity.activityType) {
            AgendaActivityType.BIRTHDAY -> Color(0xFFFF9800)
            AgendaActivityType.NOTE -> Color(0xFF9C27B0)
            AgendaActivityType.EVENT -> MaterialTheme.colorScheme.primary
            AgendaActivityType.TASK -> MaterialTheme.colorScheme.primary
        }
    }

    val isAllDayOrNote = activity.isAllDay || activity.startTime == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isAllDayOrNote) 48.dp else 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical color indicator pill (GraceLauncher: width 5dp, rounded 4dp, centered in 24dp box)
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(if (isAllDayOrNote) 22.dp else 38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(indicatorColor)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // If it's a task, interactive checkbox
        if (activity.activityType == AgendaActivityType.TASK) {
            Checkbox(
                checked = activity.isCompleted,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(
                    checkedColor = indicatorColor,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = activity.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (activity.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (activity.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val timeText = when {
                activity.isAllDay -> "Dia todo"
                activity.startTime != null && activity.endTime != null ->
                    "${activity.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))}–${activity.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                activity.startTime != null ->
                    activity.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                else -> null
            }

            val subtitleParts = buildList {
                if (timeText != null) add(timeText)
                if (activity.recurrenceRule != null) add("Recorrente")
            }

            if (subtitleParts.isNotEmpty()) {
                Text(
                    text = subtitleParts.joinToString(" · "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            activity.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Delete action button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Excluir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
