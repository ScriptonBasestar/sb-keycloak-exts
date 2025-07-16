package org.scriptonbasestar.kcexts.idp.google

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel

class GoogleIdentityProviderConfigTest {
    @Test
    fun `default constructor should initialize with Google constants`() {
        val config = GoogleIdentityProviderConfig()

        assertThat(config.alias).isEqualTo(GoogleConstant.providerId)
        assertThat(config.authorizationUrl).isEqualTo(GoogleConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(GoogleConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(GoogleConstant.profileUrl)
        assertThat(config.defaultScope).isEqualTo(GoogleConstant.defaultScope)
    }

    @Test
    fun `model constructor should initialize with Google constants`() {
        val model = IdentityProviderModel()
        model.alias = "test-alias"
        
        val config = GoogleIdentityProviderConfig(model)

        assertThat(config.alias).isEqualTo(GoogleConstant.providerId)
        assertThat(config.authorizationUrl).isEqualTo(GoogleConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(GoogleConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(GoogleConstant.profileUrl)
        assertThat(config.defaultScope).isEqualTo(GoogleConstant.defaultScope)
    }
}