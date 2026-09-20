package com.acornjuice.downloadwidget.data.remote

import com.acornjuice.downloadwidget.domain.time.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class OkHttpGitHubApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OkHttpGitHubApi
    private val fixedTime = TimeProvider { 1_726_000_000L }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
        api = OkHttpGitHubApi(client = client, time = fixedTime)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/repos/owner/repo/releases").toString().trimEnd('/')

    @Test
    fun `200 with parseable body returns Success with etag`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "W/\"abc123\"")
                .setBody(
                    """
                    {"tag_name": "v1.0.0", "assets": [{"name": "a.apk", "download_count": 7}]}
                    """.trimIndent(),
                ),
        )

        val response = api.fetchRelease(baseUrl(), tag = "v1.0.0", token = null, etag = null)

        assertThat(response).isInstanceOf(ApiResponse.Success::class.java)
        val success = response as ApiResponse.Success
        assertThat(success.etag).isEqualTo("W/\"abc123\"")
        assertThat(success.release.tag).isEqualTo("v1.0.0")
        assertThat(success.release.fetchedAtEpochSeconds).isEqualTo(fixedTime.nowEpochSeconds())
        assertThat(success.release.totalDownloads).isEqualTo(7)
    }

    @Test
    fun `sends token and etag in headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        api.fetchRelease(baseUrl(), tag = "v1.0.0", token = "ghp_abcXYZ", etag = "W/\"prev\"")

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer ghp_abcXYZ")
        assertThat(recorded.getHeader("If-None-Match")).isEqualTo("W/\"prev\"")
        assertThat(recorded.getHeader("Accept")).isEqualTo("application/vnd.github+json")
        assertThat(recorded.getHeader("X-GitHub-Api-Version")).isEqualTo("2022-11-28")
        assertThat(recorded.path).isEqualTo("/repos/owner/repo/releases/tags/v1.0.0")
    }

    @Test
    fun `omits Authorization when token is null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        api.fetchRelease(baseUrl(), tag = "v1.0.0", token = null, etag = null)

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isNull()
        assertThat(recorded.getHeader("If-None-Match")).isNull()
    }

    @Test
    fun `omits Authorization when token is blank`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        api.fetchRelease(baseUrl(), tag = "v1.0.0", token = "   ", etag = "")

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isNull()
        assertThat(recorded.getHeader("If-None-Match")).isNull()
    }

    @Test
    fun `304 Not Modified returns NotModified`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        val response = api.fetchRelease(baseUrl(), tag = "v1.0.0", token = null, etag = "W/\"prev\"")

        assertThat(response).isEqualTo(ApiResponse.NotModified)
    }

    @Test
    fun `403 with X-RateLimit-Remaining zero returns RateLimit`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1726000600"),
        )

        val response = api.fetchRelease(baseUrl(), tag = "v1.0.0", token = null, etag = null)

        assertThat(response).isEqualTo(ApiResponse.RateLimit(resetEpochSeconds = 1_726_000_600L))
    }

    @Test
    fun `403 with remaining greater than zero returns HttpError`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "5"),
        )

        val response = api.fetchRelease(baseUrl(), tag = "v1.0.0", token = null, etag = null)

        assertThat(response).isEqualTo(ApiResponse.HttpError(code = 403))
    }

    @Test
    fun `404 returns NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val response = api.fetchRelease(baseUrl(), tag = "v9.9.9", token = null, etag = null)

        assertThat(response).isEqualTo(ApiResponse.NotFound)
    }

    @Test
    fun `500 returns HttpError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val response = api.fetchRelease(baseUrl(), tag = "v1.0.0", token = null, etag = null)

        assertThat(response).isEqualTo(ApiResponse.HttpError(code = 500))
    }

    @Test
    fun `malformed body returns Malformed`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("{not-json"),
        )

        val response = api.fetchRelease(baseUrl(), tag = "v1.0.0", token = null, etag = null)

        assertThat(response).isInstanceOf(ApiResponse.Malformed::class.java)
    }

    @Test
    fun `connection failure returns Network`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val response = api.fetchRelease(baseUrl(), tag = "v1.0.0", token = null, etag = null)

        assertThat(response).isInstanceOf(ApiResponse.Network::class.java)
    }

    @Test
    fun `builds URL by appending tags path segments`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        api.fetchRelease(
            repoApiBaseUrl = server.url("/repos/owner/name/releases").toString(),
            tag = "v2.0.0-beta.1",
            token = null,
            etag = null,
        )

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/repos/owner/name/releases/tags/v2.0.0-beta.1")
    }
}
