package com.acornjuice.downloadwidget.worker

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * The watchdog's fencing rule. It runs after the fact, from a persisted queue, so the only
 * thing standing between "rescues a stuck spinner" and "stomps on a healthy refresh" is this
 * predicate.
 */
@RunWith(JUnit4::class)
class RefreshWatchdogWorkerTest {

    @Test
    fun `intervenes when the refresh it was armed for is still pending`() {
        val shouldIntervene = RefreshWatchdogWorker.shouldIntervene(
            inFlightSince = 1_000L,
            armedFor = 1_000L,
        )

        assertThat(shouldIntervene).isTrue()
    }

    @Test
    fun `stands down when the refresh already finished`() {
        val shouldIntervene = RefreshWatchdogWorker.shouldIntervene(
            inFlightSince = null,
            armedFor = 1_000L,
        )

        assertThat(shouldIntervene).isFalse()
    }

    @Test
    fun `stands down when a newer tap superseded the one it was armed for`() {
        // Otherwise a watchdog firing late would replace a perfectly healthy in-progress
        // refresh with a bogus "NOT UPDATED" badge.
        val shouldIntervene = RefreshWatchdogWorker.shouldIntervene(
            inFlightSince = 5_000L,
            armedFor = 1_000L,
        )

        assertThat(shouldIntervene).isFalse()
    }
}
