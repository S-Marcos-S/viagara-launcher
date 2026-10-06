// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.wallpaper

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import dev.viagaralauncher.wallpaper.WallpaperItem
import dev.viagaralauncher.wallpaper.WallpaperRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val surface = MaterialTheme.colorScheme.surface

    var wallpapers by remember { mutableStateOf<List<WallpaperItem>>(emptyList()) }
    var isLoadingCatalog by remember { mutableStateOf(true) }
    var previewingWallpaper by remember { mutableStateOf<WallpaperItem?>(null) }
    var isApplying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        wallpapers = WallpaperRepository.loadCatalog(context)
        isLoadingCatalog = false
    }

    BackHandler(enabled = previewingWallpaper != null) {
        if (!isApplying) previewingWallpaper = null
    }

    Scaffold(
        containerColor = surface,
        topBar = {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header card: Choose from System / Google Photos
            item(span = { GridItemSpan(2) }) {
                SystemWallpaperCard(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                            context.startActivity(Intent.createChooser(intent, "Escolher papel de parede"))
                        } catch (_: Exception) {}
                    }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.wallpaper_picker_curated),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (isLoadingCatalog && wallpapers.isEmpty()) {
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
            } else if (wallpapers.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = stringResource(R.string.wallpaper_picker_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(wallpapers, key = { it.id }) { item ->
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
                onApply = { flags ->
                    isApplying = true
                    coroutineScope.launch {
                        val result = WallpaperRepository.applyWallpaper(context, item.fullUrl, flags)
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
            )
        }
    }
}

@Composable
private fun SystemWallpaperCard(
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wallpaper_picker_gallery),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.wallpaper_picker_gallery_desc),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
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
    onApply: (Int) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val fullBitmap by produceState<Bitmap?>(initialValue = null, item.fullUrl) {
        value = WallpaperRepository.getCachedOrDownloadBitmap(context, item.fullUrl)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // High-res preview background
        if (fullBitmap != null) {
            Image(
                bitmap = fullBitmap!!.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
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

        // Top Close Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
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
                Text(
                    text = item.author,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 13.sp,
                )

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    WallpaperManager.FLAG_SYSTEM
                                } else 0
                                onApply(flag)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.wallpaper_apply_home),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Button(
                                onClick = { onApply(WallpaperManager.FLAG_LOCK) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    contentColor = colorScheme.onSurface,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.wallpaper_apply_lock),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                } else 0
                                onApply(flag)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.wallpaper_apply_both),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
