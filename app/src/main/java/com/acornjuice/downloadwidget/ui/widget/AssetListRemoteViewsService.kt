package com.acornjuice.downloadwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.acornjuice.downloadwidget.appContainer
import com.acornjuice.downloadwidget.domain.model.Asset

/**
 * Serves per-row [RemoteViews] for the widget's asset list. Reads assets from the
 * shared [com.acornjuice.downloadwidget.data.local.AssetCacheStore] so this class does
 * no JSON parsing or preference access of its own.
 *
 * **Only used below API 31.** From Android 12 the rows are carried inline by
 * [AssetListBinder] via `RemoteViews.RemoteCollectionItems`; binding a service-backed
 * adapter there stopped the host from applying the rest of the widget. See
 * [WidgetRenderer]'s kdoc for the full story.
 */
class AssetListRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return AssetListFactory(applicationContext, widgetId)
    }
}

private class AssetListFactory(
    context: Context,
    private val widgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private val cache = context.appContainer.assetCacheStore
    private val packageName: String = context.packageName
    private var assets: List<Asset> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        assets = cache.get(widgetId)?.assets.orEmpty()
    }

    override fun onDestroy() {
        assets = emptyList()
    }

    override fun getCount(): Int = assets.size

    override fun getViewAt(position: Int): RemoteViews =
        AssetListBinder.assetRow(packageName, assets[position])

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
