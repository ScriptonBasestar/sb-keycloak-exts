package org.scriptonbasestar.kcexts.idp.google

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class GoogleIdentityProviderFactoryTest {
    private val factory = GoogleIdentityProviderFactory()

    @Test
    fun `create should return GoogleIdentityProvider instance`() {
        val session: KeycloakSession = mock()
        val model = IdentityProviderModel()
        model.alias = GoogleConstant.providerId

        val provider = factory.create(session, model)

        assertThat(provider).isInstanceOf(GoogleIdentityProvider::class.java)
    }

    @Test
    fun `getId should return correct provider ID`() {
        assertThat(factory.id).isEqualTo(GoogleConstant.providerId)
    }

    @Test
    fun `getName should return correct provider name`() {
        assertThat(factory.name).isEqualTo(GoogleConstant.providerName)
    }

    @Test
    fun `createConfig should return GoogleIdentityProviderConfig instance`() {
        val config = factory.createConfig()

        assertThat(config).isInstanceOf(GoogleIdentityProviderConfig::class.java)
    }
}