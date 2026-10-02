// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.viagaralauncher.data.AppFont
import dev.viagaralauncher.data.ThemedIconStyle
import dev.viagaralauncher.service.StatusBarFader
import dev.viagaralauncher.ui.ViagaraNavHost
import dev.viagaralauncher.ui.common.IconConfig
import dev.viagaralauncher.ui.common.LocalIconConfig
import dev.viagaralauncher.ui.theme.ViagaraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a pull-down keeps the status bar on screen before it fades away again. */
private const val STATUS_BAR_PEEK_MS = 5000L

class MainActivity : ComponentActivity() {

    /** Bumped whenever HOME is pressed while we're already showing, so overlays can close. */
    private var homeIntentTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.viagaralauncher.ui.transition.AppLaunchTransitionManager.handleGestureContract(this, intent)
        handleUpdateIntent(intent)
        handleLogViewerIntent(intent)
        handleBatteryStatsIntent(intent)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).show(
            androidx.core.view.WindowInsetsCompat.Type.statusBars()
        )
        val app = application as ViagaraApp

        setContent {
            val hideStatusBar by app.prefs.hideStatusBar.collectAsState(initial = false)
            // A short pull-down peeks the status bar, then it slides away again.
            var statusBarPeek by remember { mutableStateOf(false) }
            LaunchedEffect(statusBarPeek) {
                if (statusBarPeek) {
                    delay(STATUS_BAR_PEEK_MS)
                    statusBarPeek = false
                }
            }
            LaunchedEffect(hideStatusBar, statusBarPeek) {
                StatusBarFader.setVisible(window, visible = !hideStatusBar || statusBarPeek)
            }

            val font by app.prefs.font.collectAsState(initial = AppFont.SYSTEM)
            val iconPackPackage by app.prefs.iconPackPackage.collectAsState(initial = null)
            val iconOverrides by app.prefs.iconOverrides.collectAsState(initial = emptyMap())
            val themedIcons by app.prefs.themedIcons.collectAsState(initial = false)
            val themedIconStyle by app.prefs.themedIconStyle.collectAsState(initial = ThemedIconStyle.MATERIAL_YOU)
            val iconConfig = remember(iconPackPackage, iconOverrides, themedIcons, themedIconStyle) {
                IconConfig(iconPackPackage, iconOverrides, themedIcons, themedIconStyle)
            }

            ViagaraTheme(font = font) {
                CompositionLocalProvider(LocalIconConfig provides iconConfig) {
                    ViagaraNavHost(
                        app = app,
                        homeIntentTick = homeIntentTick,
                        font = font,
                        hideStatusBar = hideStatusBar,
                        iconPackPackage = iconPackPackage,
                        iconOverrides = iconOverrides,
                        onPeekStatusBar = { statusBarPeek = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isExplicitAction = intent.action == dev.viagaralauncher.update.UpdateManager.ACTION_OPEN_UPDATE_CHANGELOG ||
            intent.getBooleanExtra(dev.viagaralauncher.update.UpdateManager.EXTRA_OPEN_UPDATE, false) ||
            intent.action == dev.viagaralauncher.battery.DrainNotificationManager.ACTION_OPEN_BATTERY_STATS ||
            intent.getBooleanExtra(dev.viagaralauncher.battery.DrainNotificationManager.EXTRA_OPEN_BATTERY_STATS, false) ||
            intent.hasExtra(dev.viagaralauncher.root.log.CrashManager.EXTRA_OPEN_CRASH_ID) ||
            intent.hasExtra(dev.viagaralauncher.root.log.CrashManager.EXTRA_CRASH_PACKAGE)

        if (!isExplicitAction) {
            // Pressing HOME re-delivers the intent to us; treat it as "go back to the home screen".
            homeIntentTick++
        }
        dev.viagaralauncher.ui.transition.AppLaunchTransitionManager.handleGestureContract(this, intent)
        handleUpdateIntent(intent)
        handleLogViewerIntent(intent)
        handleBatteryStatsIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        dev.viagaralauncher.ui.transition.AppLaunchTransitionManager.onLauncherResume()
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == dev.viagaralauncher.update.UpdateManager.ACTION_OPEN_UPDATE_CHANGELOG ||
            intent.getBooleanExtra(dev.viagaralauncher.update.UpdateManager.EXTRA_OPEN_UPDATE, false)
        ) {
            dev.viagaralauncher.update.UpdateManager.requestShowUpdateChangelog()
        }
    }

    private fun handleBatteryStatsIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == dev.viagaralauncher.battery.DrainNotificationManager.ACTION_OPEN_BATTERY_STATS ||
            intent.getBooleanExtra(dev.viagaralauncher.battery.DrainNotificationManager.EXTRA_OPEN_BATTERY_STATS, false) ||
            intent.getBooleanExtra("open_battery_stats", false)
        ) {
            dev.viagaralauncher.battery.BatteryStatsEvents.open()
        }
    }

    private fun handleLogViewerIntent(intent: Intent?) {
        if (intent == null) return
        val crashId = intent.getStringExtra(dev.viagaralauncher.root.log.CrashManager.EXTRA_OPEN_CRASH_ID)
        val crashPkg = intent.getStringExtra(dev.viagaralauncher.root.log.CrashManager.EXTRA_CRASH_PACKAGE)
        if (crashId != null || crashPkg != null) {
            dev.viagaralauncher.root.log.LogViewerEvents.open(crashPkg, "crashes")
        }
    }

    override fun onStart() {
        super.onStart()
        val app = application as ViagaraApp
        app.widgetHost.startListening()
        lifecycleScope.launch(Dispatchers.IO) {
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this@MainActivity)
            app.prefs.pruneInvalidWidgetIds { id ->
                runCatching { appWidgetManager.getAppWidgetInfo(id) != null }.getOrDefault(false)
            }
            dev.viagaralauncher.backup.BackupManager.checkAndRunAutoBackup(this@MainActivity, app.prefs)
        }
    }

    override fun onStop() {
        (application as ViagaraApp).widgetHost.stopListening()
        // The fader holds a static controller for this window; don't outlive the Activity.
        StatusBarFader.release()
        super.onStop()
    }
}