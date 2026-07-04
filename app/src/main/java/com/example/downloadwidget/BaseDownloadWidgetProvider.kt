package com.example.downloadwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class BaseDownloadWidgetProvider : AppWidgetProvider() {
    
    abstract val layoutId: Int
    abstract val providerClass: Class<*>

    companion object {
        protected const val TAG = "DownloadWidget"
        const val ACTION_REFRESH = "com.example.downloadwidget.ACTION_REFRESH"
        protected const val PREFS_NAME = "download_widget_prefs"
        protected const val PREF_API_URL = "pref_api_url"
        protected const val PREF_VERSION = "pref_version"
        protected const val PREF_GITHUB_TOKEN = "pref_github_token"
        protected const val PREF_ASSET_LIST_JSON = "pref_asset_list_json"
        protected const val PREF_LAST_UPDATE = "pref_last_update"

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        RefreshWorker.enqueueRefresh(context, providerClass)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_REFRESH == intent.action) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, providerClass)
            val widgetIds = manager.getAppWidgetIds(component)

            for (widgetId in widgetIds) {
                showLoadingState(context, manager, widgetId)
            }

            RefreshWorker.enqueueRefresh(context, providerClass)
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun showLoadingState(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val assets = loadSavedAssetList(context, widgetId)
        val currentTotal = assets.sumOf { it.downloadCount }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val version = prefs.getString(prefKey(PREF_VERSION, widgetId), null)
            ?: prefs.getString(PREF_VERSION, "v1.7.5")
            ?: ""

        val views = RemoteViews(context.packageName, layoutId)
        
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
        views.setTextViewText(R.id.widget_release_label, context.getString(R.string.widget_version_label, version))
        views.setTextViewText(R.id.widget_count, currentTotal.toString())
        
        views.setViewVisibility(R.id.widget_refresh_container, View.GONE)
        views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
        views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_in_progress))
        views.setInt(R.id.widget_status, "setBackgroundResource", R.drawable.status_in_progress_background)
        
        setupButtons(context, widgetId, views)
        manager.updateAppWidget(widgetId, views)
    }

    private fun setupButtons(context: Context, widgetId: Int, views: RemoteViews) {
        val settingsIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val settingsPending = PendingIntent.getActivity(
            context,
            widgetId,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_settings, settingsPending)

        val refreshIntent = Intent(context, providerClass).apply {
            action = ACTION_REFRESH
        }
        val refreshPending = PendingIntent.getBroadcast(
            context,
            widgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_container, refreshPending)
    }

    fun updateWidgetSyncInternal(context: Context, apm: AppWidgetManager, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiUrl = prefs.getString(prefKey(PREF_API_URL, appWidgetId), null)
            ?: prefs.getString(PREF_API_URL, "https://api.github.com/repos/Acorn-Juice-Solutions/accuvideo-releases/releases")
            ?: ""
        val version = prefs.getString(prefKey(PREF_VERSION, appWidgetId), null)
            ?: prefs.getString(PREF_VERSION, "v1.7.5")
            ?: ""
        val token = prefs.getString(prefKey(PREF_GITHUB_TOKEN, appWidgetId), null)
            ?: prefs.getString(PREF_GITHUB_TOKEN, "") ?: ""

        var success = false
        var assets = loadSavedAssetList(context, appWidgetId)

        try {
            Log.d(TAG, "Fetching assets for $appWidgetId from: $apiUrl")
            val newAssets = fetchAssetList(apiUrl, version, token)
            // Only overwrite if we got some results to avoid 0s on partial response (though JSON should be complete)
            assets = newAssets
            success = true
        } catch (exception: Exception) {
            Log.e(TAG, "Error fetching assets for $appWidgetId: ${exception.message}")
            success = false
        }

        try {
            if (success) {
                saveAssetList(context, appWidgetId, assets)
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                prefs.edit().putString(prefKey(PREF_LAST_UPDATE, appWidgetId), time).apply()
            }
            
            val totalDownloads = assets.sumOf { it.downloadCount }
            val views = createRemoteViews(context, appWidgetId, version, totalDownloads, success)
            
            apm.updateAppWidget(appWidgetId, views)
            apm.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_asset_list)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error updating UI for $appWidgetId", e)
            val errorViews = RemoteViews(context.packageName, layoutId)
            errorViews.setViewVisibility(R.id.widget_refresh_container, View.VISIBLE)
            errorViews.setViewVisibility(R.id.widget_progress, View.GONE)
            errorViews.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_not_updated))
            errorViews.setInt(R.id.widget_status, "setBackgroundResource", R.drawable.status_not_updated_background)
            apm.partiallyUpdateAppWidget(appWidgetId, errorViews)
        }
    }

    private fun saveAssetList(context: Context, appWidgetId: Int, assets: List<AssetInfo>) {
        val array = JSONArray()
        for (asset in assets) {
            val item = JSONObject()
            item.put("name", asset.name)
            item.put("download_count", asset.downloadCount)
            array.put(item)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(prefKey(PREF_ASSET_LIST_JSON, appWidgetId), array.toString())
            .apply()
    }

    private fun loadSavedAssetList(context: Context, appWidgetId: Int): List<AssetInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(prefKey(PREF_ASSET_LIST_JSON, appWidgetId), null)
            ?: prefs.getString(PREF_ASSET_LIST_JSON, "[]")
            ?: "[]"
            
        val array = JSONArray(json)
        val list = mutableListOf<AssetInfo>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            list += AssetInfo(
                item.optString("name", "asset"),
                item.optInt("download_count", 0)
            )
        }
        return list
    }

    private fun createRemoteViews(context: Context, appWidgetId: Int, version: String, totalCount: Int, updateSuccess: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getString(prefKey(PREF_LAST_UPDATE, appWidgetId), "--:--")

        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
        views.setTextViewText(R.id.widget_release_label, "${context.getString(R.string.widget_version_label, version)} • $lastUpdate")
        views.setTextViewText(R.id.widget_count, totalCount.toString())
        
        if (updateSuccess) {
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_ok))
            views.setInt(R.id.widget_status, "setBackgroundResource", R.drawable.status_background)
        } else {
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_not_updated))
            views.setInt(R.id.widget_status, "setBackgroundResource", R.drawable.status_not_updated_background)
        }

        val listIntent = Intent(context, AssetListRemoteViewsService::class.java)
        listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        listIntent.data = Uri.parse(listIntent.toUri(Intent.URI_INTENT_SCHEME))
        views.setRemoteAdapter(R.id.widget_asset_list, listIntent)
        views.setEmptyView(R.id.widget_asset_list, R.id.widget_empty)

        views.setViewVisibility(R.id.widget_refresh_container, View.VISIBLE)
        views.setViewVisibility(R.id.widget_progress, View.GONE)

        val iconColor = context.getColor(R.color.secondary_text)
        views.setInt(R.id.widget_refresh, "setColorFilter", iconColor)
        views.setInt(R.id.widget_settings, "setColorFilter", iconColor)

        setupButtons(context, appWidgetId, views)

        return views
    }

    private fun fetchAssetList(apiUrl: String, version: String, token: String): List<AssetInfo> {
        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "GitTrack-Android-App")
            
        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Network error: ${response.code}")
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (widgetId in appWidgetIds) {
            editor.remove(prefKey(PREF_API_URL, widgetId))
            editor.remove(prefKey(PREF_VERSION, widgetId))
            editor.remove(prefKey(PREF_GITHUB_TOKEN, widgetId))
            editor.remove(prefKey(PREF_ASSET_LIST_JSON, widgetId))
            editor.remove(prefKey(PREF_LAST_UPDATE, widgetId))
        }
        editor.apply()
    }

    private fun prefKey(baseKey: String, appWidgetId: Int): String = "${baseKey}_$appWidgetId"
}
