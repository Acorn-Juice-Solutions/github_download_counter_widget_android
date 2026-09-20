package com.acornjuice.downloadwidget.data.remote

import com.acornjuice.downloadwidget.domain.time.SystemTimeProvider
import com.acornjuice.downloadwidget.domain.time.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import java.io.IOException

/**
 * Default [GitHubApi] implementation backed by OkHttp.
 *
 * Uses the `GET /repos/{owner}/{repo}/releases/tags/{tag}` endpoint directly so that stale
 * releases (older than the default page size of `/releases`) are still resolvable.
 * All HTTP outcomes are mapped to typed [ApiResponse] variants — this method never throws.
 */
class OkHttpGitHubApi(
    private val client: OkHttpClient,
    private val time: TimeProvider = SystemTimeProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GitHubApi {

    override suspend fun fetchRelease(
        repoApiBaseUrl: String,
        tag: String,
        token: String?,
        etag: String?,
    ): ApiResponse = withContext(ioDispatcher) {
        val url = buildUrl(repoApiBaseUrl, tag) ?: return@withContext ApiResponse.HttpError(code = HTTP_BAD_REQUEST)

        val request = Request.Builder()
            .url(url)
            .get()
            .header(HEADER_ACCEPT, VALUE_ACCEPT)
            .header(HEADER_API_VERSION, VALUE_API_VERSION)
            .header(HEADER_USER_AGENT, VALUE_USER_AGENT)
            .apply {
                if (!etag.isNullOrBlank()) header(HEADER_IF_NONE_MATCH, etag)
                if (!token.isNullOrBlank()) header(HEADER_AUTHORIZATION, "Bearer $token")
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HTTP_OK -> handleOk(response)
                    HTTP_NOT_MODIFIED -> ApiResponse.NotModified
                    HTTP_NOT_FOUND -> ApiResponse.NotFound
                    HTTP_FORBIDDEN -> handleForbidden(response)
                    else -> ApiResponse.HttpError(response.code)
                }
            }
        } catch (io: IOException) {
            ApiResponse.Network(io)
        }
    }

    private fun handleOk(response: okhttp3.Response): ApiResponse {
        val body = response.body?.string()
            ?: return ApiResponse.Malformed(IOException("Empty response body"))
        return try {
            val release = ReleaseParser.parse(body, time.nowEpochSeconds())
            ApiResponse.Success(release = release, etag = response.header(HEADER_ETAG))
        } catch (jsonEx: JSONException) {
            ApiResponse.Malformed(jsonEx)
        }
    }

    private fun handleForbidden(response: okhttp3.Response): ApiResponse {
        val remaining = response.header(HEADER_RATE_LIMIT_REMAINING)?.toIntOrNull()
        return if (remaining == 0) {
            val reset = response.header(HEADER_RATE_LIMIT_RESET)?.toLongOrNull() ?: 0L
            ApiResponse.RateLimit(resetEpochSeconds = reset)
        } else {
            ApiResponse.HttpError(HTTP_FORBIDDEN)
        }
    }

    private fun buildUrl(repoApiBaseUrl: String, tag: String): okhttp3.HttpUrl? {
        val trimmedBase = repoApiBaseUrl.trimEnd('/')
        val base = trimmedBase.toHttpUrlOrNull() ?: return null
        return base.newBuilder()
            .addPathSegment("tags")
            .addPathSegment(tag)
            .build()
    }

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_NOT_MODIFIED = 304
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404

        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_API_VERSION = "X-GitHub-Api-Version"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_IF_NONE_MATCH = "If-None-Match"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_ETAG = "ETag"
        private const val HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining"
        private const val HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset"

        private const val VALUE_ACCEPT = "application/vnd.github+json"
        private const val VALUE_API_VERSION = "2022-11-28"
        private const val VALUE_USER_AGENT = "DownloadWidget-Android/1.0"
    }
}
