package com.acornjuice.downloadwidget.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import com.acornjuice.downloadwidget.domain.model.Asset
import com.acornjuice.downloadwidget.domain.model.Release
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Persists the last successful [Release] snapshot per widget, plus its "fetched at"
 * timestamp. Malformed cached JSON is treated as absent — never crashes callers.
 *
 * Storage layout (all keys widget-scoped via [PrefKeys.scoped]):
 * - `pref_asset_list_json_<id>` → serialized asset array
 * - `pref_asset_tag_<id>`       → tag the assets belong to
 * - `pref_last_update_epoch_<id>` → epoch seconds of last fresh (or NotModified) refresh
 */
class AssetCacheStore(private val prefs: SharedPreferences) {

    private companion object {
        const val KEY_ASSET_TAG = "pref_asset_tag"
        const val JSON_NAME = "name"
        const val JSON_DOWNLOAD_COUNT = "download_count"
        const val DEFAULT_ASSET_NAME = "asset"
    }

    fun get(widgetId: Int): Release? {
        val tag = prefs.getString(PrefKeys.scoped(KEY_ASSET_TAG, widgetId), null) ?: return null
        val json = prefs.getString(PrefKeys.scoped(PrefKeys.KEY_ASSET_LIST_JSON, widgetId), null) ?: return null
        val fetchedAt = prefs.getLong(PrefKeys.scoped(PrefKeys.KEY_LAST_UPDATE_EPOCH, widgetId), 0L)

        val assets = try {
            parseAssets(json)
        } catch (jsonEx: JSONException) {
            // Corrupted cache — treat as absent. A subsequent refresh will overwrite it.
            return null
        }
        return Release(tag = tag, assets = assets, fetchedAtEpochSeconds = fetchedAt)
    }

    fun put(widgetId: Int, release: Release) {
        val json = serializeAssets(release.assets)
        prefs.edit {
            putString(PrefKeys.scoped(PrefKeys.KEY_ASSET_LIST_JSON, widgetId), json)
            putString(PrefKeys.scoped(KEY_ASSET_TAG, widgetId), release.tag)
            putLong(PrefKeys.scoped(PrefKeys.KEY_LAST_UPDATE_EPOCH, widgetId), release.fetchedAtEpochSeconds)
        }
    }

    fun updateLastRefreshed(widgetId: Int, epochSeconds: Long) {
        prefs.edit { putLong(PrefKeys.scoped(PrefKeys.KEY_LAST_UPDATE_EPOCH, widgetId), epochSeconds) }
    }

    fun clear(widgetId: Int) {
        prefs.edit {
            remove(PrefKeys.scoped(PrefKeys.KEY_ASSET_LIST_JSON, widgetId))
            remove(PrefKeys.scoped(KEY_ASSET_TAG, widgetId))
            remove(PrefKeys.scoped(PrefKeys.KEY_LAST_UPDATE_EPOCH, widgetId))
        }
    }

    private fun serializeAssets(assets: List<Asset>): String {
        val array = JSONArray()
        for (asset in assets) {
            val obj = JSONObject()
            obj.put(JSON_NAME, asset.name)
            obj.put(JSON_DOWNLOAD_COUNT, asset.downloadCount)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseAssets(json: String): List<Asset> {
        val array = JSONArray(json)
        return buildList(capacity = array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    Asset(
                        name = obj.optString(JSON_NAME, DEFAULT_ASSET_NAME).ifBlank { DEFAULT_ASSET_NAME },
                        downloadCount = obj.optInt(JSON_DOWNLOAD_COUNT, 0).coerceAtLeast(0),
                    ),
                )
            }
        }
    }
}
