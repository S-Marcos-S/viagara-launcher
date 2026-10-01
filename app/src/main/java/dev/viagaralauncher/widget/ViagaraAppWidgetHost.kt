// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Custom AppWidgetHostView that suppresses Android's default framework widget padding (API 14+),
 * ensuring external app widgets align flush with the launcher's layout margins and grid.
 */
class ViagaraAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    init {
        super.setPadding(0, 0, 0, 0)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        // Enforce zero padding so widget bounds align flush with the launcher's content margin
        super.setPadding(0, 0, 0, 0)
    }
}

class ViagaraAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return ViagaraAppWidgetHostView(context)
    }
}