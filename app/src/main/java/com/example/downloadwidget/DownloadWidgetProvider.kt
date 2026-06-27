package com.example.downloadwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private data class AssetInfo(val name: String, val downloadCount: Int)

class DownloadWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_REFRESH = "com.example.downloadwidget.ACTION_REFRESH"
        private const val PREFS_NAME = "download_widget_prefs"
        private const val PREF_API_URL = "pref_api_url"
        private const val PREF_VERSION = "pref_version"
        private const val PREF_ASSET_LIST_JSON = "pref_asset_list_json"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidgetAsync(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_REFRESH == intent.action) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DownloadWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            for (widgetId in widgetIds) {
                updateWidgetAsync(context, manager, widgetId)
            }
        }
    }

    private fun updateWidgetAsync(context: Context, apm: AppWidgetManager, appWidgetId: Int) {
        val pendingResult = goAsync()
        Thread {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val apiUrl = prefs.getString(PREF_API_URL, "https://api.github.com/repos/Acorn-Juice-Solutions/accuvideo-releases/releases") ?: ""
                val version = prefs.getString(PREF_VERSION, "v1.7.5") ?: ""

                val assets = try {
                    fetchAssetList(apiUrl, version)
                } catch (exception: Exception) {
                    emptyList<AssetInfo>()
                }

                saveAssetList(context, assets)
                val totalDownloads = assets.sumOf { it.downloadCount }
                val views = createRemoteViews(context, appWidgetId, version, totalDownloads, assets.isNotEmpty())
                apm.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_asset_list)
                apm.updateAppWidget(appWidgetId, views)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun saveAssetList(context: Context, assets: List<AssetInfo>) {
        val array = JSONArray()
        for (asset in assets) {
            val item = JSONObject()
            item.put("name", asset.name)
            item.put("download_count", asset.downloadCount)
            array.put(item)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_ASSET_LIST_JSON, array.toString())
            .apply()
    }

    private fun createRemoteViews(context: Context, appWidgetId: Int, version: String, totalCount: Int, hasAssets: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
        views.setTextViewText(R.id.widget_release_label, context.getString(R.string.widget_version_label, version))
        views.setTextViewText(R.id.widget_count, if (hasAssets) totalCount.toString() else context.getString(R.string.widget_error))
        views.setTextViewText(R.id.widget_status, if (hasAssets) context.getString(R.string.widget_status_ok) else context.getString(R.string.widget_status_error))

        val listIntent = Intent(context, AssetListRemoteViewsService::class.java)
        listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        listIntent.data = Uri.parse(listIntent.toUri(Intent.URI_INTENT_SCHEME))
        views.setRemoteAdapter(R.id.widget_asset_list, listIntent)
        views.setEmptyView(R.id.widget_asset_list, R.id.widget_empty)

        val settingsIntent = Intent(context, MainActivity::class.java)
        val settingsPending = PendingIntent.getActivity(
            context,
            appWidgetId,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, settingsPending)

        val refreshIntent = Intent(context, DownloadWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPending = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

        return views
    }

    private fun fetchAssetList(apiUrl: String, version: String): List<AssetInfo> {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Network error: ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response")
            val releases = JSONArray(body)
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optString("tag_name") == version) {
                    val assets = release.optJSONArray("assets") ?: JSONArray()
                    val list = mutableListOf<AssetInfo>()
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        list += AssetInfo(
                            asset.optString("name", "asset"),
                            asset.optInt("download_count", 0)
                        )
                    }
                    return list
                }
            }
            throw IOException("Release version not found")
        }
    }
}
