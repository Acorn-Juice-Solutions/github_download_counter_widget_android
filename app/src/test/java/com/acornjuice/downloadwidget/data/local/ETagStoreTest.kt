package com.acornjuice.downloadwidget.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ETagStoreTest {

    private lateinit var store: ETagStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test-etag", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = ETagStore(prefs)
    }

    @Test
    fun `unset widget returns null`() {
        assertThat(store.get(1, "https://a", "v1")).isNull()
    }

    @Test
    fun `put then get with matching triple returns etag`() {
        store.put(widgetId = 1, url = "https://a", tag = "v1", etag = "W/\"abc\"")
        assertThat(store.get(1, "https://a", "v1")).isEqualTo("W/\"abc\"")
    }

    @Test
    fun `get returns null when URL differs`() {
        store.put(widgetId = 1, url = "https://a", tag = "v1", etag = "W/\"abc\"")
        assertThat(store.get(1, "https://b", "v1")).isNull()
    }

    @Test
    fun `get returns null when tag differs`() {
        store.put(widgetId = 1, url = "https://a", tag = "v1", etag = "W/\"abc\"")
        assertThat(store.get(1, "https://a", "v2")).isNull()
    }

    @Test
    fun `writes are isolated per widget`() {
        store.put(widgetId = 1, url = "https://a", tag = "v1", etag = "W/\"one\"")
        store.put(widgetId = 2, url = "https://a", tag = "v1", etag = "W/\"two\"")

        assertThat(store.get(1, "https://a", "v1")).isEqualTo("W/\"one\"")
        assertThat(store.get(2, "https://a", "v1")).isEqualTo("W/\"two\"")
    }

    @Test
    fun `clear removes only that widget's entries`() {
        store.put(widgetId = 1, url = "https://a", tag = "v1", etag = "W/\"one\"")
        store.put(widgetId = 2, url = "https://a", tag = "v1", etag = "W/\"two\"")

        store.clear(widgetId = 1)

        assertThat(store.get(1, "https://a", "v1")).isNull()
        assertThat(store.get(2, "https://a", "v1")).isEqualTo("W/\"two\"")
    }
}
