package com.acornjuice.downloadwidget.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Builds a [SecureTokenStore] backed by [EncryptedSharedPreferences] (AES-256-GCM values,
 * AES-256-SIV keys). The master key is stored in the Android Keystore.
 *
 * Kept separate from [SecureTokenStore] so unit tests can build the store with plain prefs
 * without pulling in the Android Keystore.
 */
object SecureTokenStoreFactory {

    fun create(context: Context): SecureTokenStore {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PrefKeys.SECURE_PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return SecureTokenStore(prefs)
    }
}
