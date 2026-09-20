package com.acornjuice.downloadwidget.data.remote

import java.util.concurrent.LinkedBlockingDeque

/**
 * Test double for [GitHubApi] that returns pre-programmed [ApiResponse] values in FIFO order
 * and records the arguments each caller supplied.
 *
 * Usage:
 * ```
 * val api = FakeGitHubApi()
 * api.enqueue(ApiResponse.Success(release, etag = "W/\"abc\""))
 * val result = repo.refresh(widgetId = 1)
 * assertThat(api.recorded.first().tag).isEqualTo("v1.0.0")
 * ```
 */
class FakeGitHubApi : GitHubApi {

    data class Call(
        val repoApiBaseUrl: String,
        val tag: String,
        val token: String?,
        val etag: String?,
    )

    private val responses: LinkedBlockingDeque<ApiResponse> = LinkedBlockingDeque()
    private val calls: MutableList<Call> = mutableListOf()

    val recorded: List<Call> get() = calls.toList()

    fun enqueue(response: ApiResponse) {
        responses.add(response)
    }

    override suspend fun fetchRelease(
        repoApiBaseUrl: String,
        tag: String,
        token: String?,
        etag: String?,
    ): ApiResponse {
        calls += Call(repoApiBaseUrl, tag, token, etag)
        return responses.pollFirst()
            ?: error("FakeGitHubApi ran out of enqueued responses (call #${calls.size})")
    }
}
