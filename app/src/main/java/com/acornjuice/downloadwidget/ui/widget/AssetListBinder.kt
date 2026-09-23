package com.acornjuice.downloadwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.acornjuice.downloadwidget.R
import com.acornjuice.downloadwidget.domain.model.Asset

/**
 * Everything about *where the widget's asset rows come from*, kept apart from the
 * [WidgetState] to [RemoteViews] mapping in [WidgetRenderer].
 *
 * Two paths exist because [RemoteViews.RemoteCollectionItems] only arrived in API 31:
 *
 * - **API 31+** — the rows travel inside the `RemoteViews` itself: no service, no binding,
 *   no async apply. The whole widget lands as one unit, so the list cannot disagree with
 *   the count or the badge.
 * - **Below API 31** — the legacy [AssetListRemoteViewsService] adapter, the only option
 *   available there, and the one whose async apply caused the bug documented in
 *   [WidgetRenderer]'s kdoc.
 */
internal object AssetListBinder {

    /**
     * Whether the legacy [AssetListRemoteViewsService] adapter is in use, which is the only
     * case where the collection needs an explicit `notifyAppWidgetViewDataChanged`.
     * Consumed by [WidgetPublisher].
     */
    val usesServiceBackedCollection: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    /** Points [views]' collection at the rows [state] implies, by whichever path the OS supports. */
    fun bind(context: Context, views: RemoteViews, widgetId: Int, state: WidgetState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setRemoteAdapter(R.id.widget_asset_list, inlineAssetItems(context, assetsFor(state)))
        } else {
            wireServiceBackedAssetList(context, views, widgetId)
        }
        views.setEmptyView(R.id.widget_asset_list, R.id.widget_empty)
    }

    /**
     * The rows [state] implies.
     *
     * Deriving them from the [WidgetState] rather than re-reading the cache is what keeps the
     * list and the counter in lockstep. The old service-backed factory read the cache on its
     * own schedule, which is how the widget could show a fresh asset list next to a stale
     * total — the symptom that made this bug so hard to read.
     */
    fun assetsFor(state: WidgetState): List<Asset> = when (state) {
        is WidgetState.Success -> state.release.assets
        is WidgetState.Loading -> state.cached?.assets.orEmpty()
        is WidgetState.Error -> state.cached?.assets.orEmpty()
        WidgetState.UnconfiguredEmpty -> emptyList()
    }

    /** A single asset row. Shared by both collection paths so they cannot drift apart. */
    fun assetRow(packageName: String, asset: Asset): RemoteViews =
        RemoteViews(packageName, R.layout.widget_list_item).apply {
            setTextViewText(R.id.widget_asset_name, asset.name)
            setTextViewText(R.id.widget_asset_count, asset.downloadCount.toString())
        }

    /**
     * Rows travelling inside the [RemoteViews]. Size is bounded by a GitHub release's asset
     * count (single digits in practice), comfortably inside the Binder transaction budget.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun inlineAssetItems(context: Context, assets: List<Asset>): RemoteViews.RemoteCollectionItems =
        RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(ASSET_VIEW_TYPE_COUNT)
            .apply {
                assets.forEachIndexed { index, asset ->
                    addItem(index.toLong(), assetRow(context.packageName, asset))
                }
            }
            .build()

    /** Pre-API-31 fallback: the only way to populate a collection before inline items existed. */
    @Suppress("DEPRECATION")
    private fun wireServiceBackedAssetList(context: Context, views: RemoteViews, widgetId: Int) {
        val listIntent = Intent(context, AssetListRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_asset_list, listIntent)
    }

    /** Every asset row uses the same layout. */
    private const val ASSET_VIEW_TYPE_COUNT = 1
}
