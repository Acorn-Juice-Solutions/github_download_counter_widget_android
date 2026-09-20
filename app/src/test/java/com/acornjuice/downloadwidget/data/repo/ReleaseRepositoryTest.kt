package com.acornjuice.downloadwidget.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acornjuice.downloadwidget.data.local.AssetCacheStore
import com.acornjuice.downloadwidget.data.local.ETagStore
import com.acornjuice.downloadwidget.data.local.SecureTokenStore
import com.acornjuice.downloadwidget.data.local.WidgetConfigStore
import com.acornjuice.downloadwidget.data.remote.ApiResponse
import com.acornjuice.downloadwidget.data.remote.FakeGitHubApi
import com.acornjuice.downloadwidget.domain.model.Asset
import com.acornjuice.downloadwidget.domain.model.Release
import com.acornjuice.downloadwidget.domain.model.RefreshResult
import com.acornjuice.downloadwidget.domain.time.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ReleaseRepositoryTest {

    private lateinit var api: FakeGitHubApi
    private lateinit var config: WidgetConfigStore
    private lateinit var tokens: SecureTokenStore
    private lateinit var cache: AssetCacheStore
    private lateinit var etags: ETagStore
    private var fixedNow = 1_726_000_000L
    private val time = TimeProvider { fixedNow }

    private lateinit var repo: ReleaseRepository

    private val configuredUrl = "https://api.github.com/repos/o/r/releases"
    private val configuredTag = "v1.0.0"
    private val configuredWidgetId = 42

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val plainPrefs = ctx.getSharedPreferences("test-repo-plain", Context.MODE_PRIVATE)
        val securePrefs = ctx.getSharedPreferences("test-repo-secure", Context.MODE_PRIVATE)
        plainPrefs.edit().clear().commit()
        securePrefs.edit().clear().commit()

        config = WidgetConfigStore(plainPrefs)
        tokens = SecureTokenStore(securePrefs)
        cache = AssetCacheStore(plainPrefs)
        etags = ETagStore(plainPrefs)
        api = FakeGitHubApi()

        config.setUrl(configuredWidgetId, configuredUrl)
        config.setTag(configuredWidgetId, configuredTag)

        repo = ReleaseRepository(api, config, tokens, cache, etags, time)
    }

    @Test
    fun `refresh returns InvalidConfig when config incomplete`() = runTest {
        val newWidgetId = 99 // no config set
        val result = repo.refresh(newWidgetId)

        assertThat(result).isEqualTo(RefreshResult.InvalidConfig)
        assertThat(api.recorded).isEmpty()
    }

    @Test
    fun `refresh Success caches release and stores etag`() = runTest {
        val release = Release("v1.0.0", listOf(Asset("a.apk", 10)), fetchedAtEpochSeconds = fixedNow)
        api.enqueue(ApiResponse.Success(release, etag = "W/\"abc\""))

        val result = repo.refresh(configuredWidgetId)

        assertThat(result).isInstanceOf(RefreshResult.Success::class.java)
        assertThat((result as RefreshResult.Success).release).isEqualTo(release)
        assertThat(cache.get(configuredWidgetId)?.tag).isEqualTo("v1.0.0")
        assertThat(cache.get(configuredWidgetId)?.totalDownloads).isEqualTo(10)
        assertThat(etags.get(configuredWidgetId, configuredUrl, configuredTag)).isEqualTo("W/\"abc\"")
    }

    @Test
    fun `refresh Success without etag header does not touch etag store`() = runTest {
        etags.put(configuredWidgetId, configuredUrl, configuredTag, "old-etag")
        val release = Release("v1.0.0", emptyList(), fetchedAtEpochSeconds = fixedNow)
        api.enqueue(ApiResponse.Success(release, etag = null))

        repo.refresh(configuredWidgetId)

        assertThat(etags.get(configuredWidgetId, configuredUrl, configuredTag)).isEqualTo("old-etag")
    }

    @Test
    fun `refresh sends stored etag and token to API`() = runTest {
        tokens.set("ghp_abc123")
        etags.put(configuredWidgetId, configuredUrl, configuredTag, "W/\"stored\"")
        api.enqueue(ApiResponse.NotModified)

        repo.refresh(configuredWidgetId)

        val call = api.recorded.single()
        assertThat(call.token).isEqualTo("ghp_abc123")
        assertThat(call.etag).isEqualTo("W/\"stored\"")
        assertThat(call.repoApiBaseUrl).isEqualTo(configuredUrl)
        assertThat(call.tag).isEqualTo(configuredTag)
    }

    @Test
    fun `refresh NotModified preserves cache and bumps timestamp`() = runTest {
        val cached = Release("v1.0.0", listOf(Asset("cached.apk", 5)), fetchedAtEpochSeconds = 1_000L)
        cache.put(configuredWidgetId, cached)
        fixedNow = 2_000L
        api.enqueue(ApiResponse.NotModified)

        val result = repo.refresh(configuredWidgetId)

        assertThat(result).isEqualTo(RefreshResult.NotModified)
        val reloaded = cache.get(configuredWidgetId)!!
        assertThat(reloaded.assets).containsExactlyElementsIn(cached.assets)
        assertThat(reloaded.fetchedAtEpochSeconds).isEqualTo(2_000L)
    }

    @Test
    fun `refresh RateLimit forwards reset and preserves cache`() = runTest {
        val cached = Release("v1.0.0", listOf(Asset("a", 1)), fetchedAtEpochSeconds = 1_000L)
        cache.put(configuredWidgetId, cached)
        api.enqueue(ApiResponse.RateLimit(resetEpochSeconds = 1_800_000_000L))

        val result = repo.refresh(configuredWidgetId)

        assertThat(result).isEqualTo(RefreshResult.RateLimited(1_800_000_000L))
        assertThat(cache.get(configuredWidgetId)?.assets).containsExactlyElementsIn(cached.assets)
    }

    @Test
    fun `refresh NotFound preserves cache and carries tag`() = runTest {
        val cached = Release("v1.0.0", listOf(Asset("a", 1)), fetchedAtEpochSeconds = 1_000L)
        cache.put(configuredWidgetId, cached)
        api.enqueue(ApiResponse.NotFound)

        val result = repo.refresh(configuredWidgetId)

        assertThat(result).isEqualTo(RefreshResult.NotFound(configuredTag))
        assertThat(cache.get(configuredWidgetId)).isNotNull()
    }

    @Test
    fun `refresh HttpError maps to NetworkError`() = runTest {
        api.enqueue(ApiResponse.HttpError(code = 500))

        val result = repo.refresh(configuredWidgetId)

        assertThat(result).isInstanceOf(RefreshResult.NetworkError::class.java)
        val error = result as RefreshResult.NetworkError
        assertThat(error.cause).isInstanceOf(IOException::class.java)
        assertThat(error.cause.message).contains("500")
    }

    @Test
    fun `refresh Malformed maps to NetworkError with cause`() = runTest {
        val cause = IllegalStateException("bad json")
        api.enqueue(ApiResponse.Malformed(cause))

        val result = repo.refresh(configuredWidgetId)

        assertThat((result as RefreshResult.NetworkError).cause).isSameInstanceAs(cause)
    }

    @Test
    fun `refresh Network maps to NetworkError with cause`() = runTest {
        val cause = IOException("timeout")
        api.enqueue(ApiResponse.Network(cause))

        val result = repo.refresh(configuredWidgetId)

        assertThat((result as RefreshResult.NetworkError).cause).isSameInstanceAs(cause)
    }

    @Test
    fun `refresh does not send etag from a different url or tag`() = runTest {
        etags.put(configuredWidgetId, url = "https://other", tag = "v0.9.0", etag = "stale")
        api.enqueue(ApiResponse.NotModified)

        repo.refresh(configuredWidgetId)

        assertThat(api.recorded.single().etag).isNull()
    }

    @Test
    fun `cachedRelease returns null when no cache present`() {
        assertThat(repo.cachedRelease(configuredWidgetId)).isNull()
    }

    @Test
    fun `cachedRelease returns stored release`() = runTest {
        val release = Release("v1.0.0", listOf(Asset("a", 7)), fetchedAtEpochSeconds = 1_000L)
        cache.put(configuredWidgetId, release)

        assertThat(repo.cachedRelease(configuredWidgetId)?.totalDownloads).isEqualTo(7)
    }

    @Test
    fun `clearWidget wipes config cache and etag`() = runTest {
        cache.put(configuredWidgetId, Release("v1.0.0", emptyList(), 0L))
        etags.put(configuredWidgetId, configuredUrl, configuredTag, "W/\"x\"")

        repo.clearWidget(configuredWidgetId)

        assertThat(cache.get(configuredWidgetId)).isNull()
        assertThat(etags.get(configuredWidgetId, configuredUrl, configuredTag)).isNull()
        assertThat(config.get(configuredWidgetId).isComplete).isFalse()
    }
}
