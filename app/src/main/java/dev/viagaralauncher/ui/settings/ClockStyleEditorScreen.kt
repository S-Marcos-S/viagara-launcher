// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.settings

import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import dev.viagaralauncher.clock.ClockFace
import dev.viagaralauncher.clock.ClockFontFile
import dev.viagaralauncher.clock.ClockFontStore
import dev.viagaralauncher.clock.ClockTimeLabel
import dev.viagaralauncher.data.ClockFaceStyle
import dev.viagaralauncher.data.ClockLayout
import dev.viagaralauncher.data.CustomClockStyle
import dev.viagaralauncher.service.HapticUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val CustomClockStyleSaver = Saver<CustomClockStyle, String>(
    save = { it.encode() },
    restore = { CustomClockStyle.decode(it) },
)

@StringRes
fun ClockLayout.labelRes(): Int = when (this) {
    ClockLayout.SingleLine -> R.string.clock_single_line
    ClockLayout.TwoLines -> R.string.clock_two_lines
    ClockLayout.Week -> R.string.clock_week
    ClockLayout.Sacramento -> R.string.clock_sacramento
    ClockLayout.Bokor -> R.string.clock_bokor
    ClockLayout.Plaster -> R.string.clock_plaster
    ClockLayout.DoublePlaster -> R.string.clock_double_plaster
    ClockLayout.Monoton -> R.string.clock_monoton
    ClockLayout.Lucky -> R.string.clock_lucky
    ClockLayout.SacramentoWeek -> R.string.clock_sacramento_week
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockStyleEditorScreen(
    initialStyle: CustomClockStyle,
    hapticsEnabled: Boolean,
    onApply: (CustomClockStyle) -> Unit,
    onBack: () -> Unit,
    topBarActions: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var draft by rememberSaveable(stateSaver = CustomClockStyleSaver) {
        mutableStateOf(initialStyle)
    }

    val face = draft.face
    fun changeFace(transform: (ClockFaceStyle) -> ClockFaceStyle) {
        draft = draft.withFace(transform(draft.face))
    }

    var fontDialogOpen by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val store = remember(context) { ClockFontStore(context) }
    val fonts by produceState<List<ClockFontFile>?>(null, store) {
        value = store.list()
    }

    LaunchedEffect(fonts) {
        val available = fonts?.map { it.id }?.toSet() ?: return@LaunchedEffect
        val current = draft
        val missing = (listOf(current.singleLine, current.twoLines) + current.presets.values)
            .mapNotNull { it.fontId }
            .filter { ClockFontStore.displayName(it) != null && it !in available && it !in setOf("serif", "sans_serif", "monospace", "cursive") }
            .toSet()
        if (missing.isNotEmpty()) draft = current.withoutFonts(missing)
    }

    val allowsFont = draft.layout.allowsCustomFont
    val showsColon = !draft.layout.stacked
    val defaults = CustomClockStyle.defaults(draft.layout)

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val imported = store.import(uri)
                    changeFace { it.copy(fontId = imported.id) }
                    Toast.makeText(context, "Fonte importada com sucesso!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Erro ao importar fonte", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (fontDialogOpen && allowsFont) {
        ClockFontPickerDialog(
            currentFontId = face.fontId,
            importedFonts = fonts ?: emptyList(),
            onSelect = { selectedId ->
                changeFace { it.copy(fontId = selectedId) }
                fontDialogOpen = false
            },
            onImportNew = {
                fontDialogOpen = false
                fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "font/*", "application/octet-stream", "*/*"))
            },
            onDismiss = { fontDialogOpen = false },
        )
    }

    val surface = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_clock_style),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    topBarActions()
                    Button(
                        modifier = Modifier.padding(end = 8.dp),
                        enabled = !saving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        onClick = {
                            saving = true
                            HapticUtil.tick(view, hapticsEnabled)
                            onApply(draft)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.clock_style_apply))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Live Preview at top
            ClockStylePreviewBox(
                style = draft,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            )

            // Scrollable controls
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                userScrollEnabled = !saving,
            ) {
                // Layout Selector Row
                item(key = "layout_selector") {
                    ClockLayoutSelectorRow(
                        currentStyle = draft,
                        enabled = !saving,
                        onSelect = { newLayout ->
                            HapticUtil.tick(view, hapticsEnabled)
                            draft = draft.copy(layout = newLayout)
                        },
                    )
                }

                // Font selector item (only for layouts supporting custom font)
                if (allowsFont) {
                    item(key = "font_selector") {
                        ClockEditorActionCard(
                            title = stringResource(R.string.clock_font),
                            detail = fontLabel(face.fontId),
                            enabled = !saving,
                            onClick = {
                                HapticUtil.tick(view, hapticsEnabled)
                                fontDialogOpen = true
                            },
                        )
                    }
                }

                // Weight slider
                item(key = "slider_weight") {
                    ClockSliderCard(
                        title = stringResource(R.string.clock_weight),
                        value = face.weight,
                        default = defaults.weight,
                        range = 100..face.maxWeight(draft.layout),
                        step = 100,
                        enabled = !saving,
                        onChange = { changeFace { f -> f.copy(weight = it) } },
                    )
                }

                // Size slider
                item(key = "slider_size") {
                    ClockSliderCard(
                        title = stringResource(R.string.clock_size),
                        value = face.size,
                        default = defaults.size,
                        range = 32..144,
                        step = 1,
                        enabled = !saving,
                        onChange = { changeFace { f -> f.copy(size = it) } },
                    )
                }

                // Spacing slider
                item(key = "slider_spacing") {
                    ClockSliderCard(
                        title = stringResource(if (draft.layout.week) R.string.clock_letter_spacing else R.string.clock_digit_spacing),
                        value = face.letterSpacing,
                        default = defaults.letterSpacing,
                        range = -16..16,
                        step = 1,
                        enabled = !saving,
                        onChange = { changeFace { f -> f.copy(letterSpacing = it) } },
                    )
                }

                // Hour-Minute Spacing slider
                item(key = "slider_hour_minute_spacing") {
                    ClockSliderCard(
                        title = stringResource(R.string.clock_hour_minute_spacing),
                        value = face.hourMinuteSpacing,
                        default = defaults.hourMinuteSpacing,
                        range = -16..64,
                        step = 1,
                        enabled = !saving,
                        onChange = { changeFace { f -> f.copy(hourMinuteSpacing = it) } },
                    )
                }

                // Font Shadow slider
                item(key = "slider_shadow") {
                    ClockSliderCard(
                        title = stringResource(R.string.clock_font_shadow),
                        value = face.fontShadow,
                        default = defaults.fontShadow,
                        range = 0..24,
                        step = 1,
                        enabled = !saving,
                        onChange = { changeFace { f -> f.copy(fontShadow = it) } },
                    )
                }

                // Separate digits toggle
                item(key = "toggle_separate_digits") {
                    ClockToggleCard(
                        title = stringResource(R.string.clock_separate_digits),
                        summary = stringResource(R.string.clock_separate_digits_summary),
                        checked = face.separateDigits,
                        enabled = !saving,
                        onCheckedChange = { checked ->
                            HapticUtil.tick(view, hapticsEnabled)
                            changeFace { it.copy(separateDigits = checked) }
                        },
                    )
                }

                // Show colon toggle (only if not stacked)
                if (showsColon) {
                    item(key = "toggle_show_colon") {
                        ClockToggleCard(
                            title = stringResource(R.string.clock_show_colon),
                            summary = stringResource(R.string.clock_show_colon_summary),
                            checked = face.showColon,
                            enabled = !saving,
                            onCheckedChange = { checked ->
                                HapticUtil.tick(view, hapticsEnabled)
                                changeFace { it.copy(showColon = checked) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockStylePreviewBox(
    style: CustomClockStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }

    val is24Hour = DateFormat.is24HourFormat(context)
    val timeFormat = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault())
    val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE, d 'de' MMMM", Locale.getDefault())

    val timeString = remember(now, is24Hour) { timeFormat.format(now) }
    val weekdayString = remember(now) { weekdayFormat.format(now).replaceFirstChar { it.uppercase() } }
    val dateString = remember(now) { dateFormat.format(now) }

    val shape = RoundedCornerShape(24.dp)
    val contentColor = MaterialTheme.colorScheme.onSurface

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (style.layout.stacked) 210.dp else 160.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                if (style.layout.week) {
                    // Week layout: Weekday in big ClockFace font
                    ClockFace(
                        time = weekdayString,
                        style = style,
                        color = contentColor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClockTimeLabel(
                            time = timeString,
                            style = style,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = contentColor,
                        )
                        Text(
                            text = " · $dateString",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    // Normal or Stacked ClockFace
                    ClockFace(
                        time = timeString,
                        style = style,
                        color = contentColor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockLayoutSelectorRow(
    currentStyle: CustomClockStyle,
    enabled: Boolean,
    onSelect: (ClockLayout) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentStyle.layout.ordinal - 1).coerceAtLeast(0),
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.clock_layout),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                items(ClockLayout.entries.toTypedArray()) { layout ->
                    ClockLayoutCard(
                        style = currentStyle.copy(layout = layout),
                        selected = layout == currentStyle.layout,
                        enabled = enabled,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockLayoutCard(
    style: CustomClockStyle,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (ClockLayout) -> Unit,
) {
    val layout = style.layout
    val label = stringResource(layout.labelRes())
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)

    val interactions = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }

    val container by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.5f),
        tween(200),
        label = "containerColor",
    )
    val outline by animateColorAsState(
        if (selected) colors.primary else colors.outline.copy(alpha = 0.2f),
        tween(200),
        label = "outlineColor",
    )
    val contentColor by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        tween(200),
        label = "contentColor",
    )

    LaunchedEffect(interactions) {
        interactions.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(0.92f, tween(100))
                is PressInteraction.Release -> scale.animateTo(1f, tween(150))
                is PressInteraction.Cancel -> scale.animateTo(1f, tween(100))
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(shape)
                .background(container)
                .border(if (selected) 2.dp else 1.dp, outline, shape)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    interactionSource = interactions,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = { onSelect(layout) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            ClockFace(
                time = if (layout.week) "Sex" else "09:30",
                style = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                fontScale = 0.28f,
                textAlign = TextAlign.Center,
                color = contentColor,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClockSliderCard(
    title: String,
    value: Int,
    default: Int,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = {
                        val computed = (range.first + ((it - range.first) / step).roundToInt() * step).coerceIn(range)
                        onChange(computed)
                    },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = if (step > 1) (range.last - range.first) / step - 1 else 0,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onChange(default) },
                    enabled = enabled && value != default,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ms_restart_alt),
                        contentDescription = stringResource(R.string.clock_reset_value),
                        modifier = Modifier.size(20.dp),
                        tint = if (value != default) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockToggleCard(
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (summary != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ClockEditorActionCard(
    title: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.Filled.FontDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ClockFontPickerDialog(
    currentFontId: String?,
    importedFonts: List<ClockFontFile>,
    onSelect: (String?) -> Unit,
    onImportNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        null to "Padrão do Sistema",
        "sans_serif" to "Sans-Serif",
        "serif" to "Serif",
        "monospace" to "Monospace",
        "cursive" to "Cursiva",
    ) + importedFonts.map { it.id to it.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clock_font)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .selectableGroup(),
            ) {
                items(options) { (id, name) ->
                    val isSelected = (currentFontId == id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(id) },
                            )
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onImportNew,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.clock_font_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun fontLabel(fontId: String?): String = when (fontId) {
    null -> "Padrão"
    "sans_serif" -> "Sans-Serif"
    "serif" -> "Serif"
    "monospace" -> "Monospace"
    "cursive" -> "Cursiva"
    else -> ClockFontStore.displayName(fontId) ?: "Personalizada"
}
