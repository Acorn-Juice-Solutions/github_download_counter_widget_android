package com.acornjuice.downloadwidget.data.remote

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ReleaseParserTest {

    private val fetchedAt = 1_726_000_000L

    @Test
    fun `parses full release payload`() {
        val json =
            """
            {
              "tag_name": "v1.2.3",
              "name": "Version 1.2.3",
              "assets": [
                {"name": "app.apk", "download_count": 100, "size": 4567},
                {"name": "sources.zip", "download_count": 42, "size": 890}
              ]
            }
            """.trimIndent()

        val release = ReleaseParser.parse(json, fetchedAt)

        assertThat(release.tag).isEqualTo("v1.2.3")
        assertThat(release.fetchedAtEpochSeconds).isEqualTo(fetchedAt)
        assertThat(release.assets).hasSize(2)
        assertThat(release.assets[0].name).isEqualTo("app.apk")
        assertThat(release.assets[0].downloadCount).isEqualTo(100)
        assertThat(release.assets[1].name).isEqualTo("sources.zip")
        assertThat(release.assets[1].downloadCount).isEqualTo(42)
        assertThat(release.totalDownloads).isEqualTo(142)
    }

    @Test
    fun `handles release with empty assets array`() {
        val json = """{"tag_name": "v0.1.0", "assets": []}"""

        val release = ReleaseParser.parse(json, fetchedAt)

        assertThat(release.tag).isEqualTo("v0.1.0")
        assertThat(release.assets).isEmpty()
        assertThat(release.totalDownloads).isEqualTo(0)
    }

    @Test
    fun `handles release with no assets key`() {
        val json = """{"tag_name": "v0.1.0"}"""

        val release = ReleaseParser.parse(json, fetchedAt)

        assertThat(release.assets).isEmpty()
    }

    @Test
    fun `defaults missing asset name to placeholder`() {
        val json =
            """
            {
              "tag_name": "v0.1.0",
              "assets": [{"download_count": 3}]
            }
            """.trimIndent()

        val release = ReleaseParser.parse(json, fetchedAt)

        assertThat(release.assets).hasSize(1)
        assertThat(release.assets[0].name).isEqualTo("asset")
        assertThat(release.assets[0].downloadCount).isEqualTo(3)
    }

    @Test
    fun `defaults missing download_count to zero`() {
        val json =
            """
            {
              "tag_name": "v0.1.0",
              "assets": [{"name": "readme.md"}]
            }
            """.trimIndent()

        val release = ReleaseParser.parse(json, fetchedAt)

        assertThat(release.assets[0].downloadCount).isEqualTo(0)
    }

    @Test
    fun `clamps negative download_count to zero`() {
        val json =
            """
            {
              "tag_name": "v0.1.0",
              "assets": [{"name": "broken.zip", "download_count": -5}]
            }
            """.trimIndent()

        val release = ReleaseParser.parse(json, fetchedAt)

        assertThat(release.assets[0].downloadCount).isEqualTo(0)
    }

    @Test(expected = JSONException::class)
    fun `throws on malformed JSON`() {
        ReleaseParser.parse("{not-json", fetchedAt)
    }

    @Test(expected = JSONException::class)
    fun `throws when tag_name is missing`() {
        ReleaseParser.parse("""{"assets": []}""", fetchedAt)
    }

    @Test(expected = JSONException::class)
    fun `throws when tag_name is blank`() {
        ReleaseParser.parse("""{"tag_name": "", "assets": []}""", fetchedAt)
    }

    @Test
    fun `skips non-object entries inside assets array`() {
        val json =
            """
            {
              "tag_name": "v0.1.0",
              "assets": [null, "oops", {"name": "real.apk", "download_count": 1}]
            }
            """.trimIndent()

        val release = ReleaseParser.parse(json, fetchedAt)

        assertThat(release.assets).hasSize(1)
        assertThat(release.assets[0].name).isEqualTo("real.apk")
    }
}
