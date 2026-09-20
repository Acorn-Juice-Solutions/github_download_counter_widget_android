package com.acornjuice.downloadwidget.data.remote

/**
 * Fetches a single GitHub release, hitting `GET /repos/{owner}/{repo}/releases/tags/{tag}`.
 *
 * Implementations must be thread-safe. All I/O happens inside `suspend fun`s and callers
 * are responsible for supplying a background dispatcher.
 */
interface GitHubApi {

    /**
     * @param repoApiBaseUrl The `.../repos/{owner}/{repo}/releases` prefix.
     *  The impl appends `/tags/{tag}` — do NOT pre-append.
     * @param tag Release tag to look up (e.g. `v1.2.3`).
     * @param token Optional GitHub personal access token; sent as `Authorization: Bearer`.
     * @param etag Optional value returned by a previous [ApiResponse.Success]; sent as `If-None-Match`.
     * @return An [ApiResponse] describing the transport-level outcome. This method does not throw
     *  on HTTP errors — they are converted to typed variants.
     */
    suspend fun fetchRelease(
        repoApiBaseUrl: String,
        tag: String,
        token: String?,
        etag: String?,
    ): ApiResponse
}
