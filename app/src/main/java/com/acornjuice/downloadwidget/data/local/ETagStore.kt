package com.acornjuice.downloadwidget.data.local

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persists per-widget ETag values keyed by `(apiUrl, tag)` so a stale ETag is never sent
 * against a different URL/tag pair (which would silently return 304 for the wrong resource).
 */
class ETagStore(private val prefs: SharedPreferences) {

    /**
     * @return The ETag from the last successful fetch of `(widgetId, url, tag)`, or `null` if
     *  the stored triple does not match the arguments (i.e. user changed url/tag).
     */
    fun get(widgetId: Int, url: String, tag: String): String? {
        val storedUrl = prefs.getString(PrefKeys.scoped(PrefKeys.KEY_ETAG_URL, widgetId), null)
        val storedTag = prefs.getString(PrefKeys.scoped(PrefKeys.KEY_ETAG_TAG, widgetId), null)
        if (storedUrl != url || storedTag != tag) return null
        return prefs.getString(PrefKeys.scoped(PrefKeys.KEY_ETAG, widgetId), null)
    }

    fun put(widgetId: Int, url: String, tag: String, etag: String) {
        prefs.edit {
            putString(PrefKeys.scoped(PrefKeys.KEY_ETAG, widgetId), etag)
            putString(PrefKeys.scoped(PrefKeys.KEY_ETAG_URL, widgetId), url)
            putString(PrefKeys.scoped(PrefKeys.KEY_ETAG_TAG, widgetId), tag)
        }
    }

    fun clear(widgetId: Int) {
        prefs.edit {
            remove(PrefKeys.scoped(PrefKeys.KEY_ETAG, widgetId))
            remove(PrefKeys.scoped(PrefKeys.KEY_ETAG_URL, widgetId))
            remove(PrefKeys.scoped(PrefKeys.KEY_ETAG_TAG, widgetId))
        }
    }
}
