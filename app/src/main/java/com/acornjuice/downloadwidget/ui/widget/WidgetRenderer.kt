package com.acornjuice.downloadwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
        wireAssetList(context, views, widgetId)
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

    private fun wireAssetList(context: Context, views: RemoteViews, widgetId: Int) {
        val listIntent = Intent(context, AssetListRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_asset_list, listIntent)
        views.setEmptyView(R.id.widget_asset_list, R.id.widget_empty)
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

    // Includes seconds so a user tapping refresh can visually confirm the tick even when
    // the API returned 304 NotModified (identical count + badge; only the timestamp moves).
    // Seconds included so the user can visually confirm each refresh tick even when the
    // API returns 304 NotModified (identical count + badge; only the timestamp moves).
    private const val TIME_PATTERN = "HH:mm:ss"
    private const val MILLIS_PER_SECOND = 1_000L
}
