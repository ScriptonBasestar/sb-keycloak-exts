package org.scriptonbasestar.kcexts.idp.naver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NaverConstantTest {
    @Test
    fun `should have correct provider id`() {
        assertThat(NaverConstant.providerId).isEqualTo("naver")
    }

    @Test
    fun `should have correct provider name`() {
        assertThat(NaverConstant.providerName).isEqualTo("Naver")
    }

    @Test
    fun `should have correct auth url`() {
        assertThat(NaverConstant.authUrl).isEqualTo("https://nid.naver.com/oauth2.0/authorize")
    }

    @Test
    fun `should have correct token url`() {
        assertThat(NaverConstant.tokenUrl).isEqualTo("https://nid.naver.com/oauth2.0/token")
    }

    @Test
    fun `should have correct profile url`() {
        assertThat(NaverConstant.profileUrl).isEqualTo("https://openapi.naver.com/v1/nid/me")
    }

    @Test
    fun `should have correct default scope`() {
        assertThat(NaverConstant.defaultScope).isEqualTo("profile email")
    }
}
