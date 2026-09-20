package com.acornjuice.downloadwidget.data.remote

import com.acornjuice.downloadwidget.domain.model.Release

/**
 * Low-level outcome of a single call to [GitHubApi.fetchRelease].
 *
 * Distinct from `RefreshResult` (which is domain-level and consumed by the widget UI):
 * this hierarchy carries transport-level detail and is translated by
 * [com.acornjuice.downloadwidget.data.repo.ReleaseRepository] into a `RefreshResult`.
 */
sealed interface ApiResponse {

    /** HTTP 200. The release was fetched and parsed successfully. */
    data class Success(val release: Release, val etag: String?) : ApiResponse

    /** HTTP 304. Local cache is still authoritative. */
    data object NotModified : ApiResponse

    /**
     * HTTP 403 with `X-RateLimit-Remaining: 0`. GitHub asks us to wait until
     * [resetEpochSeconds] before the next call.
     */
    data class RateLimit(val resetEpochSeconds: Long) : ApiResponse

    /** HTTP 404 — configured tag does not exist. */
    data object NotFound : ApiResponse

    /** Any other 4xx/5xx not covered above. */
    data class HttpError(val code: Int) : ApiResponse

    /** 200 OK but body could not be parsed. */
    data class Malformed(val cause: Throwable) : ApiResponse

    /** Transport-level failure (timeout, DNS, TLS, ...). */
    data class Network(val cause: Throwable) : ApiResponse
}
