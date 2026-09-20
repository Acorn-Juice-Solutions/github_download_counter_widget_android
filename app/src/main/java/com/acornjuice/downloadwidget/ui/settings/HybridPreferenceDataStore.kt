package com.acornjuice.downloadwidget.ui.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceDataStore
import com.acornjuice.downloadwidget.data.local.PrefKeys
import com.acornjuice.downloadwidget.data.local.SecureTokenStore

/**
 * Routes the [PrefKeys.KEY_TOKEN] preference to the encrypted [SecureTokenStore] while
 * every other preference stays in the plain [SharedPreferences].
 *
 * `androidx.preference.PreferenceFragmentCompat` writes through its `preferenceDataStore`
 * whenever one is set, so this class is the single choke point that keeps the PAT out of
 * the plain prefs file.
 */
class HybridPreferenceDataStore(
    private val plain: SharedPreferences,
    private val secureToken: SecureTokenStore,
) : PreferenceDataStore() {

    override fun getString(key: String, defValue: String?): String? = when {
        key == PrefKeys.KEY_TOKEN -> secureToken.get() ?: defValue
        else -> plain.getString(key, defValue)
    }

    override fun putString(key: String, value: String?) {
        // Trim to sanitize clipboard pastes: a trailing space or newline in an API URL /
        // release tag turns into `%20`/`%0A` on the wire and GitHub answers 404.
        val cleaned = value?.trim()
        if (key == PrefKeys.KEY_TOKEN) {
            secureToken.set(cleaned.orEmpty())
        } else {
            plain.edit { putString(key, cleaned) }
        }
    }
}
