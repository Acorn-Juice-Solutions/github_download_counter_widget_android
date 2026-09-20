package com.acornjuice.downloadwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.acornjuice.downloadwidget.R
import com.acornjuice.downloadwidget.appContainer
import com.acornjuice.downloadwidget.data.repo.ReleaseRepository
import com.acornjuice.downloadwidget.util.SafeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Thin `AppWidgetProvider` base. Everything expensive is delegated:
 *
 * - Data access → [com.acornjuice.downloadwidget.data.repo.ReleaseRepository].
 * - Rendering  → [WidgetRenderer].
 * - Background refresh → [com.acornjuice.downloadwidget.worker.RefreshScheduler] via
 *   [com.acornjuice.downloadwidget.worker.RefreshWorker].
 *
 * ### Tap-refresh flow
 *
 * On this app's target launchers, two `updateAppWidget` calls to the same widget within
 * ~1 s get coalesced — the "Loading" render lands, the follow-up terminal render is
 * silently dropped and the spinner never stops. To sidestep that we split the two
 * updates across **separate receiver invocations** and space them by ≥ 2 s in total,
 * comfortably outside the launcher's dedup window:
 *
 * 1. Immediately in the tap's `onReceive`: synchronous `updateAppWidget(Loading)` →
 *    spinner appears + badge turns yellow "UPDATING".
 * 2. `goAsync()` keeps the process alive; a coroutine does the HTTP fetch off the main
 *    thread, then waits [POST_LOADING_DELAY] to age past the launcher's dedup window
 *    before firing an explicit-target `ACTION_APPLY_FOLLOWUP` broadcast.
 * 3. That broadcast triggers a **fresh** `onReceive` → [handleFollowupApply], which reads
 *    the freshly populated cache and emits the terminal render via `updateAppWidget`.
 *    A prior iteration used `partiallyUpdateAppWidget` here, hoping it would slip past
 *    the dedup — but on some launchers the merged-partial update failed to repaint
 *    text/visibility setters (the ListView still refreshed via
 *    `notifyAppWidgetViewDataChanged`, masking the bug as "assets update but count and
 *    spinner don't"). Once the [POST_LOADING_DELAY] is comfortably past the ~1 s dedup
 *    window, plain `updateAppWidget` is the reliable choice.
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
        val container = context.appContainer
        for (widgetId in appWidgetIds) {
            val state = computeState(container, widgetId)
            renderWidget(context, appWidgetManager, widgetId, state)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetActions.ACTION_REFRESH -> handleRefreshRequest(context, intent)
            WidgetActions.ACTION_APPLY_FOLLOWUP -> handleFollowupApply(context, intent)
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val repository = context.appContainer.releaseRepository
        for (widgetId in appWidgetIds) repository.clearWidget(widgetId)
    }

    private fun handleRefreshRequest(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        SafeLogger.i(TAG, "Refresh tap widgetId=$widgetId kind=${kind.name}")

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = appContext.appContainer
        val manager = AppWidgetManager.getInstance(appContext)

        // 1. Immediate Loading render on the receiver's main thread — spinner appears.
        val cached = container.releaseRepository.cachedRelease(widgetId)
        renderWidget(appContext, manager, widgetId, WidgetState.Loading(cached = cached))

        // 2. Off-main network fetch + delayed rebroadcast to trigger the terminal render
        //    from a fresh onReceive execution (see class kdoc).
        refreshScope.launch {
            try {
                var networkFailed = false
                try {
                    withTimeout(REFRESH_TIMEOUT) {
                        val result = container.releaseRepository.refresh(widgetId)
                        SafeLogger.i(TAG, "Inline refresh widget=$widgetId result=${result::class.simpleName}")
                    }
                } catch (timeout: TimeoutCancellationException) {
                    SafeLogger.w(TAG, "Inline refresh widget=$widgetId timed out after $REFRESH_TIMEOUT", timeout)
                    networkFailed = true
                }
                delay(POST_LOADING_DELAY)
                appContext.sendBroadcast(buildFollowupUpdateIntent(appContext, widgetId))
                withContext(Dispatchers.Main) {
                    val toastRes = if (networkFailed) {
                        R.string.widget_refresh_toast_error
                    } else {
                        R.string.widget_refresh_toast_updated
                    }
                    Toast.makeText(appContext, toastRes, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Custom explicit-target intent that hits [handleFollowupApply] in a fresh
     * `onReceive` execution. Uniquified via `data` so successive taps do not collide in
     * Android's pending-broadcast dedup.
     */
    private fun buildFollowupUpdateIntent(context: Context, widgetId: Int): Intent =
        Intent(context, kind.providerClass).apply {
            action = WidgetActions.ACTION_APPLY_FOLLOWUP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.Builder()
                .scheme(FOLLOWUP_URI_SCHEME)
                .authority(FOLLOWUP_URI_HOST)
                .appendPath(widgetId.toString())
                .appendPath(System.currentTimeMillis().toString())
                .build()
        }

    /**
     * Second-stage of a tap refresh: the network call is already done (cache is fresh),
     * we just render the terminal state. Uses `updateAppWidget` (via [renderWidget]):
     * by the time we get here we are already [POST_LOADING_DELAY] past the initial
     * `Loading` render, comfortably outside the ~1 s launcher dedup window that this
     * flow was designed to skirt around.
     */
    private fun handleFollowupApply(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val container = context.appContainer
        val config = container.widgetConfigStore.get(widgetId)
        val state: WidgetState = if (!config.isComplete) {
            WidgetState.UnconfiguredEmpty
        } else {
            // Cache is already fresh from handleRefreshRequest's network call — read it
            // rather than making another API round-trip.
            val cached = container.releaseRepository.cachedRelease(widgetId)
            if (cached != null) {
                WidgetState.Success(cached)
            } else {
                WidgetState.Error(WidgetState.ErrorKind.NETWORK, cached = null)
            }
        }
        SafeLogger.i(TAG, "Followup apply widget=$widgetId state=${state::class.simpleName}")
        renderWidget(context, AppWidgetManager.getInstance(context), widgetId, state)
    }

    private fun computeState(
        container: com.acornjuice.downloadwidget.di.AppContainer,
        widgetId: Int,
    ): WidgetState {
        val config = container.widgetConfigStore.get(widgetId)
        if (!config.isComplete) return WidgetState.UnconfiguredEmpty
        return refreshInline(container.releaseRepository, widgetId)
    }

    private fun refreshInline(
        repository: ReleaseRepository,
        widgetId: Int,
    ): WidgetState = try {
        runBlocking {
            withTimeout(REFRESH_TIMEOUT) {
                val result = repository.refresh(widgetId)
                SafeLogger.i(TAG, "onUpdate refresh widget=$widgetId result=${result::class.simpleName}")
                val cached = repository.cachedRelease(widgetId)
                result.toWidgetState(cached)
            }
        }
    } catch (timeout: TimeoutCancellationException) {
        SafeLogger.w(TAG, "onUpdate refresh widget=$widgetId timed out", timeout)
        WidgetState.Error(
            kind = WidgetState.ErrorKind.NETWORK,
            cached = repository.cachedRelease(widgetId),
        )
    }

    private fun renderWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        state: WidgetState,
    ) {
        appWidgetManager.updateAppWidget(
            widgetId,
            WidgetRenderer.render(
                context = context,
                layoutId = kind.layoutRes,
                widgetId = widgetId,
                state = state,
                providerClass = kind.providerClass,
            ),
        )
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_asset_list)
    }

    private companion object {
        const val TAG = "DownloadWidget"
        const val FOLLOWUP_URI_SCHEME = "widget-followup"
        const val FOLLOWUP_URI_HOST = "update"

        val REFRESH_TIMEOUT = 8.seconds

        // Gap between the "Loading" updateAppWidget and the follow-up render. Two roles:
        // (a) age past the launcher's dedup window (>1 s empirically), (b) keep the
        // yellow "UPDATING" badge + spinner on screen long enough for the user to see
        // the refresh actually happening. 3 s + the ~500 ms API call leaves ~3.5 s of
        // Loading state, plenty for the spinner to animate visibly.
        val POST_LOADING_DELAY = 3000.milliseconds

        /**
         * Shared scope for the goAsync coroutine that spans a single tap. `SupervisorJob`
         * so one widget's failure does not cancel siblings; the coroutine is short-lived
         * (bounded by REFRESH_TIMEOUT + POST_LOADING_DELAY) and kept alive by `goAsync`'s
         * `PendingResult`, so no lifecycle attachment is required.
         */
        val refreshScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
