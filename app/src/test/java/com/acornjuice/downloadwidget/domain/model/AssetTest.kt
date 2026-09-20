package com.acornjuice.downloadwidget.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AssetTest {

    @Test
    fun `accepts zero download count`() {
        val asset = Asset(name = "artifact.apk", downloadCount = 0)
        assertThat(asset.downloadCount).isEqualTo(0)
    }

    @Test
    fun `accepts positive download count`() {
        val asset = Asset(name = "artifact.apk", downloadCount = 12_345)
        assertThat(asset.downloadCount).isEqualTo(12_345)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative download count`() {
        Asset(name = "artifact.apk", downloadCount = -1)
    }
}
