package com.acornjuice.downloadwidget.ui.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.acornjuice.downloadwidget.R
import com.acornjuice.downloadwidget.domain.model.Asset
import com.acornjuice.downloadwidget.domain.model.Release
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression guards for the bug where the widget showed a fresh asset list next to a stale
 * total. The rows used to be pulled from the cache by a separate `RemoteViewsService` on its
 * own schedule; they now come from the same [WidgetState] as everything else, so the two
 * cannot disagree.
 */
@RunWith(RobolectricTestRunner::class)
class AssetListBinderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun textOf(root: View, viewId: Int): String =
        root.findViewById<TextView>(viewId).text.toString()

    @Test
    fun `assetsFor Success uses the fetched release`() {
        val release = Release("v1.2.3", listOf(Asset("a.apk", 40), Asset("b.aab", 2)), 1_726_000_000L)

        assertThat(AssetListBinder.assetsFor(WidgetState.Success(release))).isEqualTo(release.assets)
    }

    @Test
    fun `assetsFor Loading and Error fall back to the cached release`() {
        val cached = Release("v1.0.0", listOf(Asset("a.apk", 12)), 1_000L)

        assertThat(AssetListBinder.assetsFor(WidgetState.Loading(cached))).isEqualTo(cached.assets)
        assertThat(AssetListBinder.assetsFor(WidgetState.Error(WidgetState.ErrorKind.NETWORK, cached)))
            .isEqualTo(cached.assets)
    }

    @Test
    fun `assetsFor yields no rows when there is nothing to show`() {
        assertThat(AssetListBinder.assetsFor(WidgetState.Loading(cached = null))).isEmpty()
        assertThat(AssetListBinder.assetsFor(WidgetState.Error(WidgetState.ErrorKind.NETWORK, cached = null)))
            .isEmpty()
        assertThat(AssetListBinder.assetsFor(WidgetState.UnconfiguredEmpty)).isEmpty()
    }

    @Test
    fun `assetRow renders the asset name and its download count`() {
        val row = AssetListBinder.assetRow(context.packageName, Asset("AccuVideoPro.msi", 147))
            .apply(context, null as ViewGroup?)

        assertThat(textOf(row, R.id.widget_asset_name)).isEqualTo("AccuVideoPro.msi")
        assertThat(textOf(row, R.id.widget_asset_count)).isEqualTo("147")
    }

    @Test
    fun `the suite exercises the inline collection path, not the service-backed one`() {
        // Robolectric runs these tests at SDK 34, comfortably above the API 31 boundary, so
        // the rows are expected to ride inside the RemoteViews — which is also why
        // WidgetPublisher must not issue notifyAppWidgetViewDataChanged here.
        assertThat(AssetListBinder.usesServiceBackedCollection).isFalse()
    }
}
