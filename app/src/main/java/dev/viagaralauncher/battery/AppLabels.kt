// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

val LocalShowAppNames = compositionLocalOf { true }

private val appLabels = HashMap<String, String?>()

fun resolveAppLabel(context: Context, packageName: String): String? {
    val pkg = packageName.substringBefore(':')
    if (appLabels.containsKey(pkg)) return appLabels[pkg]
    val label = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() && it != pkg }
    appLabels[pkg] = label
    return label
}

@Composable
fun rememberAppLabel(packageName: String): String? {
    val context = LocalContext.current
    val enabled = LocalShowAppNames.current
    return remember(enabled, context, packageName) {
        if (enabled) resolveAppLabel(context, packageName) else null
    }
}

@Composable
fun rememberAppLabelLine(packageName: String): String =
    rememberAppLabel(packageName)?.let { "$it · $packageName" } ?: packageName

private val appIcons = HashMap<String, android.graphics.drawable.Drawable?>()

fun resolveAppIcon(context: Context, packageName: String): android.graphics.drawable.Drawable? {
    val pkg = packageName.substringBefore(':')
    if (appIcons.containsKey(pkg)) return appIcons[pkg]
    val icon = runCatching {
        context.packageManager.getApplicationIcon(pkg)
    }.getOrNull()
    appIcons[pkg] = icon
    return icon
}

@Composable
fun rememberAppIcon(packageName: String): android.graphics.drawable.Drawable? {
    val context = LocalContext.current
    return remember(context, packageName) {
        resolveAppIcon(context, packageName)
    }
}
