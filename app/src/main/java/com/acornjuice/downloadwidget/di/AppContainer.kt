package com.acornjuice.downloadwidget.di

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.acornjuice.downloadwidget.data.local.AssetCacheStore
import com.acornjuice.downloadwidget.data.local.ETagStore
import com.acornjuice.downloadwidget.data.local.PrefKeys
import com.acornjuice.downloadwidget.data.local.RefreshStateStore
import com.acornjuice.downloadwidget.data.local.SecureTokenStore
import com.acornjuice.downloadwidget.data.local.SecureTokenStoreFactory
import com.acornjuice.downloadwidget.data.local.WidgetConfigStore
import com.acornjuice.downloadwidget.data.remote.GitHubApi
import com.acornjuice.downloadwidget.data.remote.GitHubHttpClientFactory
import com.acornjuice.downloadwidget.data.remote.OkHttpGitHubApi
import com.acornjuice.downloadwidget.data.repo.ReleaseRepository
import com.acornjuice.downloadwidget.domain.time.SystemTimeProvider
import com.acornjuice.downloadwidget.domain.time.TimeProvider
import com.acornjuice.downloadwidget.worker.RefreshScheduler
import okhttp3.OkHttpClient

/**
 * Manual DI graph. One instance per process, owned by [com.acornjuice.downloadwidget.DownloadWidgetApp].
 *
 * Everything is `lazy` so construction cost is paid on first access, not at app startup.
 * The container is intentionally tiny — if it grows past a screenful, that's a signal to
 * split it (e.g. `NetworkModule`, `StorageModule`) rather than to reach for Hilt.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val timeProvider: TimeProvider = SystemTimeProvider

    val plainPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PrefKeys.PLAIN_PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    val httpClient: OkHttpClient by lazy { GitHubHttpClientFactory.default() }

    val gitHubApi: GitHubApi by lazy { OkHttpGitHubApi(client = httpClient, time = timeProvider) }

    val widgetConfigStore: WidgetConfigStore by lazy { WidgetConfigStore(plainPrefs) }

    val assetCacheStore: AssetCacheStore by lazy { AssetCacheStore(plainPrefs) }

    val etagStore: ETagStore by lazy { ETagStore(plainPrefs) }

    val refreshStateStore: RefreshStateStore by lazy { RefreshStateStore(plainPrefs) }

    val secureTokenStore: SecureTokenStore by lazy { SecureTokenStoreFactory.create(appContext) }

    val releaseRepository: ReleaseRepository by lazy {
        ReleaseRepository(
            api = gitHubApi,
            config = widgetConfigStore,
            tokens = secureTokenStore,
            cache = assetCacheStore,
            etags = etagStore,
            time = timeProvider,
        )
    }

    val refreshScheduler: RefreshScheduler by lazy {
        RefreshScheduler(WorkManager.getInstance(appContext))
    }
}
