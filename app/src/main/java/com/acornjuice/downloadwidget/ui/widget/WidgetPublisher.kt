package com.acornjuice.downloadwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.acornjuice.downloadwidget.R

/**
 * Single choke point for pushing a [WidgetState] to the launcher.
 *
 * Every path that can change what a widget shows — provider `onUpdate`, the tap refresh,
 * [com.acornjuice.downloadwidget.worker.RefreshWorker] and
 * [com.acornjuice.downloadwidget.worker.RefreshWatchdogWorker] — goes through here, so the
 * "render, then notify the collection" pair cannot drift apart between call sites.
 */
object WidgetPublisher {

    /**
     * Renders [state] and hands it to the host.
     *
     * Call this on the **main thread**: some `AppWidgetHost` implementations drop updates
     * pushed from a background thread while an earlier update is still pending, which is one
     * of the ways a widget used to get stranded showing the `Loading` spinner.
     */
    @Suppress("DEPRECATION")
    fun publish(
        context: Context,
        appWidgetManager: AppWidgetManager,
        kind: WidgetKind,
        widgetId: Int,
        state: WidgetState,
    ) {
        appWidgetManager.updateAppWidget(
            widgetId,
            WidgetRenderer.render(
                context = context,
                layoutId = kind.layoutRes,
                widgetId = widgetId,
                state = state,
                providerClass = kind.providerClass,
            ),
        )
        // Only the pre-API-31 service-backed adapter needs this: from API 31 the rows ride
        // inside the RemoteViews above, so they are already current. Issuing it anyway was
        // part of what kept the host on a stale view tree — see WidgetRenderer's kdoc.
        if (AssetListBinder.usesServiceBackedCollection) {
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_asset_list)
        }
    }
}
