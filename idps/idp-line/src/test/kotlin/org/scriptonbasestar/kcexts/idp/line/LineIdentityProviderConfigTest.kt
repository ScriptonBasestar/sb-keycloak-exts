package org.scriptonbasestar.kcexts.idp.line

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LineIdentityProviderConfigTest {
    @Test
    fun `should create config with default constructor`() {
        val config = LineIdentityProviderConfig()

        assertThat(config.alias).isEqualTo("line")
        assertThat(config.authorizationUrl).isEqualTo(LineConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(LineConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(LineConstant.profileUrl)
        assertThat(config.defaultScope).isEqualTo(LineConstant.defaultScope)
    }

    @Test
    fun `should create config from IdentityProviderModel`() {
        val mockModel = mock<IdentityProviderModel>()
        whenever(mockModel.config).thenReturn(HashMap())

        val config = LineIdentityProviderConfig(mockModel)

        assertThat(config.alias).isEqualTo("line")
        assertThat(config.authorizationUrl).isEqualTo(LineConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(LineConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(LineConstant.profileUrl)
        assertThat(config.defaultScope).isEqualTo(LineConstant.defaultScope)
    }

    @Test
    fun `should preserve custom config from model`() {
        val mockModel = mock<IdentityProviderModel>()
        val customConfig = HashMap<String, String>()
        customConfig["clientId"] = "test-client-id"
        customConfig["clientSecret"] = "test-client-secret"
        whenever(mockModel.config).thenReturn(customConfig)

        val config = LineIdentityProviderConfig(mockModel)

        assertThat(config.clientId).isEqualTo("test-client-id")
        assertThat(config.clientSecret).isEqualTo("test-client-secret")
    }

    @Test
    fun `should have correct Line OAuth URLs`() {
        val config = LineIdentityProviderConfig()

        assertThat(config.authorizationUrl).isEqualTo("https://access.line.me/oauth2/v2.1/authorize")
        assertThat(config.tokenUrl).isEqualTo("https://api.line.me/oauth2/v2.1/token")
        assertThat(config.userInfoUrl).isEqualTo("https://api.line.me/v2/profile")
    }
}
