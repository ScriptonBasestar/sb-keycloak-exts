package org.scriptonbasestar.kcexts.idp.google

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class GoogleIdentityProviderTest {
    private lateinit var mockSession: KeycloakSession
    private lateinit var config: GoogleIdentityProviderConfig
    private lateinit var provider: GoogleIdentityProvider

    @BeforeEach
    fun setUp() {
        mockSession = mock()
        config = GoogleIdentityProviderConfig()
        provider = GoogleIdentityProvider(mockSession, config)
    }

    @Test
    fun `should have correct configuration`() {
        assertThat(config.defaultScope).isEqualTo(GoogleConstant.defaultScope)
    }

    @Test
    fun `should be instance of AbstractOAuth2IdentityProvider`() {
        assertThat(provider).isInstanceOf(org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider::class.java)
    }

    @Test
    fun `should implement SocialIdentityProvider interface`() {
        assertThat(provider).isInstanceOf(org.keycloak.broker.social.SocialIdentityProvider::class.java)
    }

    @Test
    fun `should return correct default scopes`() {
        assertThat(config.defaultScope).isEqualTo(GoogleConstant.defaultScope)
    }

    @Test
    fun `should have correct authorization URL in config`() {
        assertThat(config.authorizationUrl).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth")
    }

    @Test
    fun `should have correct token URL in config`() {
        assertThat(config.tokenUrl).isEqualTo("https://oauth2.googleapis.com/token")
    }

    @Test
    fun `should have correct profile URL in config`() {
        assertThat(config.userInfoUrl).isEqualTo("https://www.googleapis.com/oauth2/v2/userinfo")
    }
}