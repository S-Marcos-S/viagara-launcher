// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.root

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.data.AppRepository
import dev.viagaralauncher.root.log.LogLine
import dev.viagaralauncher.ui.common.AppIcon
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Immutable
data class LogFilterAppProcessItem(
    val key: String,
    val label: String,
    val packageName: String?,
    val processName: String?,
    val pid: String? = null,
    val appInfo: AppInfo? = null,
    val isSystem: Boolean = false,
    val isRunning: Boolean = false,
)

object LogFilterSuggestionProvider {

    suspend fun loadSuggestions(
        context: Context,
        rawLogs: List<LogLine> = emptyList(),
    ): List<LogFilterAppProcessItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        // Running processes from ActivityManager
        val runningProcesses = runCatching { am?.runningAppProcesses }.getOrNull().orEmpty()
        val runningProcessMap = runningProcesses.associate { it.processName to it.pid.toString() }

        // Live logs packages and PIDs
        val logPackages = rawLogs.mapNotNull { it.packageName?.takeIf { p -> p.isNotBlank() } }.toSet()
        val logPids = rawLogs.mapNotNull { if (it.pid.isNotBlank()) it.pid to (it.packageName ?: it.tag) else null }.toMap()

        // 1. User Launcher Apps (High Priority)
        val appRepo = AppRepository(context)
        val launcherApps = runCatching { appRepo.queryAllApps() }.getOrDefault(emptyList())
        val launcherPkgSet = launcherApps.map { it.packageName }.toSet()

        val results = mutableListOf<LogFilterAppProcessItem>()

        for (app in launcherApps) {
            val isRunning = runningProcessMap.containsKey(app.packageName) || logPackages.contains(app.packageName)
            val pid = runningProcessMap[app.packageName]
            results.add(
                LogFilterAppProcessItem(
                    key = app.packageName,
                    label = app.label,
                    packageName = app.packageName,
                    processName = app.packageName,
                    pid = pid,
                    appInfo = app,
                    isSystem = false,
                    isRunning = isRunning,
                )
            )
        }

        // 2. Other Installed Packages (System apps, services, providers)
        val installedApps = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        for (ai in installedApps) {
            val pkg = ai.packageName
            if (launcherPkgSet.contains(pkg)) continue

            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg)
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isRunning = runningProcessMap.containsKey(pkg) || logPackages.contains(pkg)
            val pid = runningProcessMap[pkg]

            results.add(
                LogFilterAppProcessItem(
                    key = pkg,
                    label = label,
                    packageName = pkg,
                    processName = pkg,
                    pid = pid,
                    appInfo = AppInfo(ComponentName(pkg, ""), label),
                    isSystem = isSystem,
                    isRunning = isRunning,
                )
            )
        }

        // 3. Native system processes and daemons (system_server, surfaceflinger, logd, etc.)
        for (proc in runningProcesses) {
            val procName = proc.processName
            if (results.none { it.packageName == procName || it.key == procName }) {
                results.add(
                    LogFilterAppProcessItem(
                        key = procName,
                        label = procName,
                        packageName = null,
                        processName = procName,
                        pid = proc.pid.toString(),
                        appInfo = null,
                        isSystem = true,
                        isRunning = true,
                    )
                )
            }
        }

        // 4. Any distinct package or process detected in live logs not yet accounted for
        for (pkg in logPackages) {
            if (results.none { it.packageName == pkg || it.key == pkg }) {
                results.add(
                    LogFilterAppProcessItem(
                        key = pkg,
                        label = pkg,
                        packageName = pkg,
                        processName = pkg,
                        pid = null,
                        appInfo = AppInfo(ComponentName(pkg, ""), pkg),
                        isSystem = pkg.startsWith("android") || pkg.startsWith("com.android"),
                        isRunning = true,
                    )
                )
            }
        }

        // Sort: Running user apps first, user apps second, running system apps third, rest
        results.sortedWith(
            compareBy<LogFilterAppProcessItem> { if (it.isRunning && !it.isSystem) 0 else if (!it.isSystem) 1 else if (it.isRunning) 2 else 3 }
                .thenBy { it.label.lowercase(Locale.ROOT) }
        )
    }

    fun filterSuggestions(
        items: List<LogFilterAppProcessItem>,
        query: String,
        limit: Int = 20,
    ): List<LogFilterAppProcessItem> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            return items.filter { it.isRunning || !it.isSystem }.take(limit)
        }

        return items.filter { item ->
            item.label.lowercase(Locale.ROOT).contains(q) ||
                (item.packageName?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                (item.processName?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                item.pid == q
        }.sortedWith(
            compareBy<LogFilterAppProcessItem> {
                val labelLower = it.label.lowercase(Locale.ROOT)
                val pkgLower = it.packageName?.lowercase(Locale.ROOT) ?: ""
                when {
                    labelLower.startsWith(q) -> 0
                    labelLower.contains(q) -> 1
                    pkgLower.startsWith(q) -> 2
                    pkgLower.contains(q) -> 3
                    else -> 4
                }
            }.thenBy { if (it.isRunning) 0 else 1 }
        ).take(limit)
    }

    fun extractTopTags(rawLogs: List<LogLine>, query: String = "", limit: Int = 10): List<String> {
        val q = query.trim().lowercase(Locale.ROOT)
        val distinctTags = rawLogs.asSequence()
            .map { it.tag.trim() }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }

        return if (q.isEmpty()) {
            distinctTags.take(limit)
        } else {
            distinctTags.filter { it.lowercase(Locale.ROOT).contains(q) }.take(limit)
        }
    }
}

@Composable
fun AppProcessSuggestionRow(
    item: LogFilterAppProcessItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App / Process Icon
        if (item.appInfo != null) {
            AppIcon(
                app = item.appInfo,
                sizeDp = 22,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(0.5.dp, colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(5.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (item.isSystem) Icons.Filled.Memory else Icons.Filled.Terminal,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Label and Package / Process Name
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.label,
                    color = colorScheme.onSurface,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (item.isRunning) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.18f))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = if (item.pid != null) "PID ${item.pid}" else "ATIVO",
                            color = Color(0xFF10B981),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (item.isSystem) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(colorScheme.onSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "SISTEMA",
                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            val subtitle = item.packageName ?: item.processName ?: item.key
            Text(
                text = subtitle,
                color = colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SelectAppProcessDialog(
    rawLogs: List<LogLine> = emptyList(),
    onDismiss: () -> Unit,
    onSelect: (LogFilterAppProcessItem?) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }
    var allItems by remember { mutableStateOf<List<LogFilterAppProcessItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        allItems = LogFilterSuggestionProvider.loadSuggestions(context, rawLogs)
        isLoading = false
    }

    val filteredItems = remember(searchQuery, allItems) {
        LogFilterSuggestionProvider.filterSuggestions(allItems, searchQuery, limit = 50)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, dynamicBorderColor(), RoundedCornerShape(20.dp)),
            color = dynamicSurfaceColor(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Selecionar App",
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fechar",
                            tint = colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Buscar app ou processo...",
                            fontSize = 11.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    },
                    textStyle = TextStyle(fontSize = 11.5.sp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Limpar",
                                    tint = colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // Clear Filter Button (Show all logs)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onSelect(null)
                            onDismiss()
                        },
                    color = colorScheme.primary.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.2f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Exibir todos os logs (sem filtro)",
                            color = colorScheme.primary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.primary,
                        )
                    }
                } else if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nenhum aplicativo ou processo encontrado.",
                            color = colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 11.5.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filteredItems, key = { it.key }) { item ->
                            AppProcessSuggestionRow(
                                item = item,
                                onClick = {
                                    onSelect(item)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

