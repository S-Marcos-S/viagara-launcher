// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.viagaralauncher.R
import dev.viagaralauncher.service.HapticUtil
import dev.viagaralauncher.ui.theme.LocalGlassThemeActive
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import dev.viagaralauncher.ui.theme.glass.GlassStyle
import dev.viagaralauncher.ui.theme.glass.GlassSurface

private data class ThemeOption(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val previewSwatches: List<Color>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(
    currentThemeId: String,
    hapticsEnabled: Boolean,
    onSelectTheme: (String) -> Unit,
    onBack: () -> Unit,
) {
    val view = LocalView.current
    val surface = MaterialTheme.colorScheme.surface
    val colorScheme = MaterialTheme.colorScheme

    val availableThemes = remember(colorScheme) {
        listOf(
            ThemeOption(
                id = "system_dynamic",
                titleRes = R.string.theme_system_dynamic_title,
                descRes = R.string.theme_system_dynamic_desc,
                previewSwatches = listOf(
                    colorScheme.primary,
                    colorScheme.secondaryContainer,
                    colorScheme.surfaceContainerHigh,
                ),
            ),
            ThemeOption(
                id = "glass",
                titleRes = R.string.theme_glass_title,
                descRes = R.string.theme_glass_desc,
                previewSwatches = listOf(
                    Color(0xFF7FD1E0),
                    Color(0x66FFFFFF),
                    Color(0xFF22303E),
                ),
            ),
            ThemeOption(
                id = "oled_black",
                titleRes = R.string.theme_oled_black_title,
                descRes = R.string.theme_oled_black_desc,
                previewSwatches = listOf(
                    Color(0xFF000000),
                    Color(0xFF141414),
                    Color(0xFF7FD1E0),
                ),
            ),
            ThemeOption(
                id = "dark_modern",
                titleRes = R.string.theme_dark_modern_title,
                descRes = R.string.theme_dark_modern_desc,
                previewSwatches = listOf(
                    Color(0xFF1B2733),
                    Color(0xFF22303E),
                    Color(0xFF7FD1E0),
                ),
            ),
            ThemeOption(
                id = "light_clean",
                titleRes = R.string.theme_light_clean_title,
                descRes = R.string.theme_light_clean_desc,
                previewSwatches = listOf(
                    Color(0xFFF3F6F8),
                    Color(0xFFDBE4E8),
                    Color(0xFF10707F),
                ),
            ),
        )
    }

    val isDark = isSystemInDarkTheme()
    val isGlassActive = LocalGlassThemeActive.current || currentThemeId == "glass"
    val backgroundColor = if (isGlassActive) {
        if (isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.45f)
    } else {
        surface
    }
    val topBarColor = if (isGlassActive) Color.Transparent else surface

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.themes_title),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.themes_subtitle),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            items(availableThemes, key = { it.id }) { theme ->
                val isSelected = currentThemeId == theme.id
                ThemeCard(
                    theme = theme,
                    isSelected = isSelected,
                    isGlassActive = isGlassActive,
                    onClick = {
                        HapticUtil.tick(view, hapticsEnabled)
                        onSelectTheme(theme.id)
                    },
                )
            }

            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.themes_custom_header),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            item {
                ComingSoonThemeCard(isGlassActive = isGlassActive)
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: ThemeOption,
    isSelected: Boolean,
    isGlassActive: Boolean = LocalGlassThemeActive.current,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(18.dp)

    val cardContent = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(theme.titleRes),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(theme.descRes),
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                // Color swatches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    theme.previewSwatches.forEach { swatchColor ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(1.dp, colorScheme.outline.copy(alpha = 0.30f), CircleShape),
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Selection indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) colorScheme.primary else Color.Transparent,
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }

    if (isGlassActive) {
        val density = LocalDensity.current
        val cornerPx = with(density) { 18.dp.toPx() }
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onClick),
            cornerPx = cornerPx,
            style = if (isSelected) GlassStyle.DEFAULT.copy(rim = 1.35f, shadow = 0.8f) else GlassStyle.DIALOG,
            tintOpacity = if (isSelected) 60 else (if (isDark) 45 else 55),
            surfaceColor = if (isSelected) colorScheme.primaryContainer.toArgb() else dynamicSurfaceColor(alpha = 1f).toArgb(),
        ) {
            cardContent()
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onClick),
            shape = shape,
            color = if (isSelected) {
                colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
            },
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.40f),
            ),
        ) {
            cardContent()
        }
    }
}

@Composable
private fun ComingSoonThemeCard(isGlassActive: Boolean = LocalGlassThemeActive.current) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(18.dp)

    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.primaryContainer.copy(alpha = 0.40f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.themes_coming_soon_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.themes_coming_soon_desc),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp,
                )
            }
        }
    }

    if (isGlassActive) {
        val density = LocalDensity.current
        val cornerPx = with(density) { 18.dp.toPx() }
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
            cornerPx = cornerPx,
            style = GlassStyle.DIALOG,
            tintOpacity = if (isDark) 40 else 50,
            surfaceColor = dynamicSurfaceColor(alpha = 1f).toArgb(),
        ) {
            content()
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = colorScheme.surfaceContainerLow.copy(alpha = 0.50f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f)),
        ) {
            content()
        }
    }
}
