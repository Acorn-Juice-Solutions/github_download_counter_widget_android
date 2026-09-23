package com.acornjuice.downloadwidget.data.local

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Tracks, per widget, whether a user-triggered refresh is still in flight.
 *
 * This is what makes the widget's `Loading` state recoverable. `Loading` is rendered
 * synchronously on the refresh tap, but the render that *leaves* it happens later, from a
 * coroutine the OS is free to kill — for the whole duration the widget's process is a
 * background process with no foreground component. Persisting an "in flight since" stamp
 * lets [com.acornjuice.downloadwidget.worker.RefreshWatchdogWorker] spot a refresh that
 * never finished and force a terminal render, instead of leaving the spinner turning
 * forever.
 *
 * The stamp doubles as a **fencing token**: a watchdog is armed for one specific stamp and
 * stands down unless that exact stamp is still in place, so a late watchdog can never
 * clobber a newer, healthy refresh.
 *
 * Writes use `commit()` rather than `apply()` on purpose. `apply()` only schedules the
 * write, and the scenario this store exists to survive — the process dying mid-refresh — is
 * exactly the one where that write would never reach disk.
 */
class RefreshStateStore(private val prefs: SharedPreferences) {

    /** Records that a refresh for [widgetId] started at [startedAtEpochMillis]. */
    fun markInFlight(widgetId: Int, startedAtEpochMillis: Long) {
        prefs.edit(commit = true) {
            putLong(PrefKeys.scoped(PrefKeys.KEY_REFRESH_STARTED_AT, widgetId), startedAtEpochMillis)
        }
    }

    /**
     * @return the epoch-millis stamp of the in-flight refresh for [widgetId], or `null` when
     *  no refresh is pending.
     */
    fun inFlightSince(widgetId: Int): Long? =
        prefs.getLong(PrefKeys.scoped(PrefKeys.KEY_REFRESH_STARTED_AT, widgetId), NOT_IN_FLIGHT)
            .takeIf { it != NOT_IN_FLIGHT }

    /** Clears the marker. Called from every terminal render that ends a tap refresh. */
    fun clear(widgetId: Int) {
        prefs.edit(commit = true) {
            remove(PrefKeys.scoped(PrefKeys.KEY_REFRESH_STARTED_AT, widgetId))
        }
    }

    private companion object {
        const val NOT_IN_FLIGHT = 0L
    }
}
