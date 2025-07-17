package org.scriptonbasestar.kcexts.idp.naver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class NaverIdentityProviderFactoryTest {
    private lateinit var factory: NaverIdentityProviderFactory
    private lateinit var mockSession: KeycloakSession
    private lateinit var mockModel: IdentityProviderModel

    @BeforeEach
    fun setUp() {
        factory = NaverIdentityProviderFactory()
        mockSession = mock()
        mockModel = mock()
    }

    @Test
    fun `should return correct provider id`() {
        assertThat(factory.id).isEqualTo("naver")
    }

    @Test
    fun `should return correct provider name`() {
        assertThat(factory.name).isEqualTo("Naver")
    }

    @Test
    fun `should create NaverIdentityProvider instance`() {
        val provider = factory.create(mockSession, mockModel)

        assertThat(provider).isNotNull
        assertThat(provider).isInstanceOf(NaverIdentityProvider::class.java)
    }

    @Test
    fun `should create NaverIdentityProviderConfig`() {
        val config = factory.createConfig()

        assertThat(config).isNotNull
        assertThat(config).isInstanceOf(NaverIdentityProviderConfig::class.java)
        assertThat(config.alias).isEqualTo("naver")
    }

    @Test
    fun `should extend AbstractIdentityProviderFactory`() {
        assertThat(factory).isInstanceOf(org.keycloak.broker.provider.AbstractIdentityProviderFactory::class.java)
    }

    @Test
    fun `should implement SocialIdentityProviderFactory`() {
        assertThat(factory).isInstanceOf(org.keycloak.broker.social.SocialIdentityProviderFactory::class.java)
    }
}
