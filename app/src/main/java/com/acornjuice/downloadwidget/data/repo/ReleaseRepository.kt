package com.acornjuice.downloadwidget.data.repo

import com.acornjuice.downloadwidget.data.local.AssetCacheStore
import com.acornjuice.downloadwidget.data.local.ETagStore
import com.acornjuice.downloadwidget.data.local.SecureTokenStore
import com.acornjuice.downloadwidget.data.local.WidgetConfigStore
import com.acornjuice.downloadwidget.data.remote.ApiResponse
import com.acornjuice.downloadwidget.data.remote.GitHubApi
import com.acornjuice.downloadwidget.domain.model.Release
import com.acornjuice.downloadwidget.domain.model.RefreshResult
import com.acornjuice.downloadwidget.domain.time.TimeProvider
import java.io.IOException

/**
 * Single point of truth for widget data.
 *
 * Reads the widget's configuration, invokes the [GitHubApi] with conditional ETag support,
 * persists successful responses to the local cache, and translates transport-level
 * [ApiResponse] into the domain-level [RefreshResult] surface that the UI consumes.
 */
class ReleaseRepository(
    private val api: GitHubApi,
    private val config: WidgetConfigStore,
    private val tokens: SecureTokenStore,
    private val cache: AssetCacheStore,
    private val etags: ETagStore,
    private val time: TimeProvider,
) {

    /**
     * Attempts to refresh the given widget. Never throws — errors are returned as
     * [RefreshResult.NetworkError] variants. On success or `NotModified`, the local cache
     * (asset list + timestamp) is updated. On any other outcome the cache is left untouched.
     */
    suspend fun refresh(widgetId: Int): RefreshResult {
        val cfg = config.get(widgetId)
        if (!cfg.isComplete) return RefreshResult.InvalidConfig

        val currentEtag = etags.get(widgetId = widgetId, url = cfg.apiUrl, tag = cfg.tag)
        val token = tokens.get()

        return when (val response = api.fetchRelease(cfg.apiUrl, cfg.tag, token, currentEtag)) {
            is ApiResponse.Success -> onSuccess(widgetId, cfg.apiUrl, cfg.tag, response)
            ApiResponse.NotModified -> onNotModified(widgetId)
            is ApiResponse.RateLimit -> RefreshResult.RateLimited(response.resetEpochSeconds)
            ApiResponse.NotFound -> RefreshResult.NotFound(cfg.tag)
            is ApiResponse.HttpError -> RefreshResult.NetworkError(IOException("HTTP ${response.code}"))
            is ApiResponse.Malformed -> RefreshResult.NetworkError(response.cause)
            is ApiResponse.Network -> RefreshResult.NetworkError(response.cause)
        }
    }

    /**
     * Returns the last cached [Release] for this widget without touching the network.
     * Consumed by widget `onUpdate` to avoid blocking the main thread.
     */
    fun cachedRelease(widgetId: Int): Release? = cache.get(widgetId)

    /** Wipes all per-widget state — invoked by the widget provider on `onDeleted`. */
    fun clearWidget(widgetId: Int) {
        config.clear(widgetId)
        cache.clear(widgetId)
        etags.clear(widgetId)
    }

    private fun onSuccess(
        widgetId: Int,
        apiUrl: String,
        tag: String,
        response: ApiResponse.Success,
    ): RefreshResult {
        cache.put(widgetId, response.release)
        if (!response.etag.isNullOrBlank()) {
            etags.put(widgetId = widgetId, url = apiUrl, tag = tag, etag = response.etag)
        }
        return RefreshResult.Success(response.release)
    }

    private fun onNotModified(widgetId: Int): RefreshResult {
        cache.updateLastRefreshed(widgetId, time.nowEpochSeconds())
        return RefreshResult.NotModified
    }
}
