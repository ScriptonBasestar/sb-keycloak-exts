package org.scriptonbasestar.kcexts.idp.line

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LineConstantTest {
    @Test
    fun `should have correct provider id`() {
        assertThat(LineConstant.providerId).isEqualTo("line")
    }

    @Test
    fun `should have correct provider name`() {
        assertThat(LineConstant.providerName).isEqualTo("Line")
    }

    @Test
    fun `should have correct auth URL`() {
        assertThat(LineConstant.authUrl).isEqualTo("https://access.line.me/oauth2/v2.1/authorize")
    }

    @Test
    fun `should have correct token URL`() {
        assertThat(LineConstant.tokenUrl).isEqualTo("https://api.line.me/oauth2/v2.1/token")
    }

    @Test
    fun `should have correct profile URL`() {
        assertThat(LineConstant.profileUrl).isEqualTo("https://api.line.me/v2/profile")
    }

    @Test
    fun `should have correct default scope`() {
        assertThat(LineConstant.defaultScope).isEqualTo("profile openid email")
    }

    @Test
    fun `should have Line OAuth2 v2_1 endpoints`() {
        // Verify that we're using the correct Line OAuth2 v2.1 endpoints
        assertThat(LineConstant.authUrl).contains("oauth2/v2.1")
        assertThat(LineConstant.tokenUrl).contains("oauth2/v2.1")
    }
}
