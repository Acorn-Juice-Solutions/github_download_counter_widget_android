package com.acornjuice.downloadwidget.domain.model

/**
 * Outcome of a widget refresh attempt.
 *
 * A single sealed hierarchy so callers use `when` and get exhaustiveness checks.
 */
sealed interface RefreshResult {

    /** Fresh data was fetched from GitHub. */
    data class Success(val release: Release) : RefreshResult

    /**
     * Server returned 304 Not Modified — cached data is still valid.
     * Callers should refresh the "last checked" timestamp but not the cached payload.
     */
    data object NotModified : RefreshResult

    /**
     * GitHub rate limit exhausted. Retry no sooner than [resetEpochSeconds].
     */
    data class RateLimited(val resetEpochSeconds: Long) : RefreshResult

    /** The configured tag does not exist in the target repository. */
    data class NotFound(val tag: String) : RefreshResult

    /** Network / IO / server error. Cached data (if any) remains untouched. */
    data class NetworkError(val cause: Throwable) : RefreshResult

    /** The widget's stored configuration is missing or blank; user must open Settings. */
    data object InvalidConfig : RefreshResult
}
