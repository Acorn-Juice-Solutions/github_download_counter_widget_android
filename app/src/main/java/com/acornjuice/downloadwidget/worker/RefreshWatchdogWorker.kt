package com.acornjuice.downloadwidget.worker

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.acornjuice.downloadwidget.appContainer
import com.acornjuice.downloadwidget.ui.widget.WidgetKind
import com.acornjuice.downloadwidget.ui.widget.WidgetPublisher
import com.acornjuice.downloadwidget.ui.widget.WidgetState
import com.acornjuice.downloadwidget.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Safety net guaranteeing a widget can never be stranded showing the `Loading` spinner.
 *
 * The tap refresh renders `Loading` synchronously and the render that leaves it comes from a
 * coroutine held alive only by `goAsync()`. If that process is killed first — memory
 * pressure, Doze, the user swiping the app away — nothing in-process is left to clear the
 * spinner, and the widget would sit on a yellow "UPDATING" badge until the next hourly
 * refresh. That is the bug this worker exists to make structurally impossible.
 *
 * Armed by [RefreshScheduler.enqueueWatchdog] alongside every `Loading` render and cancelled
 * by the terminal render. Because WorkManager persists its queue, the watchdog survives the
 * very process death it is guarding against.
 *
 * It is deliberately conservative: it only ever acts when the exact refresh it was armed for
 * is *still* the pending one (see [shouldIntervene]), so a late or duplicated run cannot
 * overwrite a healthy result.
 */
class RefreshWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(RefreshScheduler.KEY_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val armedFor = inputData.getLong(RefreshScheduler.KEY_REFRESH_STARTED_AT, NO_STAMP)
        val kind = inputData.getString(RefreshScheduler.KEY_PROVIDER_CLASS)
            ?.let(WidgetKind::fromProviderClassName)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null || armedFor == NO_STAMP) {
            SafeLogger.w(TAG, "Watchdog fired with unusable input widget=$widgetId; ignoring")
            return Result.success()
        }

        val container = applicationContext.appContainer
        val inFlightSince = container.refreshStateStore.inFlightSince(widgetId)
        if (!shouldIntervene(inFlightSince = inFlightSince, armedFor = armedFor)) {
            SafeLogger.i(TAG, "Watchdog stand-down widget=$widgetId inFlight=$inFlightSince armedFor=$armedFor")
            return Result.success()
        }

        SafeLogger.w(TAG, "Watchdog rescuing widget=$widgetId stranded in Loading since $armedFor")
        container.refreshStateStore.clear(widgetId)
        val state = WidgetState.Error(
            kind = WidgetState.ErrorKind.NETWORK,
            cached = container.releaseRepository.cachedRelease(widgetId),
        )
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        withContext(Dispatchers.Main) {
            WidgetPublisher.publish(applicationContext, appWidgetManager, kind, widgetId, state)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "RefreshWatchdog"
        private const val NO_STAMP = 0L

        /**
         * Whether this watchdog should force a terminal render.
         *
         * Only when the marker it was armed for is still the live one:
         * - `null` marker → the refresh finished and cleared it. Nothing to rescue.
         * - a *different* stamp → a newer tap is in flight and owns its own watchdog.
         *   Intervening here would replace a healthy spinner with a bogus error.
         */
        fun shouldIntervene(inFlightSince: Long?, armedFor: Long): Boolean =
            inFlightSince != null && inFlightSince == armedFor
    }
}
