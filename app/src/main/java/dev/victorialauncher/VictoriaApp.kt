// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.victorialauncher.data.AppRepository
import dev.victorialauncher.data.IconPackRepository
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.widget.VictoriaAppWidgetHost
import java.lang.ref.WeakReference

class VictoriaApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var iconPackRepository: IconPackRepository
        private set
    lateinit var widgetHost: VictoriaAppWidgetHost
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
        dev.victorialauncher.update.UpdateManager.init(this)
        dev.victorialauncher.update.UpdateManager.cleanupDownloadedApk(this)
        prefs = Prefs(this)
        appRepository = AppRepository(this)
        iconPackRepository = IconPackRepository(this)
        widgetHost = VictoriaAppWidgetHost(this, HOST_ID)
        dev.victorialauncher.backup.BackupAlarmReceiver.schedulePeriodicCheck(this)
    }

    companion object {
        const val HOST_ID = 1024
        var currentActivity: WeakReference<Activity>? = null
            private set
    }
}