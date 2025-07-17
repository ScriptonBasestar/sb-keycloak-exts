package org.scriptonbasestar.kcexts.idp.naver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NaverIdentityProviderConfigTest {
    @Test
    fun `should create config with default constructor`() {
        val config = NaverIdentityProviderConfig()

        assertThat(config.alias).isEqualTo("naver")
        assertThat(config.authorizationUrl).isEqualTo(NaverConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(NaverConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(NaverConstant.profileUrl)
        assertThat(config.defaultScope).isEqualTo(NaverConstant.defaultScope)
    }

    @Test
    fun `should create config from IdentityProviderModel`() {
        val mockModel = mock<IdentityProviderModel>()
        whenever(mockModel.config).thenReturn(HashMap())

        val config = NaverIdentityProviderConfig(mockModel)

        assertThat(config.alias).isEqualTo("naver")
        assertThat(config.authorizationUrl).isEqualTo(NaverConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(NaverConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(NaverConstant.profileUrl)
        assertThat(config.defaultScope).isEqualTo(NaverConstant.defaultScope)
    }

    @Test
    fun `should preserve custom config from model`() {
        val mockModel = mock<IdentityProviderModel>()
        val customConfig = HashMap<String, String>()
        customConfig["clientId"] = "test-client-id"
        customConfig["clientSecret"] = "test-client-secret"
        whenever(mockModel.config).thenReturn(customConfig)

        val config = NaverIdentityProviderConfig(mockModel)

        assertThat(config.clientId).isEqualTo("test-client-id")
        assertThat(config.clientSecret).isEqualTo("test-client-secret")
    }

    @Test
    fun `should extend OAuth2IdentityProviderConfig`() {
        val config = NaverIdentityProviderConfig()
        assertThat(config).isInstanceOf(org.keycloak.broker.oidc.OAuth2IdentityProviderConfig::class.java)
    }
}
