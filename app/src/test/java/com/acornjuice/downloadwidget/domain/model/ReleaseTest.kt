package com.acornjuice.downloadwidget.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ReleaseTest {

    @Test
    fun `totalDownloads is zero for empty assets`() {
        val release = Release(tag = "v1.0.0", assets = emptyList(), fetchedAtEpochSeconds = 0L)
        assertThat(release.totalDownloads).isEqualTo(0)
    }

    @Test
    fun `totalDownloads sums all asset counts`() {
        val release = Release(
            tag = "v1.0.0",
            assets = listOf(
                Asset("a.apk", 100),
                Asset("b.aab", 250),
                Asset("c.zip", 3),
            ),
            fetchedAtEpochSeconds = 1_700_000_000L,
        )
        assertThat(release.totalDownloads).isEqualTo(353)
    }
}
