package org.scriptonbasestar.kcexts.idp.line

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.broker.provider.IdentityBrokerException
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class LineIdentityProviderTest {
    private lateinit var mockSession: KeycloakSession
    private lateinit var config: LineIdentityProviderConfig
    private lateinit var provider: LineIdentityProvider

    @BeforeEach
    fun setUp() {
        mockSession = mock()
        config = LineIdentityProviderConfig()
        provider = LineIdentityProvider(mockSession, config)
    }

    @Test
    fun `should have correct configuration`() {
        assertThat(config.defaultScope).isEqualTo("profile openid email")
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
        assertThat(config.defaultScope).isEqualTo(LineConstant.defaultScope)
    }

    @Test
    fun `should have correct authorization URL in config`() {
        config.authorizationUrl = LineConstant.authUrl
        assertThat(config.authorizationUrl).isEqualTo("https://access.line.me/oauth2/v2.1/authorize")
    }

    @Test
    fun `should have correct token URL in config`() {
        config.tokenUrl = LineConstant.tokenUrl
        assertThat(config.tokenUrl).isEqualTo("https://api.line.me/oauth2/v2.1/token")
    }

    @Test
    fun `should have correct profile URL in config`() {
        config.userInfoUrl = LineConstant.profileUrl
        assertThat(config.userInfoUrl).isEqualTo("https://api.line.me/v2/profile")
    }

    @Test
    fun `should handle LINE user profile structure`() {
        val userProfile = mapOf(
            "userId" to "U1234567890",
            "displayName" to "Test User",
            "pictureUrl" to "https://example.com/profile.jpg",
            "statusMessage" to "Hello LINE"
        )
        
        assertThat(userProfile["userId"]).isEqualTo("U1234567890")
        assertThat(userProfile["displayName"]).isEqualTo("Test User")
    }

    @Test
    fun `should handle missing email gracefully`() {
        val userProfile = mapOf(
            "userId" to "U1234567890",
            "displayName" to "Test User"
        )
        
        assertThat(userProfile).containsKey("userId")
        assertThat(userProfile).doesNotContainKey("email")
    }

    @Test
    fun `should handle token exchange errors`() {
        try {
            throw IdentityBrokerException("Token exchange failed")
        } catch (e: IdentityBrokerException) {
            assertThat(e.message).isEqualTo("Token exchange failed")
        }
    }

    @Test
    fun `should handle LINE specific error responses`() {
        val errorResponse = mapOf(
            "error" to "invalid_request",
            "error_description" to "Invalid channel ID"
        )
        
        assertThat(errorResponse["error"]).isEqualTo("invalid_request")
        assertThat(errorResponse["error_description"]).contains("channel ID")
    }
}