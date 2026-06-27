package com.example.downloadwidget

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "download_widget_prefs"
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<EditTextPreference>("pref_api_url")?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        findPreference<EditTextPreference>("pref_version")?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
    }
}
