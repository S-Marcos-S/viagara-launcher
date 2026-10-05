// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.viagaralauncher.data.AppRepository
import dev.viagaralauncher.data.IconPackRepository
import dev.viagaralauncher.data.Prefs
import dev.viagaralauncher.widget.ViagaraAppWidgetHost
import java.lang.ref.WeakReference

class ViagaraApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var iconPackRepository: IconPackRepository
        private set
    lateinit var widgetHost: ViagaraAppWidgetHost
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivity = WeakReference(activity)
            }
            override fun onActivityStarted(activity: Activity) {
                currentActivity = WeakReference(activity)
            }
            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity?.get() == activity) {
                    currentActivity = null
                }
            }
        })
        dev.viagaralauncher.update.UpdateManager.init(this)
        dev.viagaralauncher.update.UpdateManager.cleanupDownloadedApk(this)
        prefs = Prefs(this)
        appRepository = AppRepository(this)
        iconPackRepository = IconPackRepository(this)
        widgetHost = ViagaraAppWidgetHost(this, HOST_ID)
        dev.viagaralauncher.backup.BackupAlarmReceiver.schedulePeriodicCheck(this)
        if (dev.viagaralauncher.battery.DrainNotificationManager.isNotificationEnabled(this)) {
            dev.viagaralauncher.battery.DrainNotificationManager.getInstance(this).startNotification()
        }
        val anomalyWatcher = dev.viagaralauncher.root.anomaly.BackgroundAnomalyWatcher.getInstance(this)
        if (anomalyWatcher.config.value.isEnabled) {
            anomalyWatcher.start()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        dev.viagaralauncher.root.anomaly.BackgroundAnomalyWatcher.getInstance(this).onSystemTrimMemory(level)
    }

    companion object {
        const val HOST_ID = 1024
        var currentActivity: WeakReference<Activity>? = null
            private set
    }
}