package com.acornjuice.downloadwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.acornjuice.downloadwidget.R
import com.acornjuice.downloadwidget.appContainer
import com.acornjuice.downloadwidget.domain.model.Asset

/**
 * Serves per-row [RemoteViews] for the widget's asset list. Reads assets from the
 * shared [com.acornjuice.downloadwidget.data.local.AssetCacheStore] so this class does
 * no JSON parsing or preference access of its own.
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

    override fun getViewAt(position: Int): RemoteViews {
        val asset = assets[position]
        return RemoteViews(packageName, R.layout.widget_list_item).apply {
            setTextViewText(R.id.widget_asset_name, asset.name)
            setTextViewText(R.id.widget_asset_count, asset.downloadCount.toString())
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
