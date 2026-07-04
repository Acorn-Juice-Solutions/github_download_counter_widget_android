package com.example.downloadwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONArray


class AssetListRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d("AssetListService", "onGetViewFactory called")
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return AssetListRemoteViewsFactory(applicationContext, appWidgetId)
    }
}

class AssetListRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private val TAG = "AssetListFactory"
    private val prefsName = "download_widget_prefs"
    private val prefsKey = "pref_asset_list_json"
    private var assets: List<AssetInfo> = emptyList()

    override fun onCreate() {
        Log.d(TAG, "onCreate")
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged")
        loadAssets()
    }

    override fun onDestroy() {
        assets = emptyList()
    }

    override fun getCount(): Int = assets.size

    override fun getViewAt(position: Int): RemoteViews {
        Log.d(TAG, "getViewAt: $position")
        val asset = assets[position]
        val itemView = RemoteViews(context.packageName, R.layout.widget_list_item)
        itemView.setTextViewText(R.id.widget_asset_name, asset.name)
        itemView.setTextViewText(R.id.widget_asset_count, asset.downloadCount.toString())
        return itemView
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun loadAssets() {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val key = "${prefsKey}_$appWidgetId"
        val json = prefs.getString(key, null)
            ?: prefs.getString(prefsKey, "[]")
            ?: "[]"

        val array = JSONArray(json)
        val list = mutableListOf<AssetInfo>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list += AssetInfo(
                obj.optString("name", "asset"),
                obj.optInt("download_count", 0)
            )
        }
        assets = list
    }
}
