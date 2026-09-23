package com.acornjuice.downloadwidget.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RefreshSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: RefreshScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = RefreshScheduler(workManager)
    }

    @Test
    fun `ensurePeriodic enqueues unique periodic work`() {
        scheduler.ensurePeriodic()

        val infos = workManager.getWorkInfosForUniqueWork(RefreshScheduler.WORK_NAME_PERIODIC).get()
        assertThat(infos).hasSize(1)
        assertThat(infos.single().state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }

    @Test
    fun `ensurePeriodic is idempotent`() {
        scheduler.ensurePeriodic()
        val firstId = workManager.getWorkInfosForUniqueWork(RefreshScheduler.WORK_NAME_PERIODIC).get().single().id

        scheduler.ensurePeriodic()
        val secondId = workManager.getWorkInfosForUniqueWork(RefreshScheduler.WORK_NAME_PERIODIC).get().single().id

        assertThat(secondId).isEqualTo(firstId)
    }

    @Test
    fun `enqueueOneShot creates work with provider-scoped name`() {
        scheduler.enqueueOneShot("com.acornjuice.downloadwidget.ui.widget.DownloadWidgetProvider")

        val infos = workManager.getWorkInfosForUniqueWork(
            "${RefreshScheduler.WORK_NAME_ONESHOT_PREFIX}_DownloadWidgetProvider",
        ).get()
        assertThat(infos).hasSize(1)
    }

    @Test
    fun `enqueueOneShotAfter respects delay by using same unique name`() {
        scheduler.enqueueOneShotAfter("com.acornjuice.downloadwidget.ui.widget.DownloadWidgetProvider", delaySeconds = 60)

        val infos = workManager.getWorkInfosForUniqueWork(
            "${RefreshScheduler.WORK_NAME_ONESHOT_PREFIX}_DownloadWidgetProvider",
        ).get()
        assertThat(infos).hasSize(1)
    }

    @Test
    fun `enqueueOneShotAfter replaces prior one-shot for same provider`() {
        scheduler.enqueueOneShot("com.acornjuice.downloadwidget.ui.widget.DownloadWidgetProvider")
        scheduler.enqueueOneShotAfter("com.acornjuice.downloadwidget.ui.widget.DownloadWidgetProvider", delaySeconds = 30)

        val infos = workManager.getWorkInfosForUniqueWork(
            "${RefreshScheduler.WORK_NAME_ONESHOT_PREFIX}_DownloadWidgetProvider",
        ).get()
        assertThat(infos).hasSize(1)
    }

    @Test
    fun `enqueueWatchdog creates work under a widget-scoped name`() {
        scheduler.enqueueWatchdog(
            widgetId = 144,
            providerClassName = PROVIDER,
            refreshStartedAtEpochMillis = 1_726_000_000_123L,
            delaySeconds = 15,
        )

        val infos = workManager.getWorkInfosForUniqueWork(RefreshScheduler.watchdogWorkName(144)).get()
        assertThat(infos).hasSize(1)
    }

    @Test
    fun `watchdogs for different widgets do not replace each other`() {
        scheduler.enqueueWatchdog(widgetId = 1, providerClassName = PROVIDER, refreshStartedAtEpochMillis = 1L, delaySeconds = 15)
        scheduler.enqueueWatchdog(widgetId = 2, providerClassName = PROVIDER, refreshStartedAtEpochMillis = 2L, delaySeconds = 15)

        assertThat(workManager.getWorkInfosForUniqueWork(RefreshScheduler.watchdogWorkName(1)).get()).hasSize(1)
        assertThat(workManager.getWorkInfosForUniqueWork(RefreshScheduler.watchdogWorkName(2)).get()).hasSize(1)
    }

    @Test
    fun `watchdog carries no network constraint`() {
        // The stuck-spinner case it rescues is most likely precisely when the network is
        // down, so requiring connectivity here would disable the safety net when it matters.
        scheduler.enqueueWatchdog(widgetId = 144, providerClassName = PROVIDER, refreshStartedAtEpochMillis = 1L, delaySeconds = 15)

        val info = workManager.getWorkInfosForUniqueWork(RefreshScheduler.watchdogWorkName(144)).get().single()
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.NOT_REQUIRED)
    }

    @Test
    fun `cancelWatchdog stands the safety net down`() {
        scheduler.enqueueWatchdog(widgetId = 144, providerClassName = PROVIDER, refreshStartedAtEpochMillis = 1L, delaySeconds = 15)

        scheduler.cancelWatchdog(144)

        val info = workManager.getWorkInfosForUniqueWork(RefreshScheduler.watchdogWorkName(144)).get().single()
        assertThat(info.state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    private companion object {
        const val PROVIDER = "com.acornjuice.downloadwidget.ui.widget.DownloadWidgetProvider"
    }
}
