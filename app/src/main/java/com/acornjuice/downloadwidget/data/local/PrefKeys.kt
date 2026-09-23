package com.acornjuice.downloadwidget.data.local

/**
 * Central registry of `SharedPreferences` filenames and key prefixes.
 *
 * All prefs access in this app goes through the stores defined next to this file — never
 * inline strings in call sites, so a typo or drift is a compile-time (rename) failure and
 * a single grep locates every producer/consumer.
 */
object PrefKeys {

    /** Plain-text prefs (URL, tag, cached JSON, ETag, last-update timestamps). */
    const val PLAIN_PREFS_FILENAME = "download_widget_prefs"

    /** [androidx.security.crypto.EncryptedSharedPreferences] file backing [SecureTokenStore]. */
    const val SECURE_PREFS_FILENAME = "download_widget_secure_prefs"

    /* Plain-store keys (widget-scoped via [scoped]). */
    const val KEY_API_URL = "pref_api_url"
    const val KEY_TAG = "pref_version"
    const val KEY_ASSET_LIST_JSON = "pref_asset_list_json"
    const val KEY_LAST_UPDATE_EPOCH = "pref_last_update_epoch"
    const val KEY_ETAG = "pref_etag"
    const val KEY_ETAG_URL = "pref_etag_url"
    const val KEY_ETAG_TAG = "pref_etag_tag"

    /** Epoch-millis stamp of an in-flight tap refresh; backs [RefreshStateStore]. */
    const val KEY_REFRESH_STARTED_AT = "pref_refresh_started_at"

    /* Secure-store keys (single, not widget-scoped — see design §6.1). */
    const val KEY_TOKEN = "pref_github_token"

    /**
     * Suffixes a base key with the widget id so per-widget entries do not collide.
     *
     * Example: `scoped("pref_api_url", 42) == "pref_api_url_42"`.
     */
    fun scoped(baseKey: String, widgetId: Int): String = "${baseKey}_$widgetId"
}
