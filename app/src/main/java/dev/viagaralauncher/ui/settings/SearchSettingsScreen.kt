// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.viagaralauncher.R
import dev.viagaralauncher.ui.search.SearchConfig
import dev.viagaralauncher.ui.search.SearchEngine

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchSettingsScreen(
    config: SearchConfig,
    onSetButtonEnabled: (Boolean) -> Unit,
    onSetIncludeApps: (Boolean) -> Unit,
    onSetIncludeContacts: (Boolean) -> Unit,
    onSetIncludeSettings: (Boolean) -> Unit,
    onSetIncludeWeb: (Boolean) -> Unit,
    onSetIncludePlayStore: (Boolean) -> Unit,
    onSetSearchEngine: (SearchEngine) -> Unit,
    onSetAutoKeyboard: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val context = LocalContext.current

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(padding)
                .background(surface)
                .fillMaxSize(),
        ) {
            // General / Behavior Section
            item {
                Section(stringResource(R.string.settings_section_behavior)) {
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_search_button_enable),
                        detail = stringResource(R.string.settings_search_button_enable_detail),
                        checked = config.buttonEnabled,
                        onCheckedChange = onSetButtonEnabled,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_search_auto_keyboard),
                        detail = stringResource(R.string.settings_search_auto_keyboard_detail),
                        checked = config.autoKeyboard,
                        onCheckedChange = onSetAutoKeyboard,
                    )
                }
            }

            // Search Sources Section
            item {
                Section(stringResource(R.string.settings_search_sources_title)) {
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_search_source_apps),
                        detail = stringResource(R.string.settings_search_source_apps_detail),
                        checked = config.includeApps,
                        onCheckedChange = onSetIncludeApps,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_search_source_contacts),
                        detail = stringResource(
                            if (hasContactsPermission) R.string.settings_search_source_contacts_detail
                            else R.string.settings_search_source_contacts_perm_needed
                        ),
                        checked = config.includeContacts,
                        onCheckedChange = { enable ->
                            onSetIncludeContacts(enable)
                            if (enable && !hasContactsPermission) {
                                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                    )
                    if (config.includeContacts && !hasContactsPermission) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            FilledChip(
                                label = stringResource(R.string.settings_enable),
                                selected = false,
                                onClick = { requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            )
                        }
                    }
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_search_source_settings),
                        detail = stringResource(R.string.settings_search_source_settings_detail),
                        checked = config.includeSettings,
                        onCheckedChange = onSetIncludeSettings,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_search_source_web),
                        detail = stringResource(R.string.settings_search_source_web_detail),
                        checked = config.includeWeb,
                        onCheckedChange = onSetIncludeWeb,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_search_source_play_store),
                        detail = stringResource(R.string.settings_search_source_play_store_detail),
                        checked = config.includePlayStore,
                        onCheckedChange = onSetIncludePlayStore,
                    )
                }
            }

            // Web Search Engine Section
            if (config.includeWeb) {
                item {
                    Section(stringResource(R.string.settings_search_engine_title)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_search_engine_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SearchEngine.entries.forEach { engine ->
                                    FilledChip(
                                        label = engine.displayName,
                                        selected = config.searchEngine == engine,
                                        onClick = { onSetSearchEngine(engine) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SwitchRowWithDetail(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun FilledChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
