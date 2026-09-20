package com.acornjuice.downloadwidget.ui.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
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

@RunWith(RobolectricTestRunner::class)
class WidgetRendererTest {

    private lateinit var context: Context
    private val fakeProviderClass: Class<*> = FakeProvider::class.java

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun render(state: WidgetState): View {
        val views: RemoteViews = WidgetRenderer.render(
            context = context,
            layoutId = R.layout.widget_layout,
            widgetId = 42,
            state = state,
            providerClass = fakeProviderClass,
        )
        return views.apply(context, null as ViewGroup?)
    }

    private fun textOf(root: View, viewId: Int): String =
        root.findViewById<TextView>(viewId).text.toString()

    private fun visibilityOf(root: View, viewId: Int): Int =
        root.findViewById<View>(viewId).visibility

    @Test
    fun `renders Success with total downloads and updated status`() {
        val release = Release(
            tag = "v1.2.3",
            assets = listOf(Asset("a.apk", 40), Asset("b.aab", 2)),
            fetchedAtEpochSeconds = 1_726_000_000L,
        )

        val root = render(WidgetState.Success(release))

        assertThat(textOf(root, R.id.widget_count)).isEqualTo("42")
        assertThat(textOf(root, R.id.widget_status))
            .isEqualTo(context.getString(R.string.widget_status_ok))
        // Idle: refresh icon visible, spinner hidden.
        assertThat(visibilityOf(root, R.id.widget_refresh)).isEqualTo(View.VISIBLE)
        assertThat(visibilityOf(root, R.id.widget_progress)).isEqualTo(View.GONE)
    }

    @Test
    fun `renders Loading uses cached total and shows spinner`() {
        val cached = Release("v1.0.0", listOf(Asset("a.apk", 12)), fetchedAtEpochSeconds = 1_000L)

        val root = render(WidgetState.Loading(cached = cached))

        assertThat(textOf(root, R.id.widget_count)).isEqualTo("12")
        assertThat(textOf(root, R.id.widget_status))
            .isEqualTo(context.getString(R.string.widget_status_in_progress))
        // While syncing: spinner visible, refresh icon hidden.
        assertThat(visibilityOf(root, R.id.widget_progress)).isEqualTo(View.VISIBLE)
        assertThat(visibilityOf(root, R.id.widget_refresh)).isEqualTo(View.GONE)
    }

    @Test
    fun `renders Loading without cache shows zero`() {
        val root = render(WidgetState.Loading(cached = null))
        assertThat(textOf(root, R.id.widget_count)).isEqualTo("0")
    }

    @Test
    fun `renders Error NETWORK shows NOT UPDATED status`() {
        val root = render(WidgetState.Error(WidgetState.ErrorKind.NETWORK, cached = null))
        assertThat(textOf(root, R.id.widget_status))
            .isEqualTo(context.getString(R.string.widget_status_not_updated))
    }

    @Test
    fun `renders Error RATE_LIMIT shows RATE LIMIT status`() {
        val root = render(WidgetState.Error(WidgetState.ErrorKind.RATE_LIMIT, cached = null))
        assertThat(textOf(root, R.id.widget_status))
            .isEqualTo(context.getString(R.string.widget_status_rate_limit))
    }

    @Test
    fun `renders Error NOT_FOUND shows TAG NOT FOUND status`() {
        val root = render(WidgetState.Error(WidgetState.ErrorKind.NOT_FOUND, cached = null))
        assertThat(textOf(root, R.id.widget_status))
            .isEqualTo(context.getString(R.string.widget_status_not_found))
    }

    @Test
    fun `renders UnconfiguredEmpty shows dash and NOT SET UP hint`() {
        val root = render(WidgetState.UnconfiguredEmpty)

        assertThat(textOf(root, R.id.widget_count)).isEqualTo("—")
        assertThat(textOf(root, R.id.widget_status))
            .isEqualTo(context.getString(R.string.widget_status_unconfigured))
        assertThat(textOf(root, R.id.widget_release_label))
            .isEqualTo(context.getString(R.string.widget_unconfigured_hint))
    }

    @Test
    fun `renders Success with version and separator in header`() {
        val release = Release("v1.2.3", emptyList(), 1_726_000_000L)

        val root = render(WidgetState.Success(release))

        val label = textOf(root, R.id.widget_release_label)
        assertThat(label).contains("Version v1.2.3")
        assertThat(label).contains("·")
    }

    /** Placeholder AppWidgetProvider class only used to feed a concrete `Class<*>` to the renderer. */
    private class FakeProvider : android.appwidget.AppWidgetProvider()
}
