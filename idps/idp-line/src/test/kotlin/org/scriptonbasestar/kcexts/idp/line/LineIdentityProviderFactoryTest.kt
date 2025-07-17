package org.scriptonbasestar.kcexts.idp.line

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class LineIdentityProviderFactoryTest {
    private lateinit var factory: LineIdentityProviderFactory
    private lateinit var mockSession: KeycloakSession
    private lateinit var mockModel: IdentityProviderModel

    @BeforeEach
    fun setUp() {
        factory = LineIdentityProviderFactory()
        mockSession = mock()
        mockModel = mock()
    }

    @Test
    fun `should return correct provider id`() {
        assertThat(factory.id).isEqualTo("line")
    }

    @Test
    fun `should return correct provider name`() {
        assertThat(factory.name).isEqualTo("Line")
    }

    @Test
    fun `should create LineIdentityProvider instance`() {
        val provider = factory.create(mockSession, mockModel)

        assertThat(provider).isNotNull
        assertThat(provider).isInstanceOf(LineIdentityProvider::class.java)
    }

    @Test
    fun `should create LineIdentityProviderConfig`() {
        val config = factory.createConfig()

        assertThat(config).isNotNull
        assertThat(config).isInstanceOf(LineIdentityProviderConfig::class.java)
        assertThat(config.alias).isEqualTo("line")
    }

    @Test
    fun `should implement SocialIdentityProviderFactory interface`() {
        assertThat(factory).isInstanceOf(org.keycloak.broker.social.SocialIdentityProviderFactory::class.java)
    }

    @Test
    fun `should extend AbstractIdentityProviderFactory`() {
        assertThat(factory).isInstanceOf(org.keycloak.broker.provider.AbstractIdentityProviderFactory::class.java)
    }
}
