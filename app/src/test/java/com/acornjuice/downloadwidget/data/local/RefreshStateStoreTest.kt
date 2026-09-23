package com.acornjuice.downloadwidget.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RefreshStateStoreTest {

    private lateinit var store: RefreshStateStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test-refresh-state", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = RefreshStateStore(prefs)
    }

    @Test
    fun `no refresh in flight by default`() {
        assertThat(store.inFlightSince(widgetId = 1)).isNull()
    }

    @Test
    fun `markInFlight round-trips the stamp`() {
        store.markInFlight(widgetId = 42, startedAtEpochMillis = 1_726_000_000_123L)

        assertThat(store.inFlightSince(widgetId = 42)).isEqualTo(1_726_000_000_123L)
    }

    @Test
    fun `clear ends the in-flight refresh`() {
        store.markInFlight(widgetId = 42, startedAtEpochMillis = 1_726_000_000_123L)

        store.clear(widgetId = 42)

        assertThat(store.inFlightSince(widgetId = 42)).isNull()
    }

    @Test
    fun `markers are scoped per widget`() {
        store.markInFlight(widgetId = 1, startedAtEpochMillis = 1_000L)
        store.markInFlight(widgetId = 2, startedAtEpochMillis = 2_000L)

        store.clear(widgetId = 1)

        assertThat(store.inFlightSince(widgetId = 1)).isNull()
        assertThat(store.inFlightSince(widgetId = 2)).isEqualTo(2_000L)
    }

    @Test
    fun `a newer tap replaces the stamp so the older watchdog can fence itself off`() {
        store.markInFlight(widgetId = 7, startedAtEpochMillis = 1_000L)

        store.markInFlight(widgetId = 7, startedAtEpochMillis = 5_000L)

        assertThat(store.inFlightSince(widgetId = 7)).isEqualTo(5_000L)
    }
}
