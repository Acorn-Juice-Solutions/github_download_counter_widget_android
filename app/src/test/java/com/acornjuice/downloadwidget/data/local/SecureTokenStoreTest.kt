package com.acornjuice.downloadwidget.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureTokenStoreTest {

    private lateinit var store: SecureTokenStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test-secure-token", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = SecureTokenStore(prefs)
    }

    @Test
    fun `unset store returns null`() {
        assertThat(store.get()).isNull()
    }

    @Test
    fun `set stores and get returns the value`() {
        store.set("ghp_secretvalue")
        assertThat(store.get()).isEqualTo("ghp_secretvalue")
    }

    @Test
    fun `set trims whitespace`() {
        store.set("  ghp_secretvalue  ")
        assertThat(store.get()).isEqualTo("ghp_secretvalue")
    }

    @Test
    fun `set with blank clears the store`() {
        store.set("ghp_secretvalue")
        store.set("   ")
        assertThat(store.get()).isNull()
    }

    @Test
    fun `clear removes stored value`() {
        store.set("ghp_secretvalue")
        store.clear()
        assertThat(store.get()).isNull()
    }
}
