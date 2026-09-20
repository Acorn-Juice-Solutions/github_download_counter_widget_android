package com.acornjuice.downloadwidget.ui.widget

import com.acornjuice.downloadwidget.domain.model.Release
import com.acornjuice.downloadwidget.domain.model.RefreshResult

/**
 * What the widget UI should currently show.
 *
 * Consumed by [WidgetRenderer]. Kept as a sealed hierarchy so the renderer's `when` is
 * exhaustive — a new state cannot be silently forgotten.
 */
sealed interface WidgetState {

    /** Fresh cached data available. */
    data class Success(val release: Release) : WidgetState

    /** Widget has cached data but is currently refreshing (spinner shown). */
    data class Loading(val cached: Release?) : WidgetState

    /**
     * Terminal error state after a failed refresh. `cached` is the last known good release
     * (or `null` if we have never fetched); the renderer will still show the cached count
     * so the widget isn't blank.
     */
    data class Error(val kind: ErrorKind, val cached: Release?) : WidgetState

    /** The widget has never been configured (blank URL/tag). Guide the user to Settings. */
    data object UnconfiguredEmpty : WidgetState

    enum class ErrorKind {
        NETWORK,
        RATE_LIMIT,
        NOT_FOUND,
        INVALID_CONFIG,
    }
}

/**
 * Domain-level [RefreshResult] → UI-level [WidgetState] mapping.
 *
 * Shared between [com.acornjuice.downloadwidget.worker.RefreshWorker] (periodic path) and
 * the inline `goAsync` refresh in `BaseDownloadWidgetProvider` (user-tap path).
 */
fun RefreshResult.toWidgetState(cached: Release?): WidgetState = when (this) {
    is RefreshResult.Success -> WidgetState.Success(release)
    RefreshResult.NotModified -> cached?.let(WidgetState::Success)
        ?: WidgetState.Error(WidgetState.ErrorKind.NETWORK, cached = null)
    is RefreshResult.RateLimited -> WidgetState.Error(WidgetState.ErrorKind.RATE_LIMIT, cached)
    is RefreshResult.NotFound -> WidgetState.Error(WidgetState.ErrorKind.NOT_FOUND, cached)
    is RefreshResult.NetworkError -> WidgetState.Error(WidgetState.ErrorKind.NETWORK, cached)
    RefreshResult.InvalidConfig -> if (cached == null) {
        WidgetState.UnconfiguredEmpty
    } else {
        WidgetState.Error(WidgetState.ErrorKind.INVALID_CONFIG, cached)
    }
}
