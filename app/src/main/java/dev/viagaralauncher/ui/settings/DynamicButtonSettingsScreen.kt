// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.data.EdgeSide
import dev.viagaralauncher.ui.common.AppIcon
import kotlinx.coroutines.launch

private enum class GestureTargetSlot {
    CLICK,
    SWIPE_UP,
    SWIPE_DOWN,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicButtonSettingsScreen(
    allApps: List<AppInfo>,
    appsByKey: Map<String, AppInfo>,
    enabled: Boolean,
    clickAppKey: String?,
    swipeUpAppKey: String?,
    swipeDownAppKey: String?,
    hapticsEnabled: Boolean,
    edgeSide: EdgeSide,
    onSetEnabled: (Boolean) -> Unit,
    onSetClickApp: (String?) -> Unit,
    onSetSwipeUpApp: (String?) -> Unit,
    onSetSwipeDownApp: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    var pickingForSlot by remember { mutableStateOf<GestureTargetSlot?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val clickApp = clickAppKey?.let { appsByKey[it] }
    val swipeUpApp = swipeUpAppKey?.let { appsByKey[it] }
    val swipeDownApp = swipeDownAppKey?.let { appsByKey[it] }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_dynamic_button_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Seção: Ativar/Desativar
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetEnabled(!enabled) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_dynamic_button_enable),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_dynamic_button_enable_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = onSetEnabled,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            // Seção: Configuração dos 3 gestos
            if (enabled) {
                item {
                    Text(
                        text = stringResource(R.string.settings_section_behavior).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            // 1. Toque (Tap)
                            GestureConfigRow(
                                title = stringResource(R.string.settings_dynamic_button_tap_action),
                                icon = Icons.Filled.TouchApp,
                                app = clickApp,
                                onClick = {
                                    searchQuery = ""
                                    pickingForSlot = GestureTargetSlot.CLICK
                                },
                                onClear = { onSetClickApp(null) },
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )

                            // 2. Puxar para Cima (Swipe Up)
                            GestureConfigRow(
                                title = stringResource(R.string.settings_dynamic_button_swipe_up_action),
                                icon = Icons.Filled.KeyboardArrowUp,
                                app = swipeUpApp,
                                onClick = {
                                    searchQuery = ""
                                    pickingForSlot = GestureTargetSlot.SWIPE_UP
                                },
                                onClear = { onSetSwipeUpApp(null) },
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )

                            // 3. Puxar para Baixo (Swipe Down)
                            GestureConfigRow(
                                title = stringResource(R.string.settings_dynamic_button_swipe_down_action),
                                icon = Icons.Filled.KeyboardArrowDown,
                                app = swipeDownApp,
                                onClick = {
                                    searchQuery = ""
                                    pickingForSlot = GestureTargetSlot.SWIPE_DOWN
                                },
                                onClear = { onSetSwipeDownApp(null) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal BottomSheet para escolher o aplicativo para o gesto selecionado
    pickingForSlot?.let { targetSlot ->
        val slotTitle = when (targetSlot) {
            GestureTargetSlot.CLICK -> stringResource(R.string.settings_dynamic_button_tap_action)
            GestureTargetSlot.SWIPE_UP -> stringResource(R.string.settings_dynamic_button_swipe_up_action)
            GestureTargetSlot.SWIPE_DOWN -> stringResource(R.string.settings_dynamic_button_swipe_down_action)
        }

        ModalBottomSheet(
            onDismissRequest = { pickingForSlot = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_dynamic_button_pick_title, slotTitle),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))

                // Campo de busca rápida de apps
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.settings_dynamic_button_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                    singleLine = true,
                )

                Spacer(Modifier.height(12.dp))

                val filteredApps = remember(allApps, searchQuery) {
                    if (searchQuery.isBlank()) {
                        allApps
                    } else {
                        allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.height(380.dp),
                ) {
                    // Opção para Limpar / Nenhum
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    when (targetSlot) {
                                        GestureTargetSlot.CLICK -> onSetClickApp(null)
                                        GestureTargetSlot.SWIPE_UP -> onSetSwipeUpApp(null)
                                        GestureTargetSlot.SWIPE_DOWN -> onSetSwipeDownApp(null)
                                    }
                                    scope.launch {
                                        sheetState.hide()
                                        pickingForSlot = null
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.settings_dynamic_button_clear),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }

                    items(filteredApps, key = { it.key }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    when (targetSlot) {
                                        GestureTargetSlot.CLICK -> onSetClickApp(app.key)
                                        GestureTargetSlot.SWIPE_UP -> onSetSwipeUpApp(app.key)
                                        GestureTargetSlot.SWIPE_DOWN -> onSetSwipeDownApp(app.key)
                                    }
                                    scope.launch {
                                        sheetState.hide()
                                        pickingForSlot = null
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app = app, sizeDp = 40)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureConfigRow(
    title: String,
    icon: ImageVector,
    app: AppInfo?,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            if (app != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app = app, sizeDp = 18)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_dynamic_button_select_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }

        if (app != null) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        )
    }
}
