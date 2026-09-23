package com.acornjuice.downloadwidget.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Handles all WorkManager enqueues for [RefreshWorker].
 *
 * - [ensurePeriodic] is idempotent (uses `ExistingPeriodicWorkPolicy.KEEP`) and is called
 *   once from `Application.onCreate`.
 * - [enqueueOneShot] is fired from a widget's "refresh" tap; `REPLACE` policy means a fresh
 *   press cancels an in-flight retry.
 * - [enqueueOneShotAfter] schedules a delayed one-shot — used to honour GitHub's
 *   `X-RateLimit-Reset` header without spinning WorkManager on failed retries.
 * - [enqueueWatchdog] / [cancelWatchdog] arm and disarm the per-widget safety net that
 *   rescues a widget stuck on the `Loading` spinner. See [RefreshWatchdogWorker].
 */
class RefreshScheduler(private val workManager: WorkManager) {

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueueOneShot(providerClassName: String) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(workDataOf(KEY_PROVIDER_CLASS to providerClassName))
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(oneShotWorkName(providerClassName), ExistingWorkPolicy.REPLACE, request)
    }

    fun enqueueOneShotAfter(providerClassName: String, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(workDataOf(KEY_PROVIDER_CLASS to providerClassName))
            .setInitialDelay(delaySeconds.coerceAtLeast(0L), TimeUnit.SECONDS)
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniqueWork(oneShotWorkName(providerClassName), ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Arms the stuck-spinner safety net for [widgetId], to fire [delaySeconds] from now.
     *
     * [refreshStartedAtEpochMillis] is the fencing token: the watchdog compares it against
     * the live marker in
     * [com.acornjuice.downloadwidget.data.local.RefreshStateStore] and stands down unless
     * they match, so a watchdog that fires late cannot overwrite a newer refresh.
     *
     * Deliberately **unconstrained**: attaching [networkConstraint] here would be exactly
     * backwards, since the failure this rescues is most likely when the network is down.
     */
    fun enqueueWatchdog(
        widgetId: Int,
        providerClassName: String,
        refreshStartedAtEpochMillis: Long,
        delaySeconds: Long,
    ) {
        val request = OneTimeWorkRequestBuilder<RefreshWatchdogWorker>()
            .setInputData(
                workDataOf(
                    KEY_WIDGET_ID to widgetId,
                    KEY_PROVIDER_CLASS to providerClassName,
                    KEY_REFRESH_STARTED_AT to refreshStartedAtEpochMillis,
                ),
            )
            .setInitialDelay(delaySeconds.coerceAtLeast(0L), TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(watchdogWorkName(widgetId), ExistingWorkPolicy.REPLACE, request)
    }

    /** Stands the safety net down once a refresh has produced its terminal render. */
    fun cancelWatchdog(widgetId: Int) {
        workManager.cancelUniqueWork(watchdogWorkName(widgetId))
    }

    private val networkConstraint: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun oneShotWorkName(providerClassName: String): String =
        "${WORK_NAME_ONESHOT_PREFIX}_${providerClassName.substringAfterLast('.')}"

    companion object {
        const val WORK_NAME_PERIODIC = "download_widget_periodic_refresh"
        const val WORK_NAME_ONESHOT_PREFIX = "download_widget_oneshot"
        const val WORK_NAME_WATCHDOG_PREFIX = "download_widget_watchdog"
        const val KEY_PROVIDER_CLASS = "provider_class"
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_REFRESH_STARTED_AT = "refresh_started_at"

        /** Watchdogs are unique per widget, not per provider kind. */
        fun watchdogWorkName(widgetId: Int): String = "${WORK_NAME_WATCHDOG_PREFIX}_$widgetId"

        private const val PERIODIC_INTERVAL_MINUTES = 60L
        private const val BACKOFF_INITIAL_SECONDS = 30L
    }
}
