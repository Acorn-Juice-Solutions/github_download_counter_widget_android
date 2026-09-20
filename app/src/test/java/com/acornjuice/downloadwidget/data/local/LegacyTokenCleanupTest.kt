package com.acornjuice.downloadwidget.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacyTokenCleanupTest {

    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ctx.getSharedPreferences("test-legacy-cleanup", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `removes bare pref_github_token`() {
        prefs.edit { putString("pref_github_token", "legacy-token") }

        LegacyTokenCleanup.purge(prefs)

        assertThat(prefs.getString("pref_github_token", null)).isNull()
    }

    @Test
    fun `removes widget-scoped variants`() {
        prefs.edit {
            putString("pref_github_token_1", "a")
            putString("pref_github_token_42", "b")
            putString("pref_api_url_1", "https://x")
        }

        LegacyTokenCleanup.purge(prefs)

        assertThat(prefs.getString("pref_github_token_1", null)).isNull()
        assertThat(prefs.getString("pref_github_token_42", null)).isNull()
        assertThat(prefs.getString("pref_api_url_1", null)).isEqualTo("https://x")
    }

    @Test
    fun `no-op when no legacy keys present`() {
        prefs.edit { putString("pref_api_url_1", "https://x") }

        LegacyTokenCleanup.purge(prefs)

        assertThat(prefs.getString("pref_api_url_1", null)).isEqualTo("https://x")
    }
}
