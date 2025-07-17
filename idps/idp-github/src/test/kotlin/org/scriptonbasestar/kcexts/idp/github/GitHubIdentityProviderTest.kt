package org.scriptonbasestar.kcexts.idp.github

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class GitHubIdentityProviderTest {
    private lateinit var mockSession: KeycloakSession
    private lateinit var config: GitHubIdentityProviderConfig
    private lateinit var provider: GitHubIdentityProvider

    @BeforeEach
    fun setUp() {
        mockSession = mock()
        config = GitHubIdentityProviderConfig()
        provider = GitHubIdentityProvider(mockSession, config)
    }

    @Test
    fun `should have correct configuration`() {
        assertThat(config.defaultScope).isEqualTo(GitHubConstant.defaultScope)
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
        assertThat(config.defaultScope).isEqualTo(GitHubConstant.defaultScope)
    }

    @Test
    fun `should have correct authorization URL in config`() {
        assertThat(config.authorizationUrl).isEqualTo("https://github.com/login/oauth/authorize")
    }

    @Test
    fun `should have correct token URL in config`() {
        assertThat(config.tokenUrl).isEqualTo("https://github.com/login/oauth/access_token")
    }

    @Test
    fun `should have correct profile URL in config`() {
        assertThat(config.userInfoUrl).isEqualTo("https://api.github.com/user")
    }
}
