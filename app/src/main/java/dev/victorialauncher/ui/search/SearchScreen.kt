// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.search

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.ui.common.AppIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    allApps: List<AppInfo>,
    nameOverrides: Map<String, String>,
    config: SearchConfig,
    onLaunchApp: (AppInfo, Rect?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SearchCategory.ALL) }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        if (granted) {
            Toast.makeText(context, context.getString(R.string.search_permission_granted_toast), Toast.LENGTH_SHORT).show()
        }
    }

    var appResults by remember { mutableStateOf(emptyList<SearchResult.AppItem>()) }
    var contactResults by remember { mutableStateOf(emptyList<SearchResult.ContactItem>()) }
    var settingResults by remember { mutableStateOf(emptyList<SearchResult.SettingItem>()) }
    var calculationResult by remember { mutableStateOf<SearchResult.CalculationItem?>(null) }

    // Reactive debounced search execution
    LaunchedEffect(query, hasContactsPermission, config, allApps, nameOverrides) {
        val q = query.trim()
        if (q.isEmpty()) {
            appResults = emptyList()
            contactResults = emptyList()
            settingResults = emptyList()
            calculationResult = null
            return@LaunchedEffect
        }

        delay(80) // Smooth micro-debounce for fluid keystroke response

        if (config.includeApps) {
            appResults = SearchRepository.searchApps(q, allApps, nameOverrides)
        } else {
            appResults = emptyList()
        }

        if (config.includeSettings) {
            settingResults = SearchRepository.searchSettings(context, q)
        } else {
            settingResults = emptyList()
        }

        calculationResult = SearchRepository.evaluateCalculation(q)

        if (config.includeContacts && hasContactsPermission) {
            contactResults = SearchRepository.searchContacts(context, q)
        } else {
            contactResults = emptyList()
        }
    }

    // Auto-focus keyboard on screen entry if enabled
    LaunchedEffect(Unit) {
        if (config.autoKeyboard) {
            delay(150)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null) {
            val hadBlurFlag = (window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
            val prevRadius = window.attributes.blurBehindRadius
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val params = window.attributes
            params.blurBehindRadius = 45
            window.attributes = params

            onDispose {
                val p = window.attributes
                p.blurBehindRadius = prevRadius
                window.attributes = p
                if (!hadBlurFlag) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        } else {
            onDispose {}
        }
    }

    val isDark = isSystemInDarkTheme()
    val contentColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    // Translucent background scrim for frosted glass over blurred wallpaper
    val scrimColor = if (isDark) {
        Color.Black.copy(alpha = 0.52f)
    } else {
        Color.White.copy(alpha = 0.58f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Search Input Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = contentColor,
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
                    ),
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
                            tint = primaryColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_hint_complete),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = contentColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                                cursorBrush = SolidColor(primaryColor),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ),
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { query = "" },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.icon_picker_clear_search),
                                    tint = contentColor.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Filter Category Chips
            val categories = listOf(
                SearchCategory.ALL to stringResource(R.string.search_category_all),
                SearchCategory.APPS to stringResource(R.string.search_category_apps),
                SearchCategory.CONTACTS to stringResource(R.string.search_category_contacts),
                SearchCategory.SETTINGS to stringResource(R.string.search_category_settings),
                SearchCategory.WEB to stringResource(R.string.search_category_web),
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(categories) { (category, label) ->
                    val isSelected = selectedCategory == category
                    val chipBg = if (isSelected) {
                        primaryColor
                    } else {
                        if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                    }
                    val chipContentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        contentColor.copy(alpha = 0.85f)
                    }
                    val chipBorder = if (isSelected) null else BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    )

                    Surface(
                        shape = CircleShape,
                        color = chipBg,
                        contentColor = chipContentColor,
                        border = chipBorder,
                        modifier = Modifier.clickable { selectedCategory = category },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            // Non-intrusive Contacts Permission Notice Banner
            if (config.includeContacts && !hasContactsPermission && (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.CONTACTS)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.search_contacts_permission_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                            )
                            Text(
                                text = stringResource(R.string.search_contacts_permission_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.7f),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_enable),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Results List
            val hasQuery = query.trim().isNotEmpty()

            val showApps = (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.APPS) && appResults.isNotEmpty()
            val showContacts = (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.CONTACTS) && contactResults.isNotEmpty()
            val showSettings = (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.SETTINGS) && settingResults.isNotEmpty()
            val showCalculation = (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.APPS) && calculationResult != null
            val showOnline = (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.WEB) && hasQuery && (config.includeWeb || config.includePlayStore)

            val hasAnyResults = showApps || showContacts || showSettings || showCalculation || showOnline

            if (!hasQuery) {
                // Empty state when user hasn't typed anything yet
                SearchEmptyPrompt(
                    hasContactsPermission = hasContactsPermission,
                    onOpenContactsPermission = { requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                )
            } else if (!hasAnyResults) {
                // No matches state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.35f),
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.search_no_results, query),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                launchWebSearch(context, query, config.searchEngine)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.search_action_web))
                        }
                        Button(
                            onClick = {
                                launchPlayStoreSearch(context, query)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = contentColor,
                            ),
                        ) {
                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.search_action_play_store))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
                ) {
                    // 1. Math Calculation Result
                    if (showCalculation && calculationResult != null) {
                        item(key = "calculation") {
                            val calc = calculationResult!!
                            CalculationRow(
                                calc = calc,
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("calc_result", calc.result))
                                    Toast.makeText(context, context.getString(R.string.search_calc_copied, calc.result), Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }

                    // 2. Apps Section
                    if (showApps) {
                        item(key = "header_apps") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_section_apps),
                                count = appResults.size,
                            )
                        }
                        items(
                            items = appResults,
                            key = { "app_${it.app.key}" },
                        ) { appItem ->
                            SearchAppRow(
                                item = appItem,
                                onClick = { bounds -> onLaunchApp(appItem.app, bounds) },
                            )
                        }
                    }

                    // 3. Contacts Section
                    if (showContacts) {
                        item(key = "header_contacts") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_section_contacts),
                                count = contactResults.size,
                            )
                        }
                        items(
                            items = contactResults,
                            key = { "contact_${it.id}_${it.phoneNumber}" },
                        ) { contactItem ->
                            SearchContactRow(
                                item = contactItem,
                                onOpen = {
                                    openContactDetails(context, contactItem)
                                },
                                onCall = {
                                    contactItem.phoneNumber?.let { num ->
                                        dialPhoneNumber(context, num)
                                    }
                                },
                            )
                        }
                    }

                    // 4. System Settings Section
                    if (showSettings) {
                        item(key = "header_settings") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_section_settings),
                                count = settingResults.size,
                            )
                        }
                        items(
                            items = settingResults,
                            key = { "setting_${it.id}" },
                        ) { settingItem ->
                            SearchSettingRow(
                                item = settingItem,
                                onClick = {
                                    openSystemSetting(context, settingItem.intentAction)
                                },
                            )
                        }
                    }

                    // 5. Online Search (Web & Play Store)
                    if (showOnline) {
                        item(key = "header_online") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_section_online),
                                count = null,
                            )
                        }
                        if (config.includeWeb) {
                            item(key = "action_web") {
                                SearchOnlineActionRow(
                                    icon = Icons.Filled.Language,
                                    title = stringResource(R.string.search_web_prompt, query, config.searchEngine.displayName),
                                    subtitle = stringResource(R.string.search_web_detail, config.searchEngine.displayName),
                                    onClick = { launchWebSearch(context, query, config.searchEngine) },
                                )
                            }
                        }
                        if (config.includePlayStore) {
                            item(key = "action_playstore") {
                                SearchOnlineActionRow(
                                    icon = Icons.Filled.ShoppingBag,
                                    title = stringResource(R.string.search_play_store_prompt, query),
                                    subtitle = stringResource(R.string.search_play_store_detail),
                                    onClick = { launchPlayStoreSearch(context, query) },
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
private fun SearchSectionHeader(title: String, count: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.8.sp,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchAppRow(
    item: SearchResult.AppItem,
    onClick: (Rect?) -> Unit,
) {
    var iconBounds by remember { mutableStateOf<Rect?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "searchAppRowPressScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onClick(iconBounds) },
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            app = item.app,
            sizeDp = 42,
            modifier = Modifier.onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    val b = coords.boundsInWindow()
                    val r = Rect(
                        b.left.toInt(),
                        b.top.toInt(),
                        b.right.toInt(),
                        b.bottom.toInt(),
                    )
                    iconBounds = r
                    dev.victorialauncher.ui.transition.AppLaunchTransitionManager.updateIconBounds(item.app.packageName, r)
                }
            },
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchContactRow(
    item: SearchResult.ContactItem,
    onOpen: () -> Unit,
    onCall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Contact Avatar Circle with initial letter
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = item.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.phoneNumber.isNullOrBlank()) {
                Text(
                    text = item.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!item.phoneNumber.isNullOrBlank()) {
            IconButton(
                onClick = onCall,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = stringResource(R.string.search_action_call),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchSettingRow(
    item: SearchResult.SettingItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CalculationRow(
    calc: SearchResult.CalculationItem,
    onCopy: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f),
        ),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onCopy)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Calculate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${calc.expression} =",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = calc.result,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.search_copy_result),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SearchOnlineActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchEmptyPrompt(
    hasContactsPermission: Boolean,
    onOpenContactsPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.search_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.search_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun launchWebSearch(context: Context, query: String, engine: SearchEngine) {
    val encoded = Uri.encode(query.trim())
    val url = engine.searchUrl + encoded
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, context.getString(R.string.search_failed_open_browser), Toast.LENGTH_SHORT).show()
    }
}

private fun launchPlayStoreSearch(context: Context, query: String) {
    val encoded = Uri.encode(query.trim())
    val marketUri = Uri.parse("market://search?q=$encoded&c=apps")
    val intent = Intent(Intent.ACTION_VIEW, marketUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        // Fallback to web browser Play Store url
        val webUri = Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(webIntent) }
    }
}

private fun openContactDetails(context: Context, contact: SearchResult.ContactItem) {
    val uri = if (contact.lookupKey != null && contact.id.isNotBlank()) {
        val contactId = contact.id.toLongOrNull() ?: 0L
        ContactsContract.Contacts.getLookupUri(contactId, contact.lookupKey)
    } else {
        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id)
    }

    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun dialPhoneNumber(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openSystemSetting(context: Context, action: String) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        // Fallback to general settings
        runCatching {
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
