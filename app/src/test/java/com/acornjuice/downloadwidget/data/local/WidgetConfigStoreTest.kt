package com.acornjuice.downloadwidget.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetConfigStoreTest {

    private lateinit var store: WidgetConfigStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test-widget-config", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = WidgetConfigStore(prefs)
    }

    @Test
    fun `unset widget returns EMPTY config`() {
        assertThat(store.get(1)).isEqualTo(WidgetConfig.EMPTY)
        assertThat(store.get(1).isComplete).isFalse()
    }

    @Test
    fun `set url and tag round-trips`() {
        store.setUrl(widgetId = 42, url = "https://api.github.com/repos/o/r/releases")
        store.setTag(widgetId = 42, tag = "v1.0.0")

        val config = store.get(widgetId = 42)
        assertThat(config.apiUrl).isEqualTo("https://api.github.com/repos/o/r/releases")
        assertThat(config.tag).isEqualTo("v1.0.0")
        assertThat(config.isComplete).isTrue()
    }

    @Test
    fun `writes are isolated per widgetId`() {
        store.setUrl(widgetId = 1, url = "url-1")
        store.setTag(widgetId = 1, tag = "v1")
        store.setUrl(widgetId = 2, url = "url-2")
        store.setTag(widgetId = 2, tag = "v2")

        assertThat(store.get(1)).isEqualTo(WidgetConfig("url-1", "v1"))
        assertThat(store.get(2)).isEqualTo(WidgetConfig("url-2", "v2"))
    }

    @Test
    fun `clear removes only that widget's entries`() {
        store.setUrl(widgetId = 1, url = "url-1")
        store.setTag(widgetId = 1, tag = "v1")
        store.setUrl(widgetId = 2, url = "url-2")
        store.setTag(widgetId = 2, tag = "v2")

        store.clear(widgetId = 1)

        assertThat(store.get(1)).isEqualTo(WidgetConfig.EMPTY)
        assertThat(store.get(2)).isEqualTo(WidgetConfig("url-2", "v2"))
    }

    @Test
    fun `blank url or tag reports incomplete`() {
        store.setUrl(widgetId = 3, url = "   ")
        store.setTag(widgetId = 3, tag = "v1.0.0")
        assertThat(store.get(3).isComplete).isFalse()

        store.setUrl(widgetId = 3, url = "https://x")
        store.setTag(widgetId = 3, tag = "")
        assertThat(store.get(3).isComplete).isFalse()
    }

    @Test
    fun `trims whitespace and newlines on write`() {
        store.setUrl(widgetId = 7, url = "  https://api.github.com/repos/o/r/releases\n")
        store.setTag(widgetId = 7, tag = "\tv1.7.6 ")

        val config = store.get(widgetId = 7)
        assertThat(config.apiUrl).isEqualTo("https://api.github.com/repos/o/r/releases")
        assertThat(config.tag).isEqualTo("v1.7.6")
    }

    @Test
    fun `falls back to unscoped keys when scoped are missing`() {
        // Simulates the user opening MainActivity from the app launcher icon (no
        // EXTRA_APPWIDGET_ID), which stores config under the bare keys.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test-widget-config", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PrefKeys.KEY_API_URL, "https://api.github.com/repos/o/r/releases")
            .putString(PrefKeys.KEY_TAG, "v1.7.6")
            .commit()

        val config = store.get(widgetId = 42)
        assertThat(config.apiUrl).isEqualTo("https://api.github.com/repos/o/r/releases")
        assertThat(config.tag).isEqualTo("v1.7.6")
        assertThat(config.isComplete).isTrue()
    }

    @Test
    fun `scoped keys win over unscoped fallback`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test-widget-config", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PrefKeys.KEY_API_URL, "https://unscoped-default/releases")
            .putString(PrefKeys.KEY_TAG, "v0.0.0")
            .commit()
        store.setUrl(widgetId = 42, url = "https://scoped-per-widget/releases")
        store.setTag(widgetId = 42, tag = "v9.9.9")

        val config = store.get(widgetId = 42)
        assertThat(config.apiUrl).isEqualTo("https://scoped-per-widget/releases")
        assertThat(config.tag).isEqualTo("v9.9.9")
    }

    @Test
    fun `blank scoped falls through to unscoped`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test-widget-config", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PrefKeys.KEY_API_URL, "https://api.github.com/repos/o/r/releases")
            .putString(PrefKeys.KEY_TAG, "v1.7.6")
            .commit()
        // Scoped keys exist but are whitespace-only — should be treated as absent.
        store.setUrl(widgetId = 5, url = "   ")
        store.setTag(widgetId = 5, tag = "   ")

        val config = store.get(widgetId = 5)
        assertThat(config.apiUrl).isEqualTo("https://api.github.com/repos/o/r/releases")
        assertThat(config.tag).isEqualTo("v1.7.6")
    }

    @Test
    fun `trims stored dirty values on read`() {
        // Simulate legacy data that was persisted before write-side trimming existed
        // (or written directly by another code path).
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test-widget-config", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PrefKeys.scoped(PrefKeys.KEY_API_URL, 9), " https://api.github.com/repos/o/r/releases ")
            .putString(PrefKeys.scoped(PrefKeys.KEY_TAG, 9), "v1.7.6\r\n")
            .commit()

        val config = store.get(widgetId = 9)
        assertThat(config.apiUrl).isEqualTo("https://api.github.com/repos/o/r/releases")
        assertThat(config.tag).isEqualTo("v1.7.6")
        assertThat(config.isComplete).isTrue()
    }
}
