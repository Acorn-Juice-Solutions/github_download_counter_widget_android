package com.acornjuice.downloadwidget.data.local

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Reads and writes the single global GitHub PAT.
 *
 * Storage-agnostic on purpose: the constructor accepts any [SharedPreferences], so tests
 * can inject a plain one while production wiring passes an [androidx.security.crypto.EncryptedSharedPreferences]
 * built by [SecureTokenStoreFactory].
 *
 * The class treats blank/whitespace input as absent — callers never have to check both
 * `null` and `""`.
 */
class SecureTokenStore(private val prefs: SharedPreferences) {

    /** @return The token, or `null` if unset or blank. */
    fun get(): String? = prefs.getString(PrefKeys.KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** Stores [token]; blank input clears the store. */
    fun set(token: String) {
        val trimmed = token.trim()
        prefs.edit {
            if (trimmed.isEmpty()) remove(PrefKeys.KEY_TOKEN) else putString(PrefKeys.KEY_TOKEN, trimmed)
        }
    }

    fun clear() {
        prefs.edit { remove(PrefKeys.KEY_TOKEN) }
    }
}
