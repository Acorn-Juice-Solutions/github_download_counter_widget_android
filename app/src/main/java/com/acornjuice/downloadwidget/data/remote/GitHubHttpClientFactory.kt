package com.acornjuice.downloadwidget.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds the [OkHttpClient] used in production. Timeouts are tuned for widget refresh:
 * a call must be fast or fail fast — nobody stares at a widget for 60 seconds.
 */
object GitHubHttpClientFactory {

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val CALL_TIMEOUT_SECONDS = 30L

    fun default(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
