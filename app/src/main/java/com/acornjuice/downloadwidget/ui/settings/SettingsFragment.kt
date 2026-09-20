package com.acornjuice.downloadwidget.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.acornjuice.downloadwidget.R
import com.acornjuice.downloadwidget.appContainer
import com.acornjuice.downloadwidget.data.local.PrefKeys
import com.acornjuice.downloadwidget.ui.widget.WidgetKind
import com.acornjuice.downloadwidget.util.SafeLogger

/**
 * Per-widget configuration screen.
 *
 * Route:
 * - `MainActivity` receives an intent extra `EXTRA_APPWIDGET_ID` and instantiates this
 *   fragment via [newInstance].
 * - The fragment scopes the URL and tag preference keys with the widget id, wires token
 *   writes to the encrypted store via [HybridPreferenceDataStore], and broadcasts an
 *   `ACTION_APPWIDGET_UPDATE` any time a value changes so the widget refreshes right away.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        val container = ctx.appContainer
        appWidgetId = arguments?.getInt(ARG_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        preferenceManager.sharedPreferencesName = PrefKeys.PLAIN_PREFS_FILENAME
        preferenceManager.preferenceDataStore = HybridPreferenceDataStore(
            plain = ctx.getSharedPreferences(PrefKeys.PLAIN_PREFS_FILENAME, Context.MODE_PRIVATE),
            secureToken = container.secureTokenStore,
        )

        setPreferencesFromResource(R.xml.preferences, rootKey)

        val apiPref = findPreference<EditTextPreference>(PrefKeys.KEY_API_URL)
        val tagPref = findPreference<EditTextPreference>(PrefKeys.KEY_TAG)
        val tokenPref = findPreference<EditTextPreference>(PrefKeys.KEY_TOKEN)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val plainPrefs = ctx.getSharedPreferences(PrefKeys.PLAIN_PREFS_FILENAME, Context.MODE_PRIVATE)
            apiPref?.let {
                rebindToScopedKey(it, PrefKeys.scoped(PrefKeys.KEY_API_URL, appWidgetId), plainPrefs)
            }
            tagPref?.let {
                rebindToScopedKey(it, PrefKeys.scoped(PrefKeys.KEY_TAG, appWidgetId), plainPrefs)
            }
        }
        // Token key stays unscoped — one PAT is shared across all widgets by design.

        apiPref?.applyHint(hint = R.string.settings_api_url_hint, inputType = InputType.TYPE_TEXT_VARIATION_URI)
        tagPref?.applyHint(hint = R.string.settings_tag_hint, inputType = InputType.TYPE_CLASS_TEXT)
        tokenPref?.applyHint(hint = 0, inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD)

        val refreshOnChange = Preference.OnPreferenceChangeListener { _, _ ->
            triggerRefresh()
            true
        }
        apiPref?.onPreferenceChangeListener = refreshOnChange
        tagPref?.onPreferenceChangeListener = refreshOnChange
        tokenPref?.onPreferenceChangeListener = refreshOnChange
    }

    private fun EditTextPreference.applyHint(hint: Int, inputType: Int) {
        setOnBindEditTextListener { editText ->
            editText.inputType = inputType
            if (hint != 0) editText.hint = getString(hint)
        }
        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
    }

    /**
     * `setPreferencesFromResource` initializes each preference's cached `mText` using the
     * XML key (unscoped). We then rewrite the key to the widget-scoped variant, but the
     * cached value stays pointing at the unscoped read. If the scoped key has a different
     * (persisted) value, the UI would show the stale unscoped one until the user opened
     * the edit dialog. Force a re-read from the scoped key here.
     */
    private fun rebindToScopedKey(
        preference: EditTextPreference,
        scopedKey: String,
        plainPrefs: android.content.SharedPreferences,
    ) {
        preference.key = scopedKey
        preference.text = plainPrefs.getString(scopedKey, "").orEmpty()
    }

    /**
     * Fires an ACTION_APPWIDGET_UPDATE broadcast so the widget re-runs `onUpdate` and
     * picks up the new preference.
     *
     * Two paths:
     * - Opened from a specific widget's settings icon → only that widget's `appWidgetId`
     *   receives the broadcast (its scoped key was the one that changed).
     * - Opened from the app launcher (no `EXTRA_APPWIDGET_ID`) → broadcast to every
     *   widget of every [WidgetKind]. The unscoped keys just changed and every widget's
     *   [WidgetConfigStore.get] falls back to them, so all should refresh.
     *
     * Posted to the next main-thread turn so the preference framework has time to
     * persist the new value (the change listener fires *before* persistence).
     *
     * Uses `sendBroadcast` instead of WorkManager because on this app's target
     * launchers, `updateAppWidget` calls coming from a WorkManager worker are silently
     * dropped, while those from a legitimate `onReceive` are respected.
     */
    private fun triggerRefresh() {
        val ctx = context?.applicationContext ?: return
        val manager = AppWidgetManager.getInstance(ctx)

        val targets: List<Pair<Class<*>, IntArray>> =
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val info = manager.getAppWidgetInfo(appWidgetId) ?: return
                val kind = WidgetKind.fromProviderClassName(info.provider.className)
                if (kind == null) {
                    SafeLogger.w(TAG, "Unknown widget provider: ${info.provider.className}")
                    return
                }
                listOf(kind.providerClass to intArrayOf(appWidgetId))
            } else {
                WidgetKind.entries.mapNotNull { kind ->
                    val ids = manager.getAppWidgetIds(ComponentName(ctx, kind.providerClass))
                    if (ids.isEmpty()) null else kind.providerClass to ids
                }
            }
        if (targets.isEmpty()) return

        Handler(Looper.getMainLooper()).post {
            for ((providerClass, ids) in targets) {
                val intent = Intent(ctx, providerClass).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                ctx.sendBroadcast(intent)
            }
        }
    }

    companion object {
        private const val ARG_APPWIDGET_ID = "arg_appwidget_id"
        private const val TAG = "SettingsFragment"

        fun newInstance(appWidgetId: Int): SettingsFragment {
            return SettingsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_APPWIDGET_ID, appWidgetId)
                }
            }
        }
    }
}
