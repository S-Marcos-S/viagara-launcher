// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import dev.viagaralauncher.service.HapticUtil
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import dev.viagaralauncher.battery.RootBatteryStatsCollector
import dev.viagaralauncher.update.RootInstaller
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.data.AppFont
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.data.ClockStyle
import dev.viagaralauncher.data.EdgeSide
import dev.viagaralauncher.data.IconPackRepository
import dev.viagaralauncher.data.TextColorMode
import dev.viagaralauncher.data.ThemedIconStyle
import dev.viagaralauncher.ui.common.AppIcon
import dev.viagaralauncher.ui.theme.toFontFamily
import dev.viagaralauncher.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    hiddenCount: Int,
    iconPacks: List<IconPackRepository.IconPackInfo>,
    iconPackPackage: String?,
    previewApp: AppInfo?,
    iconSizeDp: Int,
    labelSizeSp: Int,
    itemSpacingDp: Int,
    font: AppFont,
    hideStatusBar: Boolean,
    dimWallpaperAlpha: Float,
    hapticsEnabled: Boolean,
    dimHomeAlpha: Float,
    showFavoriteLabels: Boolean,
    textColorMode: TextColorMode,
    doubleTapToLock: Boolean,
    edgeSide: EdgeSide,
    alwaysShowAz: Boolean,
    showAlphabet: Boolean,
    alignRight: Boolean,
    nowPlayingEnabled: Boolean,
    nowPlayingListenerEnabled: Boolean,
    onSetIconPack: (String?) -> Unit,
    onSetIconSize: (Int) -> Unit,
    onSetLabelSize: (Int) -> Unit,
    onSetItemSpacing: (Int) -> Unit,
    onSetFont: (AppFont) -> Unit,
    onSetHideStatusBar: (Boolean) -> Unit,
    onSetDimWallpaper: (Float) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetDimHome: (Float) -> Unit,
    onSetShowFavoriteLabels: (Boolean) -> Unit,
    onSetTextColorMode: (TextColorMode) -> Unit,
    onSetDoubleTapToLock: (Boolean) -> Unit,
    onSetEdgeSide: (EdgeSide) -> Unit,
    onSetAlwaysShowAz: (Boolean) -> Unit,
    onSetShowAlphabet: (Boolean) -> Unit,
    onSetAlignRight: (Boolean) -> Unit,
    onSetNowPlayingEnabled: (Boolean) -> Unit,
    audioVisualizerEnabled: Boolean = false,
    onSetAudioVisualizerEnabled: (Boolean) -> Unit = {},
    showAppNotifications: Boolean,
    onSetShowAppNotifications: (Boolean) -> Unit,
    folderWindowPopup: Boolean,
    onSetFolderWindowPopup: (Boolean) -> Unit,
    shadeGestureReady: Boolean,
    clockStyle: ClockStyle = ClockStyle.CLASSIC,
    onOpenClockStyle: () -> Unit = {},
    themedIcons: Boolean = false,
    themedIconStyle: ThemedIconStyle = ThemedIconStyle.MATERIAL_YOU,
    onSetThemedIcons: (Boolean) -> Unit = {},
    onSetThemedIconStyle: (ThemedIconStyle) -> Unit = {},
    activeThemeId: String = "system_dynamic",
    onSetActiveThemeId: (String) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDynamicButtonSettings: () -> Unit,
    onOpenSearchSettings: () -> Unit = {},
    isDefaultLauncher: Boolean = false,
    onOpenDefaultLauncherSettings: () -> Unit = {},
    onOpenBackupSettings: () -> Unit = {},
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    var isRootAvailable by remember {
        mutableStateOf(RootBatteryStatsCollector.isRootCached == true || RootInstaller.isRootAvailable())
    }
    LaunchedEffect(Unit) {
        if (!isRootAvailable) {
            isRootAvailable = RootBatteryStatsCollector.isRootAvailable()
        }
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .fillMaxWidth(),
        ) {
            item {
                Section(stringResource(R.string.settings_section_appearance)) {
                    ThemeColorPaletteRow(
                        activeThemeId = activeThemeId,
                        hapticsEnabled = hapticsEnabled,
                        onSelectTheme = onSetActiveThemeId,
                    )
                    RowDivider()
                    // Live preview of exactly how a home row will render.
                    RowPreview(previewApp, iconSizeDp, labelSizeSp, font)
                    RowDivider()
                    IconPackRow(iconPacks, iconPackPackage, onSetIconPack)
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_themed_icons),
                        detail = stringResource(R.string.settings_themed_icons_detail),
                        checked = themedIcons,
                        onCheckedChange = onSetThemedIcons,
                    )
                    if (themedIcons) {
                        RowDivider()
                        ThemedIconStyleRow(themedIconStyle, onSetThemedIconStyle)
                    }
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_icon_size),
                        value = iconSizeDp.toFloat(),
                        range = 32f..96f,
                        valueLabel = "${iconSizeDp}dp",
                        onValueChange = { onSetIconSize(it.toInt()) },
                    )
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_text_size),
                        value = labelSizeSp.toFloat(),
                        range = 10f..28f,
                        valueLabel = "${labelSizeSp}sp",
                        onValueChange = { onSetLabelSize(it.toInt()) },
                    )
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_favorite_spacing),
                        value = itemSpacingDp.toFloat(),
                        range = 0f..40f,
                        valueLabel = "${itemSpacingDp}dp",
                        onValueChange = { onSetItemSpacing(it.toInt()) },
                    )
                    RowDivider()
                    FontRow(font, onSetFont)
                    RowDivider()
                    TextColorRow(textColorMode, onSetTextColorMode)
                    RowDivider()
                    NavigationRow(
                        label = stringResource(R.string.settings_clock_style),
                        detail = stringResource(clockStyle.labelRes()),
                        onClick = onOpenClockStyle,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_right_handed),
                        detail = stringResource(R.string.settings_right_handed_detail),
                        checked = alignRight,
                        onCheckedChange = onSetAlignRight,
                    )
                    RowDivider()
                    SwitchRow(stringResource(R.string.settings_show_names), showFavoriteLabels, onSetShowFavoriteLabels)
                    RowDivider()
                    SwitchRow(stringResource(R.string.settings_hide_status_bar), hideStatusBar, onSetHideStatusBar)
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_dim_home),
                        value = dimHomeAlpha,
                        range = 0f..0.85f,
                        valueLabel = "${(dimHomeAlpha * 100).toInt()}%",
                        onValueChange = onSetDimHome,
                        step = 0.05f,
                    )
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_dim_applist),
                        value = dimWallpaperAlpha,
                        range = 0f..0.85f,
                        valueLabel = "${(dimWallpaperAlpha * 100).toInt()}%",
                        onValueChange = onSetDimWallpaper,
                        step = 0.05f,
                    )
                }
            }

            item {
                Section(stringResource(R.string.settings_section_behavior)) {
                    NavigationRow(
                        label = stringResource(R.string.settings_default_launcher),
                        detail = stringResource(
                            if (isDefaultLauncher) R.string.settings_default_launcher_is_default
                            else R.string.settings_default_launcher_set
                        ),
                        onClick = onOpenDefaultLauncherSettings,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_haptics),
                        detail = stringResource(R.string.settings_haptics_detail),
                        checked = hapticsEnabled,
                        onCheckedChange = onSetHaptics,
                    )
                    RowDivider()
                    EdgeSideRow(edgeSide, onSetEdgeSide)
                    RowDivider()
                    val canLockScreen = remember(shadeGestureReady, isRootAvailable) {
                        shadeGestureReady || isRootAvailable
                    }
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_double_tap_lock),
                        detail = stringResource(
                            if (doubleTapToLock && !canLockScreen) R.string.settings_double_tap_lock_not_ready
                            else R.string.settings_double_tap_lock_detail
                        ),
                        checked = doubleTapToLock,
                        onCheckedChange = { enable ->
                            onSetDoubleTapToLock(enable)
                            if (enable && !canLockScreen) {
                                onOpenAccessibilitySettings()
                            }
                        },
                    )
                    if (doubleTapToLock && !canLockScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            FilledChip(
                                label = stringResource(R.string.settings_enable),
                                selected = false,
                                onClick = onOpenAccessibilitySettings,
                            )
                        }
                    }
                    RowDivider()
                    SwitchRow(stringResource(R.string.settings_always_show_az), alwaysShowAz, onSetAlwaysShowAz)
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_shade_gesture), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(
                                    if (shadeGestureReady) R.string.settings_shade_ready
                                    else R.string.settings_shade_not_ready
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        if (!shadeGestureReady) {
                            FilledChip(stringResource(R.string.settings_enable), selected = false, onClick = onOpenAccessibilitySettings)
                        }
                    }
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenDynamicButtonSettings)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_dynamic_button), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_dynamic_button_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenSearchSettings)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_search_title), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_search_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.settings_section_now_playing)) {
                    SwitchRow(stringResource(R.string.settings_now_playing_show), nowPlayingEnabled, onSetNowPlayingEnabled)
                    RowDivider()
                    SwitchRowWithDetail(
                        label = "Visualizador de música",
                        detail = "Exibe um espectro animado baseado no áudio reproduzido pelo sistema. Requer acesso root.",
                        checked = audioVisualizerEnabled,
                        onCheckedChange = onSetAudioVisualizerEnabled,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_show_notifications),
                        detail = stringResource(R.string.settings_show_notifications_detail),
                        checked = showAppNotifications,
                        onCheckedChange = onSetShowAppNotifications,
                    )
                    if (nowPlayingEnabled || showAppNotifications) {
                        RowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        if (nowPlayingListenerEnabled) R.string.settings_notification_access_granted
                                        else R.string.settings_notification_access_missing
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(R.string.settings_notification_access_detail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            FilledChip(stringResource(R.string.settings_open_settings), selected = false, onClick = onOpenNotificationSettings)
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_apps)) }
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                  Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenFavorites)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_favorites), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_favorites_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenHiddenApps)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_hidden_apps), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (hiddenCount == 0) stringResource(R.string.settings_hidden_none)
                                else stringResource(R.string.settings_hidden_count, hiddenCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    RowDivider()
                    var showRestoreDialog by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRestoreDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Restaurar Apps do Sistema", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Recuperar aplicativos de sistema desinstalados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    if (showRestoreDialog) {
                        dev.viagaralauncher.ui.settings.SystemAppRestoreDialog(
                            onDismiss = { showRestoreDialog = false }
                        )
                    }
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_folder_window_popup),
                        detail = stringResource(R.string.settings_folder_window_popup_detail),
                        checked = folderWindowPopup,
                        onCheckedChange = onSetFolderWindowPopup,
                    )
                  }
                }
            }

            item {
                Section(stringResource(R.string.settings_section_backup)) {
                    NavigationRow(
                        label = stringResource(R.string.settings_backup_title),
                        detail = stringResource(R.string.settings_backup_subtitle),
                        onClick = onOpenBackupSettings,
                    )
                }
            }

            item {
                Section(stringResource(R.string.settings_section_about)) {
                    var showCurrentChangelog by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    val appVersionName = remember(context) {
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: "0.59.64"
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.settings_version, appVersionName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "O que há de novo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showCurrentChangelog = true }.padding(vertical = 4.dp)
                        )
                    }

                    if (showCurrentChangelog) {
                        var changelogText by remember { mutableStateOf("") }
                        val context = LocalContext.current
                        LaunchedEffect(Unit) {
                            changelogText = runCatching {
                                context.assets.open("CHANGELOG_LATEST.md").bufferedReader().use { it.readText() }
                            }.getOrDefault("Changelog indisponível.")
                        }

                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { showCurrentChangelog = false },
                            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showCurrentChangelog = false },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {},
                                        ),
                                    shape = RoundedCornerShape(24.dp),
                                    color = dev.viagaralauncher.ui.theme.dynamicSurfaceColor(),
                                    border = BorderStroke(1.dp, dev.viagaralauncher.ui.theme.dynamicBorderColor()),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(22.dp),
                                    ) {
                                        Text(
                                            text = "O que há de novo na v$appVersionName",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 260.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f))
                                                .padding(14.dp)
                                                .verticalScroll(rememberScrollState()),
                                        ) {
                                            dev.viagaralauncher.ui.common.ChangelogMarkdownViewer(
                                                markdown = changelogText,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        Spacer(Modifier.height(20.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            TextButton(onClick = { showCurrentChangelog = false }) {
                                                Text(stringResource(R.string.action_close))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_root_access),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = if (isRootAvailable) {
                                    stringResource(R.string.settings_root_available)
                                } else {
                                    stringResource(R.string.settings_root_unavailable)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRootAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                },
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isRootAvailable) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            },
                        ) {
                            Text(
                                text = if (isRootAvailable) {
                                    stringResource(R.string.settings_root_badge_detected)
                                } else {
                                    stringResource(R.string.settings_root_badge_none)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRootAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
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
        SectionLabel(title)
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
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
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

/** Slider plus a pair of steppers, since dragging to an exact value is fiddly. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    step: Float = 1f,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onValueChange((value - step).coerceIn(range)) },
                enabled = value > range.start,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.settings_less))
            }
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                onClick = { onValueChange((value + step).coerceIn(range)) },
                enabled = value < range.endInclusive,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_more))
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPackRow(packs: List<IconPackRepository.IconPackInfo>, selected: String?, onSelect: (String?) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_icon_pack), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledChip(stringResource(R.string.settings_icon_pack_default), selected == null) { onSelect(null) }
            packs.forEach { pack ->
                FilledChip(pack.label, selected == pack.packageName) { onSelect(pack.packageName) }
            }
        }
        if (packs.isEmpty()) {
            Text(
                stringResource(R.string.settings_icon_pack_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemedIconStyleRow(selected: ThemedIconStyle, onSelect: (ThemedIconStyle) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_themed_icons_style), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemedIconStyle.entries.forEach { style ->
                FilledChip(stringResource(style.labelRes()), selected == style) { onSelect(style) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontRow(selected: AppFont, onSelect: (AppFont) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFont.entries.forEach { f ->
                // Each chip is rendered in the font it selects, so the choice previews itself.
                FilledChip(
                    label = stringResource(f.labelRes()),
                    selected = selected == f,
                    fontFamily = f.toFontFamily(),
                    onClick = { onSelect(f) },
                )
            }
        }
    }
}

@Composable
private fun RowPreview(app: AppInfo?, iconSizeDp: Int, labelSizeSp: Int, font: AppFont) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.settings_preview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app != null) {
                AppIcon(app = app, sizeDp = iconSizeDp)
                Spacer(Modifier.width(16.dp))
                Text(app.label, fontSize = labelSizeSp.sp, fontFamily = font.toFontFamily())
            } else {
                Text(stringResource(R.string.settings_preview_sample), fontSize = labelSizeSp.sp, fontFamily = font.toFontFamily())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextColorRow(selected: TextColorMode, onSelect: (TextColorMode) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_text_colour), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_text_colour_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextColorMode.entries.forEach { mode ->
                FilledChip(stringResource(mode.labelRes()), selected == mode) { onSelect(mode) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EdgeSideRow(selected: EdgeSide, onSelect: (EdgeSide) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_edge_side), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdgeSide.entries.forEach { side ->
                FilledChip(stringResource(side.labelRes()), selected == side) { onSelect(side) }
            }
        }
    }
}

@Composable
private fun FilledChip(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
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
            fontFamily = fontFamily,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NavigationRow(
    label: String,
    detail: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.padding(4.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

private data class ThemeColorDotOption(
    val id: String,
    val nameRes: Int,
    val color: Color,
    val borderColor: Color,
    val checkmarkColor: Color,
)

@Composable
private fun ThemeColorPaletteRow(
    activeThemeId: String,
    hapticsEnabled: Boolean,
    onSelectTheme: (String) -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    val options = remember(colorScheme) {
        listOf(
            ThemeColorDotOption(
                id = "system_dynamic",
                nameRes = R.string.theme_system_dynamic_title,
                color = colorScheme.primary,
                borderColor = colorScheme.outlineVariant.copy(alpha = 0.5f),
                checkmarkColor = colorScheme.onPrimary,
            ),
            ThemeColorDotOption(
                id = "oled_black",
                nameRes = R.string.theme_oled_black_title,
                color = Color.Black,
                borderColor = Color(0xFF333333),
                checkmarkColor = Color(0xFFEDEDED),
            ),
            ThemeColorDotOption(
                id = "dark_modern",
                nameRes = R.string.theme_dark_modern_title,
                color = Color(0xFF1B2733),
                borderColor = Color(0xFF384755),
                checkmarkColor = Color(0xFF7FD1E0),
            ),
            ThemeColorDotOption(
                id = "light_clean",
                nameRes = R.string.theme_light_clean_title,
                color = Color(0xFFF3F6F8),
                borderColor = Color(0xFFB0BCC7),
                checkmarkColor = Color(0xFF10707F),
            ),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_theme_colors),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = activeThemeId == option.id
                ThemeColorDot(
                    option = option,
                    isSelected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            HapticUtil.tick(view, hapticsEnabled)
                            onSelectTheme(option.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeColorDot(
    option: ThemeColorDotOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dotScale",
    )
    val ringSize by animateDpAsState(
        targetValue = if (isSelected) 36.dp else 26.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dotRingSize",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "dotRingAlpha",
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (ringAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .graphicsLayer { alpha = ringAlpha }
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha),
                        shape = CircleShape,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(option.color)
                .border(1.dp, option.borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(150)),
                exit = scaleOut(tween(150)) + fadeOut(tween(150)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(option.nameRes),
                    tint = option.checkmarkColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}