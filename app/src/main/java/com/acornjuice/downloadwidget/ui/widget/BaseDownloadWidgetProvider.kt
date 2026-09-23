package com.acornjuice.downloadwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.acornjuice.downloadwidget.appContainer
import com.acornjuice.downloadwidget.di.AppContainer
import com.acornjuice.downloadwidget.util.SafeLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Thin `AppWidgetProvider` base. Everything expensive is delegated:
 *
 * - Data access → [com.acornjuice.downloadwidget.data.repo.ReleaseRepository].
 * - Rendering  → [WidgetRenderer], published via [WidgetPublisher].
 * - Background refresh → [com.acornjuice.downloadwidget.worker.RefreshScheduler] via
 *   [com.acornjuice.downloadwidget.worker.RefreshWorker].
 *
 * ### The invariant
 *
 * **A `Loading` render is never issued without a scheduled way out of it.**
 *
 * `Loading` is the only non-terminal state, and it is cleared by a *later* render. Every
 * previous incarnation of the tap refresh made that later render depend on this process
 * staying alive, so any stall or kill left the spinner turning and the badge stuck on a
 * yellow "UPDATING" — the widget's longest-running bug. The fix is structural rather than
 * another retry: before the spinner is drawn, a stamp is persisted
 * ([com.acornjuice.downloadwidget.data.local.RefreshStateStore]) and
 * [com.acornjuice.downloadwidget.worker.RefreshWatchdogWorker] is armed in WorkManager,
 * which outlives this process. Whatever happens next, something clears the spinner.
 *
 * ### Tap-refresh flow
 *
 * 1. Persist the in-flight stamp and arm the watchdog for [WATCHDOG_DELAY].
 * 2. Render `Loading` synchronously — spinner appears, badge turns yellow.
 * 3. `goAsync()` keeps the process alive while a coroutine fetches off the main thread,
 *    bounded by [REFRESH_TIMEOUT]. That bound is real only because
 *    [com.acornjuice.downloadwidget.data.remote.OkHttpGitHubApi] aborts the underlying
 *    `Call` on cancellation; a blocking `execute()` would otherwise run on to OkHttp's own
 *    30 s call timeout no matter what `withTimeout` said.
 * 4. Hold the spinner until [MIN_SPINNER_DWELL] has elapsed, then publish the terminal state
 *    derived from the **actual** [com.acornjuice.downloadwidget.domain.model.RefreshResult]
 *    on the main thread, and only then stand the watchdog down.
 *
 * Subclasses only supply their [WidgetKind]; both layout resolution and provider
 * `ComponentName` are derived from it.
 */
abstract class BaseDownloadWidgetProvider(val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val container = context.appContainer
        for (widgetId in appWidgetIds) {
            WidgetPublisher.publish(context, appWidgetManager, kind, widgetId, cachedState(container, widgetId))
        }
        // The network round-trip is handed to WorkManager rather than run inline. onUpdate
        // executes on the receiver's main thread, so the previous `runBlocking { refresh() }`
        // here could block the UI thread for as long as the HTTP call took — an ANR waiting
        // to happen on exactly the slow networks this widget has to cope with.
        container.refreshScheduler.enqueueOneShot(kind.providerClass.name)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetActions.ACTION_REFRESH) {
            handleRefreshRequest(context, intent)
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val container = context.appContainer
        for (widgetId in appWidgetIds) {
            container.releaseRepository.clearWidget(widgetId)
            container.refreshStateStore.clear(widgetId)
            container.refreshScheduler.cancelWatchdog(widgetId)
        }
    }

    private fun handleRefreshRequest(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        SafeLogger.i(TAG, "Refresh tap widgetId=$widgetId kind=${kind.name}")

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = appContext.appContainer
        val appWidgetManager = AppWidgetManager.getInstance(appContext)

        // Arm the safety net *before* drawing the spinner, so there is never a moment where a
        // Loading render exists without a scheduled escape from it.
        val startedAt = container.timeProvider.nowEpochMillis()
        container.refreshStateStore.markInFlight(widgetId, startedAt)
        container.refreshScheduler.enqueueWatchdog(
            widgetId = widgetId,
            providerClassName = kind.providerClass.name,
            refreshStartedAtEpochMillis = startedAt,
            delaySeconds = WATCHDOG_DELAY.inWholeSeconds,
        )
        val cached = container.releaseRepository.cachedRelease(widgetId)
        WidgetPublisher.publish(appContext, appWidgetManager, kind, widgetId, WidgetState.Loading(cached))

        refreshScope.launch {
            try {
                val state = fetchTerminalState(container, widgetId)
                awaitMinimumSpinnerDwell(container, startedAt)
                withContext(Dispatchers.Main) {
                    WidgetPublisher.publish(appContext, appWidgetManager, kind, widgetId, state)
                }
                SafeLogger.i(TAG, "Tap refresh settled widget=$widgetId state=${state::class.simpleName}")
                // Only now — the terminal render is out and the spinner is gone.
                container.refreshStateStore.clear(widgetId)
                container.refreshScheduler.cancelWatchdog(widgetId)
            } catch (cancellation: CancellationException) {
                SafeLogger.w(TAG, "Tap refresh cancelled widget=$widgetId; watchdog stays armed", cancellation)
                throw cancellation
            } catch (error: Throwable) {
                // Swallowed on purpose: letting a background coroutine crash would take the
                // whole process down and strand the spinner. The watchdog is still armed and
                // will publish the error state instead.
                SafeLogger.e(TAG, "Tap refresh failed widget=$widgetId; watchdog stays armed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Runs the refresh and maps its **actual** outcome to a terminal [WidgetState].
     *
     * Never throws, and never reports a success it did not get: the previous implementation
     * discarded the [com.acornjuice.downloadwidget.domain.model.RefreshResult] and simply
     * re-read the cache, so a failed refresh painted a green "UPDATED" badge over stale data.
     */
    private suspend fun fetchTerminalState(container: AppContainer, widgetId: Int): WidgetState {
        val repository = container.releaseRepository
        return try {
            withTimeout(REFRESH_TIMEOUT) {
                val result = repository.refresh(widgetId)
                SafeLogger.i(TAG, "Tap refresh widget=$widgetId result=${result::class.simpleName}")
                result.toWidgetState(repository.cachedRelease(widgetId))
            }
        } catch (timeout: TimeoutCancellationException) {
            SafeLogger.w(TAG, "Tap refresh widget=$widgetId timed out after $REFRESH_TIMEOUT", timeout)
            WidgetState.Error(WidgetState.ErrorKind.NETWORK, repository.cachedRelease(widgetId))
        }
    }

    /**
     * Keeps the spinner on screen for at least [MIN_SPINNER_DWELL], counting from the
     * `Loading` render.
     *
     * Two jobs: the user tapped refresh and deserves to see that something happened, and
     * spacing the two `updateAppWidget` calls clears the launcher-side window in which a
     * terminal render issued milliseconds after a `Loading` one gets coalesced away.
     */
    private suspend fun awaitMinimumSpinnerDwell(container: AppContainer, startedAtEpochMillis: Long) {
        val elapsed = container.timeProvider.nowEpochMillis() - startedAtEpochMillis
        val remaining = MIN_SPINNER_DWELL.inWholeMilliseconds - elapsed
        if (remaining > 0L) delay(remaining)
    }

    /**
     * What to show without touching the network. A configured-but-never-fetched widget
     * reports "not updated" rather than a spinner: the one-shot enqueued by [onUpdate] owns
     * that fetch, and a spinner this provider does not control would break the invariant.
     */
    private fun cachedState(container: AppContainer, widgetId: Int): WidgetState {
        if (!container.widgetConfigStore.get(widgetId).isComplete) return WidgetState.UnconfiguredEmpty
        val cached = container.releaseRepository.cachedRelease(widgetId)
        return cached?.let(WidgetState::Success)
            ?: WidgetState.Error(WidgetState.ErrorKind.NETWORK, cached = null)
    }

    private companion object {
        const val TAG = "DownloadWidget"

        /** Upper bound on the tap-refresh HTTP call. Real, not aspirational — see class kdoc. */
        val REFRESH_TIMEOUT = 8.seconds

        /** Minimum time the spinner stays up, measured from the `Loading` render. */
        val MIN_SPINNER_DWELL = 2.seconds

        /**
         * When the safety net fires. Comfortably past the worst honest case
         * ([REFRESH_TIMEOUT] plus a render), so a healthy refresh always disarms it first.
         */
        val WATCHDOG_DELAY = 15.seconds

        /**
         * Shared scope for the goAsync coroutine that spans a single tap. `SupervisorJob` so
         * one widget's failure does not cancel siblings; the coroutine is short-lived
         * (bounded by [REFRESH_TIMEOUT]) and kept alive by `goAsync`'s `PendingResult`, so no
         * lifecycle attachment is required.
         */
        val refreshScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
