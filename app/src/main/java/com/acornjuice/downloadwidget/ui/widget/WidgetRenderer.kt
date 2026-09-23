package com.acornjuice.downloadwidget.ui.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.acornjuice.downloadwidget.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure(-ish) mapping from [WidgetState] to a [RemoteViews] tree.
 *
 * "Pure-ish" because [RemoteViews] construction itself requires a package name (a [Context]
 * dependency), but the class does no I/O, holds no state, and is safe to unit-test with
 * Robolectric.
 *
 * The two widget layouts (`R.layout.widget_layout` and `R.layout.widget_layout_small`)
 * share the same set of view ids; the small layout marks unused views as `gone`, so all
 * `setViewVisibility` / `setTextViewText` calls below are safe against both.
 *
 * The asset list is not built here: [AssetListBinder] owns it, including which of the two
 * collection mechanisms the running OS version allows.
 *
 * ### Why the asset list is inlined (API 31+)
 *
 * This is the fix for the widget's longest-running bug. The list used to be backed by
 * [AssetListRemoteViewsService] via the legacy `setRemoteAdapter(viewId, Intent)`, re-wired
 * on every render. A `RemoteViews` carrying a remote adapter forces the host to bind that
 * service and apply the tree asynchronously — and on the target launchers that apply never
 * replaced the live view. The host kept the previous view tree, whose adapter was still
 * alive and still answered `notifyAppWidgetViewDataChanged`. The result was the symptom that
 * defeated three earlier rounds of fixes: **the asset list kept updating while the count,
 * the badge and the spinner stayed frozen on whatever rendered first**, which made it look
 * like a redraw/coalescing problem rather than an apply failure.
 *
 * [RemoteViews.RemoteCollectionItems] (API 31+) carries the rows inside the `RemoteViews`
 * itself: no service, no binding, no async apply, and no `notifyAppWidgetViewDataChanged`.
 * The whole widget lands as one unit, so the list can no longer disagree with the rest of
 * the widget — the rows are derived from the very same [WidgetState].
 *
 * Below API 31 the service-backed adapter remains the only option, so that path is kept.
 */
object WidgetRenderer {

    fun render(
        context: Context,
        @LayoutRes layoutId: Int,
        widgetId: Int,
        state: WidgetState,
        providerClass: Class<*>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
        wireActions(context, views, widgetId, providerClass)
        AssetListBinder.bind(context, views, widgetId, state)
        applyState(context, views, state)
        return views
    }

    private fun wireActions(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        providerClass: Class<*>,
    ) {
        val refreshPending = WidgetActions.refreshPendingIntent(context, widgetId, providerClass)
        val settingsPending = WidgetActions.settingsPendingIntent(context, widgetId)
        views.setOnClickPendingIntent(R.id.widget_refresh_container, refreshPending)
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)
        views.setOnClickPendingIntent(R.id.widget_settings, settingsPending)
    }

    private fun applyState(context: Context, views: RemoteViews, state: WidgetState) {
        when (state) {
            is WidgetState.Success -> renderSuccess(context, views, state)
            is WidgetState.Loading -> renderLoading(context, views, state)
            is WidgetState.Error -> renderError(context, views, state)
            WidgetState.UnconfiguredEmpty -> renderUnconfigured(context, views)
        }
    }

    private fun renderSuccess(context: Context, views: RemoteViews, state: WidgetState.Success) {
        setHeader(context, views, tag = state.release.tag, lastUpdateEpoch = state.release.fetchedAtEpochSeconds)
        views.setTextViewText(R.id.widget_count, state.release.totalDownloads.toString())
        setStatus(context, views, R.string.widget_status_ok, R.drawable.status_background)
        setSpinner(views, visible = false)
    }

    private fun renderLoading(context: Context, views: RemoteViews, state: WidgetState.Loading) {
        val cached = state.cached
        val tag = cached?.tag ?: ""
        setHeader(context, views, tag = tag, lastUpdateEpoch = cached?.fetchedAtEpochSeconds ?: 0L)
        views.setTextViewText(R.id.widget_count, (cached?.totalDownloads ?: 0).toString())
        setStatus(context, views, R.string.widget_status_in_progress, R.drawable.status_in_progress_background)
        setSpinner(views, visible = true)
    }

    private fun renderError(context: Context, views: RemoteViews, state: WidgetState.Error) {
        val cached = state.cached
        val tag = cached?.tag ?: ""
        setHeader(context, views, tag = tag, lastUpdateEpoch = cached?.fetchedAtEpochSeconds ?: 0L)
        views.setTextViewText(R.id.widget_count, (cached?.totalDownloads ?: 0).toString())
        val (label, background) = errorLabelFor(state.kind)
        setStatus(context, views, label, background)
        setSpinner(views, visible = false)
    }

    private fun renderUnconfigured(context: Context, views: RemoteViews) {
        views.setTextViewText(
            R.id.widget_release_label,
            context.getString(R.string.widget_unconfigured_hint),
        )
        views.setTextViewText(R.id.widget_count, "—")
        setStatus(context, views, R.string.widget_status_unconfigured, R.drawable.status_not_updated_background)
        setSpinner(views, visible = false)
    }

    private fun errorLabelFor(kind: WidgetState.ErrorKind): Pair<Int, Int> = when (kind) {
        WidgetState.ErrorKind.NETWORK -> R.string.widget_status_not_updated to R.drawable.status_not_updated_background
        WidgetState.ErrorKind.RATE_LIMIT -> R.string.widget_status_rate_limit to R.drawable.status_error_background
        WidgetState.ErrorKind.NOT_FOUND -> R.string.widget_status_not_found to R.drawable.status_error_background
        WidgetState.ErrorKind.INVALID_CONFIG -> R.string.widget_status_unconfigured to R.drawable.status_not_updated_background
    }

    private fun setHeader(context: Context, views: RemoteViews, tag: String, lastUpdateEpoch: Long) {
        val versionLabel = if (tag.isBlank()) "—" else context.getString(R.string.widget_version_label, tag)
        val timeLabel = if (lastUpdateEpoch <= 0L) {
            context.getString(R.string.widget_last_update_never)
        } else {
            SimpleDateFormat(TIME_PATTERN, Locale.getDefault())
                .format(Date(lastUpdateEpoch * MILLIS_PER_SECOND))
        }
        views.setTextViewText(
            R.id.widget_release_label,
            context.getString(R.string.widget_release_label_format, versionLabel, timeLabel),
        )
    }

    private fun setStatus(
        context: Context,
        views: RemoteViews,
        @StringRes labelRes: Int,
        backgroundRes: Int,
    ) {
        views.setTextViewText(R.id.widget_status, context.getString(labelRes))
        views.setInt(R.id.widget_status, "setBackgroundResource", backgroundRes)
    }

    private fun setSpinner(views: RemoteViews, visible: Boolean) {
        views.setViewVisibility(R.id.widget_progress, if (visible) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_refresh, if (visible) View.GONE else View.VISIBLE)
    }

    // Seconds included so the user can visually confirm each refresh tick even when the
    // API returns 304 NotModified (identical count + badge; only the timestamp moves).
    private const val TIME_PATTERN = "HH:mm:ss"
    private const val MILLIS_PER_SECOND = 1_000L
}
