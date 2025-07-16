package org.scriptonbasestar.kcexts.idp.google

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GoogleConstantTest {
    @Test
    fun `providerId should be google`() {
        assertThat(GoogleConstant.providerId).isEqualTo("google")
    }

    @Test
    fun `providerName should be Google`() {
        assertThat(GoogleConstant.providerName).isEqualTo("Google")
    }

    @Test
    fun `authUrl should be valid Google OAuth2 authorization URL`() {
        assertThat(GoogleConstant.authUrl).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth")
    }

    @Test
    fun `tokenUrl should be valid Google OAuth2 token URL`() {
        assertThat(GoogleConstant.tokenUrl).isEqualTo("https://oauth2.googleapis.com/token")
    }

    @Test
    fun `profileUrl should be valid Google userinfo URL`() {
        assertThat(GoogleConstant.profileUrl).isEqualTo("https://www.googleapis.com/oauth2/v2/userinfo")
    }

    @Test
    fun `defaultScope should include required scopes`() {
        assertThat(GoogleConstant.defaultScope).isEqualTo("openid email profile")
    }
}