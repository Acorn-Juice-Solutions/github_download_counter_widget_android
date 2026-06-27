package com.example.downloadwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException


class DownloadWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val TAG = "DownloadWidget"
        private const val ACTION_REFRESH = "com.example.downloadwidget.ACTION_REFRESH"
        private const val PREFS_NAME = "download_widget_prefs"
        private const val PREF_API_URL = "pref_api_url"
        private const val PREF_VERSION = "pref_version"
        private const val PREF_ASSET_LIST_JSON = "pref_asset_list_json"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        Thread {
            try {
                for (widgetId in appWidgetIds) {
                    updateWidgetSync(context, appWidgetManager, widgetId)
                }
            } finally {
                pendingResult?.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_REFRESH == intent.action) {
            val pendingResult = goAsync()
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DownloadWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            Thread {
                try {
                    for (widgetId in widgetIds) {
                        updateWidgetSync(context, manager, widgetId)
                    }
                } finally {
                    pendingResult?.finish()
                }
            }.start()
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            // Si quieres limpiar preferencias específicas por widgetId podrías hacerlo aquí.
            // Por ahora, como las preferencias son globales, solo registramos el borrado.
            Log.d(TAG, "Widgets deleted: ${appWidgetIds.joinToString()}")
        }.apply()
    }

    private fun updateWidgetSync(context: Context, apm: AppWidgetManager, appWidgetId: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val apiUrl = prefs.getString(PREF_API_URL, "https://api.github.com/repos/Acorn-Juice-Solutions/accuvideo-releases/releases") ?: ""
            val version = prefs.getString(PREF_VERSION, "v1.7.5") ?: ""

            val assets = try {
                Log.d(TAG, "Fetching assets from: $apiUrl for version: $version")
                fetchAssetList(apiUrl, version)
            } catch (exception: Exception) {
                Log.e(TAG, "Error fetching assets: ${exception.message}", exception)
                emptyList<AssetInfo>()
            }

            saveAssetList(context, assets)
            val totalDownloads = assets.sumOf { it.downloadCount }
            val views = createRemoteViews(context, appWidgetId, version, totalDownloads, assets.isNotEmpty())
            
            apm.updateAppWidget(appWidgetId, views)
            apm.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_asset_list)
            Log.d(TAG, "Widget $appWidgetId updated successfully with $totalDownloads downloads")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error updating widget $appWidgetId", e)
        }
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
        views.setTextViewText(R.id.widget_count, if (hasAssets) totalCount.toString() else context.getString(R.string.widget_error))
        
        if (hasAssets) {
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_ok))
            views.setInt(R.id.widget_status, "setBackgroundResource", R.drawable.status_background)
        } else {
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_error))
            views.setInt(R.id.widget_status, "setBackgroundResource", R.drawable.status_error_background)
        }

        val listIntent = Intent(context, AssetListRemoteViewsService::class.java)
        listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        listIntent.data = Uri.parse(listIntent.toUri(Intent.URI_INTENT_SCHEME))
        views.setRemoteAdapter(R.id.widget_asset_list, listIntent)
        views.setEmptyView(R.id.widget_asset_list, R.id.widget_empty)

        // Tint icons
        val iconColor = context.getColor(R.color.secondary_text)
        views.setInt(R.id.widget_refresh, "setColorFilter", iconColor)
        views.setInt(R.id.widget_settings, "setColorFilter", iconColor)

        // Settings Button
        val settingsIntent = Intent(context, MainActivity::class.java)
        val settingsPending = PendingIntent.getActivity(
            context,
            appWidgetId,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_settings, settingsPending)

        // Refresh Button
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
            .header("User-Agent", "DownloadWidgetAndroid-App")
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
                    Log.d(TAG, "Found ${list.size} assets for version $version")
                    return list
                }
            }
            throw IOException("Release version not found")
        }
    }
}
