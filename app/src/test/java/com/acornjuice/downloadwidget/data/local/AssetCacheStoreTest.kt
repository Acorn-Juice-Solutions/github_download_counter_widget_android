package com.acornjuice.downloadwidget.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.acornjuice.downloadwidget.domain.model.Asset
import com.acornjuice.downloadwidget.domain.model.Release
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssetCacheStoreTest {

    private lateinit var store: AssetCacheStore
    private lateinit var rawPrefs: android.content.SharedPreferences

    private val sample = Release(
        tag = "v1.0.0",
        assets = listOf(Asset("a.apk", 10), Asset("b.aab", 3)),
        fetchedAtEpochSeconds = 1_700_000_000L,
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        rawPrefs = ctx.getSharedPreferences("test-asset-cache", Context.MODE_PRIVATE)
        rawPrefs.edit().clear().commit()
        store = AssetCacheStore(rawPrefs)
    }

    @Test
    fun `unset widget returns null`() {
        assertThat(store.get(1)).isNull()
    }

    @Test
    fun `put and get round-trip preserves release`() {
        store.put(widgetId = 5, release = sample)

        val loaded = store.get(widgetId = 5)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.tag).isEqualTo("v1.0.0")
        assertThat(loaded.assets).containsExactlyElementsIn(sample.assets).inOrder()
        assertThat(loaded.fetchedAtEpochSeconds).isEqualTo(1_700_000_000L)
        assertThat(loaded.totalDownloads).isEqualTo(13)
    }

    @Test
    fun `writes are isolated per widget`() {
        val other = sample.copy(tag = "v2.0.0", assets = listOf(Asset("z.zip", 99)))
        store.put(widgetId = 5, release = sample)
        store.put(widgetId = 6, release = other)

        assertThat(store.get(5)!!.tag).isEqualTo("v1.0.0")
        assertThat(store.get(6)!!.tag).isEqualTo("v2.0.0")
        assertThat(store.get(6)!!.totalDownloads).isEqualTo(99)
    }

    @Test
    fun `updateLastRefreshed touches timestamp without altering assets`() {
        store.put(widgetId = 5, release = sample)

        store.updateLastRefreshed(widgetId = 5, epochSeconds = 1_700_500_000L)

        val loaded = store.get(widgetId = 5)!!
        assertThat(loaded.assets).containsExactlyElementsIn(sample.assets).inOrder()
        assertThat(loaded.fetchedAtEpochSeconds).isEqualTo(1_700_500_000L)
    }

    @Test
    fun `clear removes only that widget's entries`() {
        store.put(widgetId = 5, release = sample)
        store.put(widgetId = 6, release = sample.copy(tag = "v2.0.0"))

        store.clear(widgetId = 5)

        assertThat(store.get(5)).isNull()
        assertThat(store.get(6)).isNotNull()
    }

    @Test
    fun `corrupted asset list JSON is treated as absent`() {
        rawPrefs.edit {
            putString(PrefKeys.scoped(PrefKeys.KEY_ASSET_LIST_JSON, 5), "{not-json")
            putString(PrefKeys.scoped("pref_asset_tag", 5), "v1.0.0")
            putLong(PrefKeys.scoped(PrefKeys.KEY_LAST_UPDATE_EPOCH, 5), 1L)
        }

        assertThat(store.get(5)).isNull()
    }

    @Test
    fun `empty assets list round-trips`() {
        store.put(widgetId = 5, release = Release("v1.0.0", emptyList(), 100L))

        val loaded = store.get(5)!!
        assertThat(loaded.assets).isEmpty()
        assertThat(loaded.tag).isEqualTo("v1.0.0")
    }
}
