package com.acornjuice.downloadwidget.domain.time

/**
 * Abstraction over wall-clock time so units can be tested deterministically.
 *
 * Production code depends on this interface, not on [System.currentTimeMillis].
 */
fun interface TimeProvider {
    /** @return Unix epoch time in **seconds**. */
    fun nowEpochSeconds(): Long

    /**
     * @return Unix epoch time in **milliseconds**.
     *
     * Defaults to second precision so this stays a single-abstract-method interface (test
     * fakes keep working as SAM lambdas); [SystemTimeProvider] overrides it with true
     * millisecond precision, which the refresh fencing token relies on to tell two taps
     * within the same second apart.
     */
    fun nowEpochMillis(): Long = nowEpochSeconds() * MILLIS_PER_SECOND

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
