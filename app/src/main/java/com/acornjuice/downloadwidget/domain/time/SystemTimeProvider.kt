package com.acornjuice.downloadwidget.domain.time

/**
 * Default [TimeProvider] backed by [System.currentTimeMillis].
 * Not thread-hostile: reads are atomic.
 */
object SystemTimeProvider : TimeProvider {
    override fun nowEpochSeconds(): Long = System.currentTimeMillis() / MILLIS_PER_SECOND

    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    private const val MILLIS_PER_SECOND = 1_000L
}
