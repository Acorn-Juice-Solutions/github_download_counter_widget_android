package com.acornjuice.downloadwidget.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.acornjuice.downloadwidget.R
import com.acornjuice.downloadwidget.appContainer
import com.acornjuice.downloadwidget.data.repo.ReleaseRepository
import com.acornjuice.downloadwidget.domain.model.RefreshResult
import com.acornjuice.downloadwidget.domain.time.TimeProvider
import com.acornjuice.downloadwidget.ui.widget.WidgetKind
import com.acornjuice.downloadwidget.ui.widget.WidgetRenderer
import com.acornjuice.downloadwidget.ui.widget.toWidgetState
import com.acornjuice.downloadwidget.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes a refresh cycle across every installed widget instance.
 *
 * Two entry points:
 * - Periodic (no input data)   → iterates every [WidgetKind].
 * - One-shot (input data has `provider_class`) → iterates just that kind.
 *
 * Contract with WorkManager (kept in sync with the design spec):
 * - Every widget touched by this run ends in a terminal [WidgetState]: Success, Error, or
 *   UnconfiguredEmpty. Never Loading. The [android.widget.RemoteViews] is pushed *before*
 *   this method returns.
 * - `Result.retry()` iff at least one widget hit a transient network error.
 * - `Result.success()` otherwise. Rate-limit responses on one-shots are re-scheduled for
 *   `resetEpochSeconds` via [RefreshScheduler.enqueueOneShotAfter]; periodic rate limits
 *   just wait for the next natural tick.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val repository = container.releaseRepository
        val scheduler = container.refreshScheduler
        val time = container.timeProvider
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        val targetKinds = resolveTargetKinds()
        SafeLogger.i(TAG, "Worker start: kinds=${targetKinds.map { it.name }}")
        val outcome = Outcome()

        for (kind in targetKinds) {
            processKind(kind, appWidgetManager, repository, outcome)
        }

        val result = terminalResult(outcome, scheduler, time)
        SafeLogger.i(TAG, "Worker done: result=$result netError=${outcome.hadNetworkError} rateLimited=${outcome.rateLimited != null}")
        return result
    }

    private fun resolveTargetKinds(): List<WidgetKind> {
        val providerClassName = inputData.getString(RefreshScheduler.KEY_PROVIDER_CLASS)
        return if (providerClassName == null) {
            WidgetKind.entries.toList()
        } else {
            val single = WidgetKind.fromProviderClassName(providerClassName)
            if (single == null) {
                SafeLogger.w(TAG, "Unknown provider class in input data: $providerClassName")
                emptyList()
            } else {
                listOf(single)
            }
        }
    }

    private suspend fun processKind(
        kind: WidgetKind,
        appWidgetManager: AppWidgetManager,
        repository: ReleaseRepository,
        outcome: Outcome,
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, kind.providerClass))
        if (ids.isEmpty()) return

        for (widgetId in ids) {
            val result = repository.refresh(widgetId)
            SafeLogger.i(TAG, "Refreshed widget=$widgetId kind=${kind.name} result=${result::class.simpleName}")
            outcome.record(result, providerClass = kind.providerClass)
            val cached = repository.cachedRelease(widgetId)
            val state = result.toWidgetState(cached)
            SafeLogger.i(TAG, "Rendering widget=$widgetId cached=${cached != null} state=${state::class.simpleName}")
            val views = WidgetRenderer.render(
                context = applicationContext,
                layoutId = kind.layoutRes,
                widgetId = widgetId,
                state = state,
                providerClass = kind.providerClass,
            )
            // Push RemoteViews from the main thread. Some AppWidgetHost implementations
            // silently drop updates originating from background threads when the previous
            // update (from an onReceive on the main thread) is still pending, leaving the
            // widget stuck in the earlier state (e.g. Loading).
            withContext(Dispatchers.Main) {
                appWidgetManager.updateAppWidget(widgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_asset_list)
            }
            SafeLogger.i(TAG, "updateAppWidget sent widget=$widgetId")
        }
    }

    private fun terminalResult(
        outcome: Outcome,
        scheduler: RefreshScheduler,
        time: TimeProvider,
    ): Result {
        val rateLimitTarget = outcome.rateLimited
        if (rateLimitTarget != null && isOneShot()) {
            val delaySeconds = (rateLimitTarget.resetEpochSeconds - time.nowEpochSeconds())
                .coerceAtLeast(MIN_RATE_LIMIT_RETRY_SECONDS)
            scheduler.enqueueOneShotAfter(rateLimitTarget.providerClass.name, delaySeconds)
        }
        return if (outcome.hadNetworkError) Result.retry() else Result.success()
    }

    private fun isOneShot(): Boolean =
        inputData.getString(RefreshScheduler.KEY_PROVIDER_CLASS) != null

    private class Outcome {
        var hadNetworkError: Boolean = false
            private set
        var rateLimited: RateLimitTarget? = null
            private set

        fun record(result: RefreshResult, providerClass: Class<*>) {
            when (result) {
                is RefreshResult.NetworkError -> hadNetworkError = true
                is RefreshResult.RateLimited -> if (
                    rateLimited == null || result.resetEpochSeconds < rateLimited!!.resetEpochSeconds
                ) {
                    rateLimited = RateLimitTarget(result.resetEpochSeconds, providerClass)
                }
                else -> Unit
            }
        }
    }

    private data class RateLimitTarget(
        val resetEpochSeconds: Long,
        val providerClass: Class<*>,
    )

    private companion object {
        const val TAG = "RefreshWorker"
        const val MIN_RATE_LIMIT_RETRY_SECONDS = 60L
    }
}
