// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch

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
    var previewingWallpaper by remember { mutableStateOf<WallpaperItem?>(null) }
    var isApplying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dev.viagaralauncher.wallpaper.WallpaperNotificationManager.cancelNotification(context)
        catalog = WallpaperRepository.loadCatalog(context)
        isLoadingCatalog = false
    }

    val filteredWallpapers = remember(catalog, selectedCategoryId, searchQuery) {
        val all = catalog?.wallpapers ?: emptyList()
        val query = searchQuery.trim().lowercase()

        val baseList = if (selectedCategoryId.equals("all", ignoreCase = true)) {
            all
        } else {
            all.filter {
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

    BackHandler(enabled = previewingWallpaper != null) {
        if (!isApplying) previewingWallpaper = null
    }

    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }
    }

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
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Minimalist Search Bar (no lugar do antigo card "Fotos e Galeria")
            item(span = { GridItemSpan(2) }) {
                WallpaperSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
            }

            if (isLoadingCatalog && filteredWallpapers.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
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
                item(span = { GridItemSpan(2) }) {
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
                    WallpaperGridItem(
                        item = item,
                        onClick = { previewingWallpaper = item }
                    )
                }
            }
        }
    }

    // Full-screen Wallpaper Preview & Application Dialog/Overlay
    AnimatedVisibility(
        visible = previewingWallpaper != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        previewingWallpaper?.let { item ->
            WallpaperPreviewOverlay(
                item = item,
                isApplying = isApplying,
                onDismiss = {
                    if (!isApplying) previewingWallpaper = null
                },
                onApply = { flags, customBitmap ->
                    isApplying = true
                    coroutineScope.launch {
                        if (flags == -1) {
                            // Live Wallpaper application
                            val videoFile = WallpaperRepository.getCachedOrDownloadFile(context, item.fullUrl)
                            isApplying = false
                            if (videoFile != null && VideoLiveWallpaperService.setVideoFile(context, videoFile)) {
                                runCatching {
                                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                        putExtra(
                                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                            ComponentName(context, VideoLiveWallpaperService::class.java)
                                        )
                                    }
                                    context.startActivity(intent)
                                    previewingWallpaper = null
                                    onBack()
                                }.onFailure {
                                    Toast.makeText(context, R.string.wallpaper_apply_error, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, R.string.wallpaper_apply_error, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val result = if (customBitmap != null) {
                                WallpaperRepository.applyWallpaper(context, item, customBitmap, flags)
                            } else {
                                WallpaperRepository.applyWallpaper(context, item, flags)
                            }
                            isApplying = false
                            if (result.isSuccess) {
                                Toast.makeText(context, R.string.wallpaper_applied_success, Toast.LENGTH_SHORT).show()
                                previewingWallpaper = null
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
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)

    val fallbackColor = remember(item.primaryColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(item.primaryColorHex)) }
            .getOrDefault(Color(0xFF1E1E1E))
    }

    val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, item.thumbnailUrl) {
        value = WallpaperRepository.getCachedOrDownloadBitmap(context, item.thumbnailUrl)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.52f)
            .clip(shape)
            .background(fallbackColor)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick),
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
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "▶ LIVE",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
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
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.author,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WallpaperPreviewOverlay(
    item: WallpaperItem,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onApply: (Int, Bitmap?) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

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
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it },
    ) {
        // High-res preview background (Image or Video)
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
        } else if (fullBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 3.5f)
                            scale = newScale

                            if (viewportSize.width > 0 && viewportSize.height > 0) {
                                val maxOffsetX = (viewportSize.width * (newScale - 1f)) / 2f
                                val maxOffsetY = (viewportSize.height * (newScale - 1f)) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            } else {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = fullBitmap!!.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
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

        // Top Navigation & Reset Zoom Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
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

            IconButton(
                onClick = onDismiss,
                enabled = !isApplying,
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

        // Bottom Action Panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
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

                    if (fullBitmap != null) {
                        val w = fullBitmap!!.width
                        val h = fullBitmap!!.height
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
                        val src = fullBitmap
                        if (src != null && viewportSize.width > 0 && viewportSize.height > 0) {
                            val viewW = viewportSize.width.toFloat()
                            val viewH = viewportSize.height.toFloat()
                            val bmpW = src.width.toFloat()
                            val bmpH = src.height.toFloat()

                            val baseScale = maxOf(viewW / bmpW, viewH / bmpH)
                            val totalScale = baseScale * scale

                            val cx = viewW / 2f + offsetX
                            val cy = viewH / 2f + offsetY

                            val targetH = maxOf(1920, src.height)
                            val targetW = ((targetH.toFloat() / viewH) * viewW).toInt()

                            val resultBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(resultBmp)
                            val matrix = Matrix()

                            val normFactor = targetH.toFloat() / viewH
                            matrix.postTranslate(-bmpW / 2f, -bmpH / 2f)
                            matrix.postScale(totalScale * normFactor, totalScale * normFactor)
                            matrix.postTranslate(cx * normFactor, cy * normFactor)

                            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                            canvas.drawBitmap(src, matrix, paint)
                            resultBmp
                        } else {
                            src
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
