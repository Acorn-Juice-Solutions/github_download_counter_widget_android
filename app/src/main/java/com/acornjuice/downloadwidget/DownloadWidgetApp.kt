package com.acornjuice.downloadwidget

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.acornjuice.downloadwidget.data.local.LegacyTokenCleanup
import com.acornjuice.downloadwidget.di.AppContainer
import com.google.android.material.color.DynamicColors

/**
 * Root [Application]. Owns the singleton [AppContainer] and performs one-off setup:
 *
 * 1. Applies Material 3 dynamic colors to future activities (Android 12+ only, silent no-op on older).
 * 2. Purges legacy plaintext token keys left behind by pre-1.0.0 installs.
 * 3. Ensures the periodic refresh work is enqueued (idempotent).
 *
 * Also implements [Configuration.Provider] so WorkManager initializes on demand without
 * relying on `androidx.startup`. This means unit tests that instantiate the Application
 * (Robolectric) do not need to install a `WorkManagerTestInitHelper` unless they
 * specifically inspect scheduled work.
 */
class DownloadWidgetApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        DynamicColors.applyToActivitiesIfAvailable(this)
        LegacyTokenCleanup.purge(container.plainPrefs)
        container.refreshScheduler.ensurePeriodic()
    }
}

/**
 * Convenience accessor: any [Context] can reach the [AppContainer] without importing the
 * concrete [DownloadWidgetApp] subtype.
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as DownloadWidgetApp).container
