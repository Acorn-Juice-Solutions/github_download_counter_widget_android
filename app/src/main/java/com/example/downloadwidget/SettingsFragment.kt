package com.example.downloadwidget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private const val ARG_APPWIDGET_ID = "arg_appwidget_id"

        fun newInstance(appWidgetId: Int): SettingsFragment {
            return SettingsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_APPWIDGET_ID, appWidgetId)
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "download_widget_prefs"
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val appWidgetId = arguments?.getInt(ARG_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val apiPref = findPreference<EditTextPreference>("pref_api_url")
        val versionPref = findPreference<EditTextPreference>("pref_version")

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            apiPref?.key = "pref_api_url_$appWidgetId"
            versionPref?.key = "pref_version_$appWidgetId"
        }

        apiPref?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        versionPref?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
    }
}
