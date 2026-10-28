// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.home

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import android.widget.Toast
import dev.viagaralauncher.root.AppUninstallManager
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import dev.viagaralauncher.update.DownloadStatus
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import dev.viagaralauncher.data.Prefs
import dev.viagaralauncher.wallpaper.DailyWallpaperManager
import dev.viagaralauncher.wallpaper.WallpaperRepository
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.viagaralauncher.ui.theme.LocalGlassThemeActive
import dev.viagaralauncher.ui.theme.glass.GlassSurface
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import dev.viagaralauncher.update.UpdateManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomeOptionsBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTaskManager: () -> Unit = {},
    onManageFavorites: () -> Unit,
    onAddWidget: () -> Unit,
    onOpenClockStyle: () -> Unit = {},
    onOpenThemes: () -> Unit = {},
    onOpenWallpaperPicker: () -> Unit = {},
    onEditLayout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }
    val updateInfo by UpdateManager.updateAvailable.collectAsState()
    val downloadStatus by UpdateManager.downloadStatus.collectAsState()
    var showChangelogDialog by remember { mutableStateOf(false) }

    val prefs = remember { Prefs(context) }
    val haptic = LocalHapticFeedback.current
    val isAutoDailyEnabled by prefs.autoDailyWallpaperEnabled.collectAsState(initial = false)
    val dailyWallpaperIdFlow by prefs.dailyWallpaperCurrentId.collectAsState(initial = "")
    val dailyWallpaperIdMem by DailyWallpaperManager.currentWallpaperId.collectAsState()
    val dailyWallpaperId = dailyWallpaperIdMem.ifBlank { dailyWallpaperIdFlow }
    val isChangingWallpaper by DailyWallpaperManager.isChangingWallpaper.collectAsState()
    val favoriteIds by WallpaperRepository.favoriteIds.collectAsState()
    val isCurrentFavorite = remember(dailyWallpaperId, favoriteIds) {
        dailyWallpaperId.isNotBlank() && favoriteIds.contains(dailyWallpaperId)
    }

    BackHandler(enabled = visible) {
        onDismiss()
    }

    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetY.snapTo(0f)
            showChangelogDialog = false
            UpdateManager.checkForUpdates(coroutineScope, force = true)
            WallpaperRepository.initFavorites(context)
            WallpaperRepository.initDisliked(context)
            DailyWallpaperManager.syncCurrentWallpaper(context)
            DailyWallpaperManager.checkAndAdvanceIfNewDay(context, coroutineScope)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val isGlassActive = LocalGlassThemeActive.current
    val density = LocalDensity.current
    val topCornerPx = with(density) { 24.dp.toPx() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetModifier = Modifier
                .animateEnterExit(
                    enter = slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                    exit = slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(
                            durationMillis = 200,
                            easing = FastOutLinearInEasing,
                        ),
                    ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                )
                .offset { IntOffset(0, dragOffsetY.value.coerceAtLeast(0f).roundToInt()) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val next = (dragOffsetY.value + delta).coerceAtLeast(0f)
                            dragOffsetY.snapTo(next)
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        coroutineScope.launch {
                            // velocity < 0: gesture moving/flung UPWARDS
                            // velocity > 0: gesture moving/flung DOWNWARDS
                            val isFlingUp = velocity < -150f
                            val isFlingDown = velocity > 600f

                            val shouldDismiss = when {
                                isFlingUp -> false
                                isFlingDown -> true
                                velocity < 0f -> dragOffsetY.value > 280f
                                else -> dragOffsetY.value > 160f
                            }

                            if (shouldDismiss) {
                                onDismiss()
                            } else {
                                val initialVel = if (velocity < 0f) velocity else 0f
                                dragOffsetY.animateTo(
                                    targetValue = 0f,
                                    initialVelocity = initialVel,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        }
                    },
                )
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )

            val sheetInnerContent: @Composable ColumnScope.() -> Unit = {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.primary.copy(alpha = 0.50f))
                        .align(Alignment.CenterHorizontally),
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.options_title),
                        color = colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (isAutoDailyEnabled) {
                        Spacer(Modifier.width(8.dp))
                        DailyWallpaperFeedbackRow(
                            isFavorite = isCurrentFavorite,
                            isChanging = isChangingWallpaper,
                            onLike = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    val success = DailyWallpaperManager.handleLike(context)
                                    if (success) {
                                        Toast.makeText(context, R.string.daily_wallpaper_liked_feedback, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDislike = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    val changed = DailyWallpaperManager.advanceToNextWallpaper(context, isUserDislike = true)
                                    if (changed) {
                                        Toast.makeText(context, R.string.daily_wallpaper_disliked_feedback, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, R.string.wallpaper_apply_error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                updateInfo?.takeIf { it.hasUpdate }?.let { update ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorScheme.primaryContainer.copy(alpha = 0.35f))
                            .border(
                                width = 1.dp,
                                color = colorScheme.primary.copy(alpha = 0.40f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.update_available_title),
                                color = colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colorScheme.primary)
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = update.displayVersion,
                                    color = colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Dois botões lado a lado: Mudanças e Atualizar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Botão 1: Mudanças (abre o balão flutuante transparente com blur)
                            OutlinedButton(
                                onClick = { showChangelogDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colorScheme.primary,
                                ),
                                border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.40f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.update_action_changelog),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            // Botão 2: Atualizar / Baixar / Instalar
                            when (val status = downloadStatus) {
                                is DownloadStatus.Downloading -> {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            disabledContainerColor = colorScheme.primary.copy(alpha = 0.6f),
                                            disabledContentColor = colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.update_downloading_progress, status.progressPercent),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                                is DownloadStatus.Finished -> {
                                    Button(
                                        onClick = {
                                            status.fileUri?.let { UpdateManager.promptInstall(context, it) }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.update_action_install),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = {
                                            UpdateManager.startDownload(context, update)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.update_action_download),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }

                OptionItem(
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.settings_title),
                    onClick = { onDismiss(); onOpenSettings() },
                )

                val isRoot = remember { AppUninstallManager.isRootAvailable() }
                if (isRoot) {
                    OptionItem(
                        icon = Icons.Filled.Speed,
                        label = stringResource(R.string.task_manager_title),
                        onClick = { onDismiss(); onOpenTaskManager() },
                    )
                }

                OptionItem(
                    icon = Icons.Filled.Image,
                    label = stringResource(R.string.home_option_wallpaper),
                    onClick = { onDismiss(); onOpenWallpaperPicker() },
                )

                OptionItem(
                    icon = Icons.Filled.Palette,
                    label = stringResource(R.string.home_option_themes),
                    onClick = { onDismiss(); onOpenThemes() },
                )

                OptionItem(
                    icon = Icons.Filled.Checklist,
                    label = stringResource(R.string.home_choose_favorites),
                    onClick = { onDismiss(); onManageFavorites() },
                )

                OptionItem(
                    icon = Icons.Filled.Widgets,
                    label = stringResource(R.string.widget_add),
                    onClick = { onDismiss(); onAddWidget() },
                )

                OptionItem(
                    icon = Icons.Filled.Tune,
                    label = stringResource(R.string.action_edit_layout),
                    onClick = { onDismiss(); onEditLayout() },
                )

                OptionItem(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.settings_clock_style),
                    onClick = { onDismiss(); onOpenClockStyle() },
                )

                Spacer(Modifier.height(8.dp))
            }

            if (isGlassActive) {
                GlassSurface(
                    modifier = sheetModifier,
                    topCornerPx = topCornerPx,
                    bottomCornerPx = 0f,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        content = sheetInnerContent,
                    )
                }
            } else {
                Column(
                    modifier = sheetModifier
                        .background(lerp(colorScheme.surfaceContainerLow, colorScheme.primary, 0.10f))
                        .border(
                            BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    content = sheetInnerContent,
                )
            }
        }
    }

    if (showChangelogDialog && updateInfo != null) {
        UpdateChangelogDialog(
            update = updateInfo!!,
            onDismissRequest = { showChangelogDialog = false },
            onDownload = { useRoot ->
                UpdateManager.startDownload(context, updateInfo!!, autoInstallWithRoot = useRoot)
            },
        )
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badge: String? = null,
    highlight: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (highlight) colorScheme.primaryContainer
                    else colorScheme.primary.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) colorScheme.onPrimaryContainer else colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badge,
                    color = colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun launchWallpaperPicker(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        context.startActivity(Intent.createChooser(intent, "Escolher papel de parede"))
    } catch (_: Exception) {}
}

@Composable
private fun DailyWallpaperFeedbackRow(
    isFavorite: Boolean,
    isChanging: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = stringResource(R.string.daily_wallpaper_prompt),
            color = colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 6.dp),
        )

        if (isChanging) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.primary,
                )
            }
        } else {
            // Like Button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFavorite) colorScheme.primary.copy(alpha = 0.20f)
                        else colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    .clickable(onClick = onLike),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ThumbUp,
                    contentDescription = stringResource(R.string.daily_wallpaper_like),
                    tint = if (isFavorite) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            // Dislike Button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colorScheme.onSurface.copy(alpha = 0.08f))
                    .clickable(onClick = onDislike),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ThumbDown,
                    contentDescription = stringResource(R.string.daily_wallpaper_dislike),
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

