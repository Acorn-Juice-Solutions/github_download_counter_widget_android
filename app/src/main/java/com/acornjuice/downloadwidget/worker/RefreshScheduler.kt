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

    private val networkConstraint: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun oneShotWorkName(providerClassName: String): String =
        "${WORK_NAME_ONESHOT_PREFIX}_${providerClassName.substringAfterLast('.')}"

    companion object {
        const val WORK_NAME_PERIODIC = "download_widget_periodic_refresh"
        const val WORK_NAME_ONESHOT_PREFIX = "download_widget_oneshot"
        const val KEY_PROVIDER_CLASS = "provider_class"

        private const val PERIODIC_INTERVAL_MINUTES = 60L
        private const val BACKOFF_INITIAL_SECONDS = 30L
    }
}
