// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateEnterExit
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
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
        }
    }

    // Expand recurring instances for next 60 days
    val today = remember { LocalDate.now() }
    val futureLimit = remember { today.plusDays(60) }

    val allOccurrences = remember(rawActivities, today) {
        val list = mutableListOf<AgendaActivity>()
        rawActivities.forEach { baseAct ->
            if (recurrenceService.isRecurring(baseAct)) {
                val instances = recurrenceService.generateRecurringInstances(baseAct, today, futureLimit)
                list.addAll(instances)
            } else {
                try {
                    val actDate = LocalDate.parse(baseAct.date)
                    if (!actDate.isBefore(today.minusDays(1))) {
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

    // Group by date
    val groupedByDate = remember(filteredList) {
        filteredList
            .groupBy {
                try { LocalDate.parse(it.date) } catch (_: Exception) { today }
            }
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
        enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.50f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = maxOf(topPaddingDp, 8.dp))
                    .padding(horizontal = 10.dp)
                    .fillMaxHeight()
                    .animateEnterExit(
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
                        ) + fadeOut(animationSpec = tween(durationMillis = 160))
                    )
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
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(lerp(colorScheme.surfaceContainerLow, colorScheme.primary, 0.08f).copy(alpha = 0.92f))
                    .border(
                        BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.28f)),
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                    )
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
                                    text = "Agendamentos",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR")))
                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                groupedByDate.forEach { (date, itemsForDate) ->
                                    item(key = date.toString()) {
                                        AgendaDateHeader(date = date, today = today)
                                    }

                                    items(itemsForDate, key = { it.id }) { activity ->
                                        AgendaItemCard(
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
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun AgendaDateHeader(date: LocalDate, today: LocalDate) {
    val title = when {
        date == today -> "Hoje"
        date == today.plusDays(1) -> "Amanhã"
        date == today.minusDays(1) -> "Ontem"
        else -> {
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val formatted = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "BR")))
            "$dayOfWeek, $formatted"
        }
    }

    val subtitle = if (date == today || date == today.plusDays(1)) {
        date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "BR")))
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($subtitle)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun AgendaItemCard(
    activity: AgendaActivity,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (activity.categoryColor) {
        "2" -> Color(0xFF2196F3)
        "3" -> Color(0xFFFFC107)
        "4" -> Color(0xFFF44336)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority Indicator Bar
            if (priorityColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(priorityColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Interactive Checkbox or Type Icon
            if (activity.activityType == AgendaActivityType.TASK) {
                Checkbox(
                    checked = activity.isCompleted,
                    onCheckedChange = { onToggleComplete() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                val icon = when (activity.activityType) {
                    AgendaActivityType.EVENT -> Icons.Default.Event
                    AgendaActivityType.NOTE -> Icons.Default.Note
                    AgendaActivityType.BIRTHDAY -> Icons.Default.Cake
                    else -> Icons.Default.Assignment
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (activity.activityType == AgendaActivityType.BIRTHDAY) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (activity.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (activity.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                activity.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Time or Type details
                val timeText = when {
                    activity.isAllDay -> "Dia todo"
                    activity.startTime != null && activity.endTime != null ->
                        "${activity.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${activity.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                    activity.startTime != null ->
                        activity.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    else -> null
                }

                if (timeText != null || activity.recurrenceRule != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (timeText != null) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        }
                        if (activity.recurrenceRule != null) {
                            Text(
                                text = "• Recorrente",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
