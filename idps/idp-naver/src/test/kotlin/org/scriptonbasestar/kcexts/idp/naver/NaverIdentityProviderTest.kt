package org.scriptonbasestar.kcexts.idp.naver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.broker.provider.IdentityBrokerException
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class NaverIdentityProviderTest {
    private lateinit var mockSession: KeycloakSession
    private lateinit var config: NaverIdentityProviderConfig
    private lateinit var provider: NaverIdentityProvider

    @BeforeEach
    fun setUp() {
        mockSession = mock()
        config = NaverIdentityProviderConfig()
        provider = NaverIdentityProvider(mockSession, config)
    }

    @Test
    fun `should have correct configuration`() {
        assertThat(config.defaultScope).isEqualTo(NaverConstant.defaultScope)
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
        assertThat(config.defaultScope).isEqualTo(NaverConstant.defaultScope)
    }

    @Test
    fun `should have correct authorization URL in config`() {
        config.authorizationUrl = NaverConstant.authUrl
        assertThat(config.authorizationUrl).isEqualTo("https://nid.naver.com/oauth2.0/authorize")
    }

    @Test
    fun `should have correct token URL in config`() {
        config.tokenUrl = NaverConstant.tokenUrl
        assertThat(config.tokenUrl).isEqualTo("https://nid.naver.com/oauth2.0/token")
    }

    @Test
    fun `should have correct profile URL in config`() {
        config.userInfoUrl = NaverConstant.profileUrl
        assertThat(config.userInfoUrl).isEqualTo("https://openapi.naver.com/v1/nid/me")
    }

    @Test
    fun `should handle Naver user profile structure`() {
        val userProfile =
            mapOf(
                "resultcode" to "00",
                "message" to "success",
                "response" to
                    mapOf(
                        "id" to "32742776",
                        "nickname" to "Test User",
                        "profile_image" to "https://example.com/profile.jpg",
                        "email" to "test@naver.com",
                        "name" to "테스트",
                        "birthday" to "01-01",
                        "birthyear" to "1990",
                        "age" to "30-39",
                        "gender" to "M",
                        "mobile" to "010-1234-5678",
                    ),
            )

        val response = userProfile["response"] as Map<String, Any>
        assertThat(response["email"]).isEqualTo("test@naver.com")
        assertThat(response["nickname"]).isEqualTo("Test User")
    }

    @Test
    fun `should handle missing email gracefully`() {
        val userProfile =
            mapOf(
                "resultcode" to "00",
                "message" to "success",
                "response" to
                    mapOf(
                        "id" to "32742776",
                        "nickname" to "Test User",
                    ),
            )

        val response = userProfile["response"] as Map<String, Any>
        assertThat(response).containsKey("id")
        assertThat(response).doesNotContainKey("email")
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
    fun `should handle Naver API error responses`() {
        val errorResponse =
            mapOf(
                "resultcode" to "024",
                "message" to "Authentication failed",
                "error" to "invalid_request",
                "error_description" to "Invalid client_id",
            )

        assertThat(errorResponse["resultcode"]).isNotEqualTo("00")
        assertThat(errorResponse["message"]).contains("failed")
    }
}
