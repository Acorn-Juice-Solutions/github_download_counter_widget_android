package com.acornjuice.downloadwidget.data.local

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * One-off cleanup of the pre-1.0.0 plaintext `pref_github_token` keys that used to live in
 * the plain preferences file (both bare and widget-scoped variants).
 *
 * Runs once per install of 1.0.0+; harmless to run repeatedly. Does NOT migrate the token
 * value into the encrypted store — the design document (§6.1) explicitly declines
 * migration and asks users to re-enter their PAT.
 */
object LegacyTokenCleanup {

    private const val LEGACY_KEY_PREFIX = "pref_github_token"

    /**
     * Removes any key in [plainPrefs] whose name is `pref_github_token` or `pref_github_token_*`.
     * Reads the entire pref map once, filters, and applies a single [SharedPreferences.Editor] batch.
     */
    fun purge(plainPrefs: SharedPreferences) {
        val toRemove = plainPrefs.all.keys.filter { key ->
            key == LEGACY_KEY_PREFIX || key.startsWith("${LEGACY_KEY_PREFIX}_")
        }
        if (toRemove.isEmpty()) return
        plainPrefs.edit {
            for (key in toRemove) remove(key)
        }
    }
}
