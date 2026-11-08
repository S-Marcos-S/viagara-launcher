// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import dev.viagaralauncher.data.Prefs
import dev.viagaralauncher.wallpaper.DailyWallpaperManager
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.viagaralauncher.wallpaper.WallpaperCatalog
import dev.viagaralauncher.wallpaper.WallpaperCategory
import dev.viagaralauncher.wallpaper.WallpaperItem
import dev.viagaralauncher.wallpaper.WallpaperRepository
import dev.viagaralauncher.wallpaper.VideoLiveWallpaperService
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

private data class PreviewTransitionState(
    val item: WallpaperItem,
    val sourceRect: Rect,
    val thumbnailBitmap: Bitmap?,
    val cornerRadiusDp: Float,
)

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val surface = MaterialTheme.colorScheme.surface

    var catalog by remember { mutableStateOf<WallpaperCatalog?>(null) }
    var selectedCategoryId by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var isLoadingCatalog by remember { mutableStateOf(true) }
    var activePreviewState by remember { mutableStateOf<PreviewTransitionState?>(null) }
    var isClosing by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    val animProgress = remember { Animatable(0f) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val favoriteIds by WallpaperRepository.favoriteIds.collectAsState()
    val prefs = remember { Prefs(context) }
    val isAutoDailyEnabled by prefs.autoDailyWallpaperEnabled.collectAsState(initial = false)
    val wallpaperColumns by prefs.wallpaperColumns.collectAsState(initial = 2)

    LaunchedEffect(Unit) {
        WallpaperRepository.initFavorites(context)
        dev.viagaralauncher.wallpaper.WallpaperNotificationManager.cancelNotification(context)
        catalog = WallpaperRepository.loadCatalog(context)
        isLoadingCatalog = false
    }

    val filteredWallpapers = remember(catalog, selectedCategoryId, searchQuery, favoriteIds) {
        val all = catalog?.wallpapers ?: emptyList()
        val query = searchQuery.trim().lowercase()

        val baseList = when {
            selectedCategoryId.equals("all", ignoreCase = true) -> all
            selectedCategoryId.equals("favorites", ignoreCase = true) -> all.filter { favoriteIds.contains(it.id) }
            else -> all.filter {
                it.category.equals(selectedCategoryId, ignoreCase = true)
            }
        }

        if (query.isEmpty()) {
            baseList
        } else {
            baseList.filter { item ->
                item.name.lowercase().contains(query) ||
                item.id.lowercase().contains(query) ||
                item.id.replace("tg_", "").contains(query) ||
                item.searchTerms.any { it.lowercase().contains(query) } ||
                item.tags.any { it.lowercase().contains(query) } ||
                item.category.lowercase().contains(query) ||
                item.author.lowercase().contains(query)
            }
        }
    }

    val dismissPreview: () -> Unit = {
        if (!isClosing && !isApplying && activePreviewState != null) {
            isClosing = true
            coroutineScope.launch {
                animProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing,
                    )
                )
                activePreviewState = null
                isClosing = false
            }
        }
    }

    BackHandler(enabled = activePreviewState != null) {
        if (!isApplying) dismissPreview()
    }

    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoordinates = it }
    ) {
        Scaffold(
            containerColor = surface,
            topBar = {
            Surface(
                color = surface,
                tonalElevation = if (isScrolled) 2.dp else 0.dp,
                shadowElevation = if (isScrolled) 3.dp else 0.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.wallpaper_picker_title),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        actions = {
                            val haptic = LocalHapticFeedback.current
                            val columnsDesc = stringResource(R.string.wallpaper_columns_desc, wallpaperColumns)
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val nextColumns = when (wallpaperColumns) {
                                        2 -> 3
                                        3 -> 4
                                        else -> 2
                                    }
                                    coroutineScope.launch {
                                        prefs.setWallpaperColumns(nextColumns)
                                    }
                                },
                                modifier = Modifier.semantics { contentDescription = columnsDesc },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .border(
                                            0.8.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${wallpaperColumns}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                                        context.startActivity(Intent.createChooser(intent, "Escolher papel de parede"))
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = stringResource(R.string.wallpaper_picker_gallery),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )

                    val categories = catalog?.categories ?: WallpaperRepository.DEFAULT_CATEGORIES
                    WallpaperCategoriesRow(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onSelectCategory = { newCategory ->
                            if (!selectedCategoryId.equals(newCategory, ignoreCase = true)) {
                                selectedCategoryId = newCategory
                                coroutineScope.launch {
                                    gridState.scrollToItem(0)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    )

                    if (isScrolled) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(wallpaperColumns),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(if (wallpaperColumns >= 4) 10.dp else if (wallpaperColumns == 3) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (wallpaperColumns >= 4) 8.dp else if (wallpaperColumns == 3) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (wallpaperColumns >= 4) 8.dp else if (wallpaperColumns == 3) 10.dp else 14.dp),
        ) {
            // Minimalist Search Bar (no lugar do antigo card "Fotos e Galeria")
            item(span = { GridItemSpan(maxLineSpan) }) {
                WallpaperSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                DailyWallpaperToggleCard(
                    enabled = isAutoDailyEnabled,
                    onToggle = { enabled ->
                        coroutineScope.launch {
                            DailyWallpaperManager.setEnabled(context, enabled)
                        }
                    },
                )
            }

            if (isLoadingCatalog && filteredWallpapers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            } else if (filteredWallpapers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    stringResource(R.string.wallpaper_search_no_results, searchQuery)
                                } else if (selectedCategoryId.equals("favorites", ignoreCase = true)) {
                                    "Nenhum papel de parede favorito ainda.\nToque no coração de qualquer papel de parede para favoritá-lo."
                                } else {
                                    stringResource(R.string.wallpaper_picker_empty)
                                },
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                items(filteredWallpapers, key = { it.id }) { item ->
                    val isBeingPreviewed = activePreviewState?.item?.id == item.id
                    WallpaperGridItem(
                        item = item,
                        columns = wallpaperColumns,
                        isFavorite = favoriteIds.contains(item.id),
                        isBeingPreviewed = isBeingPreviewed,
                        onToggleFavorite = {
                            WallpaperRepository.toggleFavorite(context, item.id)
                        },
                        onClick = { boundsInRoot, thumb ->
                            val root = rootCoordinates
                            val relativeRect = if (root != null && root.isAttached && boundsInRoot.width > 0) {
                                val rootBounds = root.boundsInRoot()
                                boundsInRoot.translate(-rootBounds.left, -rootBounds.top)
                            } else {
                                boundsInRoot
                            }
                            val cornerRadius = when {
                                wallpaperColumns >= 4 -> 10f
                                wallpaperColumns == 3 -> 14f
                                else -> 18f
                            }
                            activePreviewState = PreviewTransitionState(
                                item = item,
                                sourceRect = relativeRect,
                                thumbnailBitmap = thumb,
                                cornerRadiusDp = cornerRadius,
                            )
                            isClosing = false
                            coroutineScope.launch {
                                animProgress.snapTo(0f)
                                animProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 340,
                                        easing = FastOutSlowInEasing,
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }

        // Full-screen Wallpaper Preview & Application Overlay with opening and closing transition
        activePreviewState?.let { previewState ->
            WallpaperPreviewOverlay(
                item = previewState.item,
                sourceRect = previewState.sourceRect,
                initialBitmap = previewState.thumbnailBitmap,
                cornerRadiusDp = previewState.cornerRadiusDp,
                animProgress = animProgress.value,
                isClosing = isClosing,
                isFavorite = favoriteIds.contains(previewState.item.id),
                onToggleFavorite = { WallpaperRepository.toggleFavorite(context, previewState.item.id) },
                isApplying = isApplying,
                onDismiss = dismissPreview,
                onApply = { flags, customBitmap ->
                    isApplying = true
                    coroutineScope.launch {
                        if (flags == -1) {
                            // Live Wallpaper application
                            val videoFile = WallpaperRepository.getCachedOrDownloadFile(context, previewState.item.fullUrl)
                            isApplying = false
                            if (videoFile != null && VideoLiveWallpaperService.setVideoFile(context, videoFile)) {
                                DailyWallpaperManager.onWallpaperApplied(context, previewState.item.id)
                                runCatching {
                                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                        putExtra(
                                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                            ComponentName(context, VideoLiveWallpaperService::class.java)
                                        )
                                    }
                                    context.startActivity(intent)
                                    activePreviewState = null
                                    onBack()
                                }.onFailure {
                                    Toast.makeText(context, R.string.wallpaper_apply_error, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, R.string.wallpaper_apply_error, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val result = if (customBitmap != null) {
                                WallpaperRepository.applyWallpaper(context, previewState.item, customBitmap, flags)
                            } else {
                                WallpaperRepository.applyWallpaper(context, previewState.item, flags)
                            }
                            isApplying = false
                            if (result.isSuccess) {
                                DailyWallpaperManager.onWallpaperApplied(context, previewState.item.id)
                                Toast.makeText(context, R.string.wallpaper_applied_success, Toast.LENGTH_SHORT).show()
                                activePreviewState = null
                                onBack()
                            } else {
                                Toast.makeText(context, R.string.wallpaper_apply_error, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DailyWallpaperToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle(!enabled) },
        color = if (enabled) colorScheme.primaryContainer.copy(alpha = 0.35f) else colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) colorScheme.primary.copy(alpha = 0.50f) else colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (enabled) colorScheme.primary else colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (enabled) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.daily_wallpaper_title),
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.daily_wallpaper_desc),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }

            Spacer(Modifier.width(10.dp))

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.onPrimary,
                    checkedTrackColor = colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun WallpaperSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = shape,
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wallpaper_search_hint),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.wallpaper_search_clear),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperGridItem(
    item: WallpaperItem,
    columns: Int = 2,
    isFavorite: Boolean,
    isBeingPreviewed: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: (Rect, Bitmap?) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (columns >= 4) 10.dp else if (columns == 3) 14.dp else 18.dp)

    val fallbackColor = remember(item.primaryColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(item.primaryColorHex)) }
            .getOrDefault(Color(0xFF1E1E1E))
    }

    val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, item.thumbnailUrl) {
        value = WallpaperRepository.getCachedOrDownloadBitmap(context, item.thumbnailUrl)
    }

    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val heartPadding = if (columns >= 4) 4.dp else if (columns == 3) 6.dp else 8.dp
    val heartBoxSize = if (columns >= 4) 24.dp else if (columns == 3) 28.dp else 32.dp
    val heartIconSize = if (columns >= 4) 13.dp else if (columns == 3) 15.dp else 18.dp

    val livePadding = if (columns >= 4) 4.dp else if (columns == 3) 6.dp else 8.dp
    val liveTextSize = if (columns >= 4) 7.sp else if (columns == 3) 8.sp else 9.sp
    val liveCorner = if (columns >= 4) 4.dp else 6.dp

    val titlePaddingH = if (columns >= 4) 5.dp else if (columns == 3) 8.dp else 12.dp
    val titlePaddingV = if (columns >= 4) 4.dp else if (columns == 3) 6.dp else 10.dp
    val titleFontSize = if (columns >= 4) 9.sp else if (columns == 3) 11.sp else 13.sp
    val authorFontSize = if (columns >= 4) 8.sp else if (columns == 3) 9.sp else 11.sp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.52f)
            .onGloballyPositioned { itemCoordinates = it }
            .graphicsLayer {
                alpha = if (isBeingPreviewed) 0f else 1f
            }
            .clip(shape)
            .background(fallbackColor)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
            .clickable(onClick = {
                val coords = itemCoordinates
                val rect = if (coords != null && coords.isAttached) {
                    coords.boundsInRoot()
                } else {
                    Rect.Zero
                }
                onClick(rect, thumbnailBitmap)
            }),
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }

        // Live Wallpaper indicator tag for video items
        if (item.isLive || item.type == "video") {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(livePadding)
                    .clip(RoundedCornerShape(liveCorner))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = if (columns >= 4) 4.dp else 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "▶ LIVE",
                    color = Color.White,
                    fontSize = liveTextSize,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Heart favorite button above each wallpaper (top right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(heartPadding)
                .size(heartBoxSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center,
        ) {
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorito",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(heartIconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.FavoriteBorder,
                    contentDescription = "Favoritar",
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(heartIconSize),
                )
            }
        }

        // Gradient overlay at the bottom with title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    )
                )
                .padding(horizontal = titlePaddingH, vertical = titlePaddingV),
        ) {
            Column {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (columns < 4 || (item.author.isNotBlank() && item.author != "Viagara")) {
                    Text(
                        text = item.author,
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = authorFontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperPreviewOverlay(
    item: WallpaperItem,
    sourceRect: Rect,
    initialBitmap: Bitmap?,
    cornerRadiusDp: Float,
    animProgress: Float,
    isClosing: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onApply: (Int, Bitmap?) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    val isVideo = item.isLive || item.type == "video"

    val fullBitmap by produceState<Bitmap?>(initialValue = null, item.fullUrl) {
        if (!isVideo) {
            value = WallpaperRepository.getCachedOrDownloadBitmap(context, item.fullUrl)
        }
    }

    val videoFile by produceState<java.io.File?>(initialValue = null, item.fullUrl) {
        if (isVideo) {
            value = WallpaperRepository.getCachedOrDownloadFile(context, item.fullUrl)
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var showClassifyDialog by remember { mutableStateOf(false) }

    val fallbackColor = remember(item.primaryColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(item.primaryColorHex)) }
            .getOrDefault(Color(0xFF1E1E1E))
    }

    val cornerRadiusPx = remember(cornerRadiusDp, density) {
        with(density) { cornerRadiusDp.dp.toPx() }
    }

    val p = animProgress.coerceIn(0f, 1f)

    // Calculate effective scale and offset (reverting zoom smoothly during closing)
    val effectiveScale = if (isClosing) 1f + (scale - 1f) * p else scale
    val effectiveOffsetX = if (isClosing) offsetX * p else offsetX
    val effectiveOffsetY = if (isClosing) offsetY * p else offsetY

    val dragDismissFraction = (dragOffsetY / 500f).coerceIn(0f, 0.7f)
    val scrimAlpha = (p * (1f - dragDismissFraction)).coerceIn(0f, 1f)
    val controlsAlpha = if (isClosing) {
        p.coerceIn(0f, 1f)
    } else {
        (((p - 0.40f) / 0.60f).coerceIn(0f, 1f) * (1f - (dragOffsetY / 200f).coerceIn(0f, 1f))).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .pointerInput(scale, isApplying, isClosing) {
                if (scale <= 1.05f && !isApplying && !isClosing) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY > 140f) {
                                onDismiss()
                            } else {
                                coroutineScope.launch {
                                    val anim = Animatable(dragOffsetY)
                                    anim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                    ) {
                                        dragOffsetY = value
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                val anim = Animatable(dragOffsetY)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                ) {
                                    dragOffsetY = value
                                }
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0 || dragOffsetY > 0f) {
                                change.consume()
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            }
                        }
                    )
                }
            },
    ) {
        // Scrim background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(Color.Black)
        )

        // Media hero container
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val sRect = if (sourceRect.width > 0 && sourceRect.height > 0) {
                        sourceRect
                    } else {
                        Rect(
                            constraints.maxWidth * 0.2f,
                            constraints.maxHeight * 0.2f,
                            constraints.maxWidth * 0.8f,
                            constraints.maxHeight * 0.8f
                        )
                    }

                    val targetW = constraints.maxWidth.toFloat()
                    val targetH = constraints.maxHeight.toFloat()

                    val currentLeft = lerp(sRect.left, 0f, p).roundToInt()
                    val currentTop = (lerp(sRect.top, 0f, p) + dragOffsetY * p).roundToInt()
                    val currentW = lerp(sRect.width, targetW, p).roundToInt().coerceAtLeast(1)
                    val currentH = lerp(sRect.height, targetH, p).roundToInt().coerceAtLeast(1)

                    val placeable = measurable.measure(Constraints.fixed(currentW, currentH))

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(currentLeft, currentTop)
                    }
                }
                .graphicsLayer {
                    val currentRadiusPx = lerp(cornerRadiusPx, 0f, p)
                    shape = RoundedCornerShape(currentRadiusPx)
                    clip = true
                }
                .background(fallbackColor)
        ) {
            // Media content: Canvas for images, VideoView for video
            if (isVideo) {
                if (videoFile != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f)
                                    mp.start()
                                }
                                setVideoPath(videoFile!!.absolutePath)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (initialBitmap != null) {
                    val bmp = initialBitmap
                    val bmpW = bmp.width.toFloat()
                    val bmpH = bmp.height.toFloat()
                    val imageBmp = remember(bmp) { bmp.asImageBitmap() }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val vw = size.width
                        val vh = size.height
                        if (vw > 0f && vh > 0f && bmpW > 0f && bmpH > 0f && !bmp.isRecycled) {
                            val baseScale = maxOf(vw / bmpW, vh / bmpH)
                            withTransform({
                                translate(left = vw / 2f, top = vh / 2f)
                                scale(scaleX = baseScale, scaleY = baseScale, pivot = Offset.Zero)
                                translate(left = -bmpW / 2f, top = -bmpH / 2f)
                            }) {
                                drawImage(imageBmp)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.wallpaper_loading),
                                color = Color.White,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            } else {
                val currentBitmap = fullBitmap ?: initialBitmap
                if (currentBitmap != null) {
                    val bmp = currentBitmap
                    val bmpW = bmp.width.toFloat()
                    val bmpH = bmp.height.toFloat()
                    val imageBmp = remember(bmp) { bmp.asImageBitmap() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bmp, p) {
                                if (p >= 0.95f) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                                        scale = newScale

                                        val vw = if (viewportSize.width > 0) viewportSize.width.toFloat() else context.resources.displayMetrics.widthPixels.toFloat()
                                        val vh = if (viewportSize.height > 0) viewportSize.height.toFloat() else context.resources.displayMetrics.heightPixels.toFloat()
                                        if (vw > 0f && vh > 0f && bmpW > 0f && bmpH > 0f) {
                                            val baseScale = maxOf(vw / bmpW, vh / bmpH)
                                            val currentRenderedW = bmpW * baseScale * newScale
                                            val currentRenderedH = bmpH * baseScale * newScale
                                            val maxOffsetX = maxOf(0f, (currentRenderedW - vw) / 2f)
                                            val maxOffsetY = maxOf(0f, (currentRenderedH - vh) / 2f)
                                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                        } else {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        }
                                    }
                                }
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val vw = size.width
                            val vh = size.height
                            if (vw <= 0f || vh <= 0f || bmpW <= 0f || bmpH <= 0f || bmp.isRecycled) return@Canvas

                            val baseScale = maxOf(vw / bmpW, vh / bmpH)
                            val totalScale = baseScale * effectiveScale
                            val cx = vw / 2f + effectiveOffsetX
                            val cy = vh / 2f + effectiveOffsetY

                            withTransform({
                                translate(left = cx, top = cy)
                                scale(scaleX = totalScale, scaleY = totalScale, pivot = Offset.Zero)
                                translate(left = -bmpW / 2f, top = -bmpH / 2f)
                            }) {
                                drawImage(imageBmp)
                            }
                        }

                        if (fullBitmap == null && p >= 0.90f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 70.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.wallpaper_loading),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.wallpaper_loading),
                                color = Color.White,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        // Top Navigation & Reset Zoom Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .graphicsLayer {
                    alpha = controlsAlpha
                    translationY = -(1f - controlsAlpha) * 30f
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isVideo && (scale > 1.05f || kotlin.math.abs(offsetX) > 5f || kotlin.math.abs(offsetY) > 5f)) {
                Surface(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = stringResource(R.string.wallpaper_action_reset_crop),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.wallpaper_action_reset_crop),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(1.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    enabled = !isApplying && controlsAlpha > 0.5f,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Favorito" else "Favoritar",
                        tint = if (isFavorite) Color(0xFFE53935) else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    enabled = !isApplying && controlsAlpha > 0.5f,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White,
                    )
                }
            }
        }

        // Bottom Action Panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    alpha = controlsAlpha
                    translationY = (1f - controlsAlpha) * 60f
                },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color.Black.copy(alpha = 0.80f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                val authorUrl = item.authorUrl
                if (!authorUrl.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorUrl))
                                    context.startActivity(intent)
                                }
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = item.author,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "↗",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    Text(
                        text = item.author,
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Wallpaper ID", item.id)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "ID copiado: ${item.id}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "ID: ${item.id}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copiar ID",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp),
                        )
                    }

                    val resBmp = fullBitmap ?: initialBitmap
                    if (resBmp != null) {
                        val w = resBmp.width
                        val h = resBmp.height
                        val label = when {
                            w >= 3840 || h >= 3840 -> "4K UHD"
                            w >= 2560 || h >= 2560 -> "2K QHD"
                            w >= 1920 || h >= 1920 -> "FHD"
                            w >= 1280 || h >= 1280 -> "HD"
                            else -> "SD"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "$label • ${w}x${h}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    // Botão minimalista de classificação manual ao lado da informação de resolução
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { showClassifyDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.wallpaper_action_classify),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.wallpaper_action_classify),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    if (!isVideo) {
                        Text(
                            text = stringResource(R.string.wallpaper_hint_pinch_pan),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isApplying) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.wallpaper_applying),
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    val getFinalFramedBitmap: () -> Bitmap? = {
                        val src = fullBitmap ?: initialBitmap
                        if (src != null) {
                            val vw = if (viewportSize.width > 0) viewportSize.width.toFloat() else context.resources.displayMetrics.widthPixels.toFloat()
                            val vh = if (viewportSize.height > 0) viewportSize.height.toFloat() else context.resources.displayMetrics.heightPixels.toFloat()
                            WallpaperRepository.frameBitmapToDisplay(
                                context = context,
                                src = src,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                viewportWidth = vw,
                                viewportHeight = vh,
                            )
                        } else {
                            null
                        }
                    }

                    if (isVideo) {
                        Button(
                            onClick = { onApply(-1, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.wallpaper_apply_live),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        WallpaperManager.FLAG_SYSTEM
                                    } else 0
                                    onApply(flag, getFinalFramedBitmap())
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.wallpaper_apply_home),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Button(
                                    onClick = {
                                        onApply(WallpaperManager.FLAG_LOCK, getFinalFramedBitmap())
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.surfaceContainerHigh,
                                        contentColor = colorScheme.onSurface,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(R.string.wallpaper_apply_lock),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                    } else 0
                                    onApply(flag, getFinalFramedBitmap())
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.secondaryContainer,
                                    contentColor = colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.wallpaper_apply_both),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showClassifyDialog) {
            WallpaperClassificationDialog(
                item = item,
                onDismiss = { showClassifyDialog = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallpaperClassificationDialog(
    item: WallpaperItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var titleText by remember { mutableStateOf(item.name) }
    var newTagInput by remember { mutableStateOf("") }
    var tagsList by remember {
        mutableStateOf(
            (item.tags + item.searchTerms).filter { it.isNotBlank() }.distinct()
        )
    }
    var isSubmitting by remember { mutableStateOf(false) }

    fun addTag() {
        val trimmed = newTagInput.trim()
        if (trimmed.isNotEmpty() && !tagsList.any { it.equals(trimmed, ignoreCase = true) }) {
            tagsList = tagsList + trimmed
            newTagInput = ""
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E1E),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.wallpaper_classify_dialog_title),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "ID: ${item.id} • " + stringResource(R.string.wallpaper_classify_dialog_desc),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Campo: Título da Imagem
                Text(
                    text = stringResource(R.string.wallpaper_classify_title_label),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Campo: Adicionar Tag
                Text(
                    text = stringResource(R.string.wallpaper_classify_tag_label),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (newTagInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.wallpaper_classify_tag_hint),
                                    color = Color.White.copy(alpha = 0.40f),
                                    fontSize = 14.sp,
                                )
                            }
                            BasicTextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                ),
                                cursorBrush = SolidColor(colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { addTag() }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Botão Adicionar Tag
                    Surface(
                        onClick = { addTag() },
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primary,
                        modifier = Modifier.height(46.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.wallpaper_classify_tag_add),
                                color = colorScheme.onPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Lista de Tags Adicionadas
                Text(
                    text = stringResource(R.string.wallpaper_classify_tags_added, tagsList.size),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))

                if (tagsList.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wallpaper_classify_no_tags),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        tagsList.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = tag,
                                        color = Color.White.copy(alpha = 0.90f),
                                        fontSize = 12.sp,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { tagsList = tagsList - tag },
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remover tag",
                                            tint = Color.White.copy(alpha = 0.70f),
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }

                    Button(
                        onClick = {
                            if (titleText.isNotBlank()) {
                                isSubmitting = true
                                coroutineScope.launch {
                                    WallpaperRepository.submitClassification(
                                        context = context,
                                        item = item,
                                        newTitle = titleText.trim(),
                                        newTags = tagsList,
                                    )
                                    Toast.makeText(
                                        context,
                                        R.string.wallpaper_classify_saved_locally,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    isSubmitting = false
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !isSubmitting && titleText.isNotBlank(),
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(text = stringResource(R.string.wallpaper_classify_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperCategoriesRow(
    categories: List<WallpaperCategory>,
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categories.forEach { category ->
            val isSelected = category.id.equals(selectedCategoryId, ignoreCase = true)
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                label = "category_chip_bg",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "category_chip_content",
            )

            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectCategory(category.id)
                    },
                shape = CircleShape,
                color = backgroundColor,
                border = if (!isSelected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                } else null,
            ) {
                Text(
                    text = getCategoryDisplayName(category),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun getCategoryDisplayName(category: WallpaperCategory): String {
    return when (category.id.lowercase()) {
        "all" -> stringResource(R.string.wallpaper_category_all)
        "favorites" -> "Favoritos"
        "oled" -> stringResource(R.string.wallpaper_category_oled)
        "minimal" -> stringResource(R.string.wallpaper_category_minimal)
        "abstract" -> stringResource(R.string.wallpaper_category_abstract)
        "space" -> stringResource(R.string.wallpaper_category_space)
        "nature" -> stringResource(R.string.wallpaper_category_nature)
        "anime" -> stringResource(R.string.wallpaper_category_anime)
        "games" -> stringResource(R.string.wallpaper_category_games)
        "cyberpunk" -> stringResource(R.string.wallpaper_category_cyberpunk)
        "city" -> stringResource(R.string.wallpaper_category_city)
        "cars" -> stringResource(R.string.wallpaper_category_cars)
        "animals" -> stringResource(R.string.wallpaper_category_animals)
        "fantasy" -> stringResource(R.string.wallpaper_category_fantasy)
        else -> category.label.ifBlank { category.id.replaceFirstChar { it.uppercase() } }
    }
}
