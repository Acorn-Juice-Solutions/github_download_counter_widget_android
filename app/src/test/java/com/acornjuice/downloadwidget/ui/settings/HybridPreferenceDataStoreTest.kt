package com.acornjuice.downloadwidget.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acornjuice.downloadwidget.data.local.PrefKeys
import com.acornjuice.downloadwidget.data.local.SecureTokenStore
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HybridPreferenceDataStoreTest {

    private lateinit var plain: android.content.SharedPreferences
    private lateinit var secureToken: SecureTokenStore
    private lateinit var store: HybridPreferenceDataStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        plain = ctx.getSharedPreferences("test-hybrid-plain", Context.MODE_PRIVATE)
        val secure = ctx.getSharedPreferences("test-hybrid-secure", Context.MODE_PRIVATE)
        plain.edit().clear().commit()
        secure.edit().clear().commit()
        secureToken = SecureTokenStore(secure)
        store = HybridPreferenceDataStore(plain = plain, secureToken = secureToken)
    }

    @Test
    fun `trims whitespace and newlines on plain writes`() {
        // Real UI keys are widget-scoped (e.g. `pref_api_url_42`). Trim must apply to any key
        // this data store handles — a paste-happy user is the root cause we're guarding against.
        val urlKey = PrefKeys.scoped(PrefKeys.KEY_API_URL, 42)
        val tagKey = PrefKeys.scoped(PrefKeys.KEY_TAG, 42)

        store.putString(urlKey, "  https://api.github.com/repos/o/r/releases\n")
        store.putString(tagKey, "\tv1.7.6 ")

        assertThat(plain.getString(urlKey, null)).isEqualTo("https://api.github.com/repos/o/r/releases")
        assertThat(plain.getString(tagKey, null)).isEqualTo("v1.7.6")
    }

    @Test
    fun `token write goes to secure store and is trimmed`() {
        store.putString(PrefKeys.KEY_TOKEN, "  ghp_abc123\n")

        assertThat(secureToken.get()).isEqualTo("ghp_abc123")
        // Must not leak into plain prefs.
        assertThat(plain.contains(PrefKeys.KEY_TOKEN)).isFalse()
    }

    @Test
    fun `token read prefers secure store`() {
        secureToken.set("ghp_secure")

        assertThat(store.getString(PrefKeys.KEY_TOKEN, defValue = null)).isEqualTo("ghp_secure")
    }

    @Test
    fun `blank token write clears the store`() {
        secureToken.set("ghp_existing")

        store.putString(PrefKeys.KEY_TOKEN, "   \n")

        assertThat(secureToken.get()).isNull()
    }

    @Test
    fun `null value on plain key is written as null`() {
        store.putString("pref_api_url_1", null)

        assertThat(plain.contains("pref_api_url_1")).isFalse()
    }
}
