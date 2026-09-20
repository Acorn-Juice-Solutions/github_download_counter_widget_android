package com.acornjuice.downloadwidget.data.local

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persists per-widget user configuration (API URL + release tag) in plain [SharedPreferences].
 *
 * The [androidx.preference.PreferenceFragmentCompat] settings screen writes to the same
 * prefs file using the same key naming ([PrefKeys.scoped]) so this store is a stable
 * read-side view of what the user typed in the UI.
 *
 * Read fallback: if the widget-scoped key is missing/blank, we fall back to the unscoped
 * base key. This is the "safety net" for users who opened `MainActivity` from the app icon
 * (no `EXTRA_APPWIDGET_ID`) — that path saves under the bare keys and would otherwise be
 * invisible to every widget. Scoped values always win when both exist.
 *
 * All URL and tag values are trimmed on both write and read: a stray whitespace or newline
 * pasted from the clipboard would otherwise produce `.../tags/<tag>%20`, which GitHub rejects
 * with 404, or an unparseable URL — either way the widget silently reports "tag not found".
 */
class WidgetConfigStore(private val prefs: SharedPreferences) {

    fun get(widgetId: Int): WidgetConfig = WidgetConfig(
        apiUrl = readScopedOrFallback(PrefKeys.KEY_API_URL, widgetId),
        tag = readScopedOrFallback(PrefKeys.KEY_TAG, widgetId),
    )

    private fun readScopedOrFallback(baseKey: String, widgetId: Int): String {
        val scoped = (prefs.getString(PrefKeys.scoped(baseKey, widgetId), "") ?: "").trim()
        if (scoped.isNotBlank()) return scoped
        return (prefs.getString(baseKey, "") ?: "").trim()
    }

    fun setUrl(widgetId: Int, url: String) {
        prefs.edit { putString(PrefKeys.scoped(PrefKeys.KEY_API_URL, widgetId), url.trim()) }
    }

    fun setTag(widgetId: Int, tag: String) {
        prefs.edit { putString(PrefKeys.scoped(PrefKeys.KEY_TAG, widgetId), tag.trim()) }
    }

    fun clear(widgetId: Int) {
        prefs.edit {
            remove(PrefKeys.scoped(PrefKeys.KEY_API_URL, widgetId))
            remove(PrefKeys.scoped(PrefKeys.KEY_TAG, widgetId))
        }
    }
}
