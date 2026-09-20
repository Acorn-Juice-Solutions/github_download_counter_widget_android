package com.acornjuice.downloadwidget.domain.time

/**
 * Abstraction over wall-clock time so units can be tested deterministically.
 *
 * Production code depends on this interface, not on [System.currentTimeMillis].
 */
fun interface TimeProvider {
    /** @return Unix epoch time in **seconds**. */
    fun nowEpochSeconds(): Long
}
