package com.acornjuice.downloadwidget.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SafeLoggerTest {

    @Test
    fun `passes unrelated strings through unchanged`() {
        assertThat(SafeLogger.redact("Fetching release v1.2.3 for widget 42"))
            .isEqualTo("Fetching release v1.2.3 for widget 42")
    }

    @Test
    fun `redacts Authorization Bearer header`() {
        val redacted = SafeLogger.redact("Authorization: Bearer ghp_abc123def456XYZ")
        assertThat(redacted).isEqualTo("Authorization: Bearer ***")
    }

    @Test
    fun `redacts standalone Bearer token`() {
        val redacted = SafeLogger.redact("Sent header value=Bearer ghp_secretsecretsecret")
        assertThat(redacted).contains("Bearer ***")
        assertThat(redacted).doesNotContain("ghp_secretsecretsecret")
    }

    @Test
    fun `redacts token key equals value`() {
        val redacted = SafeLogger.redact("Stored token=ghp_abc123def456XYZ_890 in prefs")
        assertThat(redacted).isEqualTo("Stored token=*** in prefs")
    }

    @Test
    fun `redacts github_token colon value`() {
        val redacted = SafeLogger.redact("pref[github_token: ghp_abc.def+xyz-123=] updated")
        assertThat(redacted).contains("github_token: ***")
        assertThat(redacted).doesNotContain("ghp_abc.def+xyz-123=")
    }

    @Test
    fun `redacts api_key form`() {
        val redacted = SafeLogger.redact("api_key=deadbeef_cafe")
        assertThat(redacted).isEqualTo("api_key=***")
    }

    @Test
    fun `redacts PAT keyword`() {
        val redacted = SafeLogger.redact("PAT: ghp_secretvalue123")
        assertThat(redacted).isEqualTo("PAT: ***")
    }

    @Test
    fun `handles multiple secrets in one line`() {
        val redacted = SafeLogger.redact(
            "Authorization: Bearer ghp_first; also token=ghp_second here",
        )
        assertThat(redacted).doesNotContain("ghp_first")
        assertThat(redacted).doesNotContain("ghp_second")
    }
}
