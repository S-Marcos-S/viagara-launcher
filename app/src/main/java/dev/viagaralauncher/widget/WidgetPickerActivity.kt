// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.widget

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.viagaralauncher.R
import dev.viagaralauncher.ViagaraApp
import dev.viagaralauncher.ui.theme.ViagaraTheme

private const val REQUEST_CONFIGURE_WIDGET = 1001
private const val TAG = "WidgetPickerActivity"

class WidgetPickerActivity : ComponentActivity() {

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var widgetHost: ViagaraAppWidgetHost
    private var pendingWidgetId: Int = -1

    private val bindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) proceedAfterBind(pendingWidgetId) else cancel()
        }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            val configuredId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode == RESULT_OK) {
                finishWithWidgetId(configuredId)
            } else {
                cancel()
            }
        }
    }

    private data class AppGroup(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val widgets: List<AppWidgetProviderInfo>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetManager = AppWidgetManager.getInstance(this)
        widgetHost = (application as ViagaraApp).widgetHost

        setContent {
            ViagaraTheme {
                WidgetPickerScreen(
                    appWidgetManager = appWidgetManager,
                    packageManager = packageManager,
                    onBack = { cancel() },
                    onPickWidget = { info -> pick(info) },
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WidgetPickerScreen(
        appWidgetManager: AppWidgetManager,
        packageManager: PackageManager,
        onBack: () -> Unit,
        onPickWidget: (AppWidgetProviderInfo) -> Unit,
    ) {
        val context = LocalContext.current
        val providers = remember {
            runCatching { appWidgetManager.installedProviders ?: emptyList() }.getOrDefault(emptyList())
        }

        val allGroups = remember(providers) {
            providers.groupBy { it.provider.packageName }
                .map { (pkg, widgets) ->
                    val appInfo = runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
                    AppGroup(
                        packageName = pkg,
                        label = appInfo?.loadLabel(packageManager)?.toString() ?: pkg,
                        icon = appInfo?.let { runCatching { it.loadIcon(packageManager) }.getOrNull() },
                        widgets = widgets.sortedBy { it.loadLabel(packageManager).lowercase() },
                    )
                }
                .sortedBy { it.label.lowercase() }
        }

        var searchQuery by rememberSaveable { mutableStateOf("") }
        val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

        val trimmedQuery = searchQuery.trim()
        val filteredGroups = remember(allGroups, trimmedQuery) {
            if (trimmedQuery.isEmpty()) {
                allGroups
            } else {
                allGroups.mapNotNull { group ->
                    val appMatches = group.label.contains(trimmedQuery, ignoreCase = true)
                    val matchingWidgets = group.widgets.filter { widget ->
                        appMatches || widget.loadLabel(packageManager).contains(trimmedQuery, ignoreCase = true)
                    }
                    if (matchingWidgets.isNotEmpty()) {
                        group.copy(widgets = matchingWidgets)
                    } else null
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.widget_add),
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.widget_search_hint),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.action_reset),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )

                    if (allGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.widget_none_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (filteredGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.widget_no_matches, trimmedQuery),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filteredGroups, key = { it.packageName }) { group ->
                                val isExpanded = if (trimmedQuery.isNotEmpty()) true else (expandedMap[group.packageName] ?: false)
                                AppAccordionCard(
                                    group = group,
                                    isExpanded = isExpanded,
                                    onToggleExpand = {
                                        expandedMap[group.packageName] = !isExpanded
                                    },
                                    onPickWidget = onPickWidget,
                                    packageManager = packageManager,
                                    densityDpi = context.resources.configuration.densityDpi,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppAccordionCard(
        group: AppGroup,
        isExpanded: Boolean,
        onToggleExpand: () -> Unit,
        onPickWidget: (AppWidgetProviderInfo) -> Unit,
        packageManager: PackageManager,
        densityDpi: Int,
    ) {
        val chevronRotation by animateFloatAsState(
            targetValue = if (isExpanded) 180f else 0f,
            animationSpec = tween(200),
            label = "chevronRotation",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpand)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawableIcon(group.icon, 38.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val countText = if (group.widgets.size == 1) {
                            stringResource(R.string.widget_count_single)
                        } else {
                            stringResource(R.string.widget_count, group.widgets.size)
                        }
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        group.widgets.forEach { widgetInfo ->
                            WidgetPreviewCard(
                                info = widgetInfo,
                                packageManager = packageManager,
                                densityDpi = densityDpi,
                                onClick = { onPickWidget(widgetInfo) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetPreviewCard(
        info: AppWidgetProviderInfo,
        packageManager: PackageManager,
        densityDpi: Int,
        onClick: () -> Unit,
    ) {
        val context = LocalContext.current
        val widgetTitle = remember(info) { info.loadLabel(packageManager) }
        val dimensions = remember(info) { calculateWidgetDimensions(info, context) }
        val description = remember(info) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { info.loadDescription(context)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            } else null
        }

        val previewDrawable = remember(info) {
            runCatching { info.loadPreviewImage(context, densityDpi) }.getOrNull()
        }
        val iconDrawable = remember(info) {
            runCatching { info.loadIcon(context, densityDpi) }.getOrNull()
        }

        OutlinedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = widgetTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = dimensions,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }

                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewDrawable != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    adjustViewBounds = true
                                }
                            },
                            update = { it.setImageDrawable(previewDrawable) },
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            DrawableIcon(iconDrawable, 44.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = widgetTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun calculateWidgetDimensions(info: AppWidgetProviderInfo, context: Context): String {
        val span = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.targetCellWidth > 0 && info.targetCellHeight > 0
        ) {
            info.targetCellWidth to info.targetCellHeight
        } else {
            val density = context.resources.displayMetrics.density
            val minWidthDp = (info.minWidth / density).toInt()
            val minHeightDp = (info.minHeight / density).toInt()
            val cols = ((minWidthDp + 30) / 70).coerceIn(1, 5)
            val rows = ((minHeightDp + 30) / 70).coerceIn(1, 6)
            cols to rows
        }
        return "${span.first} × ${span.second}"
    }

    private fun pick(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        val bound = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && info.profile != null) {
                appWidgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
            } else {
                appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
            }
        }.getOrDefault(false)

        if (bound) {
            proceedAfterBind(id)
        } else {
            try {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && info.profile != null) {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                    }
                }
                bindLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting widget bind permission for id $id", e)
                cancel()
            }
        }
    }

    private fun proceedAfterBind(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        val configure = info?.configure
        if (configure != null) {
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } else {
                null
            }
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(
                    this,
                    id,
                    0,
                    REQUEST_CONFIGURE_WIDGET,
                    options
                )
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Configure activity not found for widget $id, adding directly", e)
                finishWithWidgetId(id)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException starting configure activity for widget $id, completing bind", e)
                finishWithWidgetId(id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start widget configure activity for widget $id", e)
                finishWithWidgetId(id)
            }
        } else {
            finishWithWidgetId(id)
        }
    }

    private fun finishWithWidgetId(id: Int) {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }

    private fun cancel() {
        if (pendingWidgetId != -1) widgetHost.deleteAppWidgetId(pendingWidgetId)
        setResult(RESULT_CANCELED)
        finish()
    }
}

@Composable
private fun DrawableIcon(drawable: Drawable?, size: androidx.compose.ui.unit.Dp) {
    if (drawable != null) {
        AndroidView(
            modifier = Modifier.size(size),
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { it.setImageDrawable(drawable) },
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Widgets,
                contentDescription = null,
                modifier = Modifier.size(size * 0.6f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}