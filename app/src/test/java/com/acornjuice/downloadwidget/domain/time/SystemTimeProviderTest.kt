package com.acornjuice.downloadwidget.domain.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SystemTimeProviderTest {

    @Test
    fun `returns seconds close to wall-clock`() {
        val before = System.currentTimeMillis() / 1_000
        val now = SystemTimeProvider.nowEpochSeconds()
        val after = System.currentTimeMillis() / 1_000
        assertThat(now).isAtLeast(before)
        assertThat(now).isAtMost(after)
    }
}
