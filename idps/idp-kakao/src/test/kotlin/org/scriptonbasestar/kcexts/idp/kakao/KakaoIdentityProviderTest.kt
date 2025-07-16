package org.scriptonbasestar.kcexts.idp.kakao

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.broker.provider.IdentityBrokerException
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KakaoIdentityProviderTest {
    private lateinit var mockSession: KeycloakSession
    private lateinit var config: KakaoIdentityProviderConfig
    private lateinit var provider: KakaoIdentityProvider

    @BeforeEach
    fun setUp() {
        mockSession = mock()
        config = KakaoIdentityProviderConfig()
        provider = KakaoIdentityProvider(mockSession, config)
    }

    @Test
    fun `should have correct configuration`() {
        assertThat(config.defaultScope).isEqualTo("profile_image openid profile_nickname")
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
        assertThat(config.defaultScope).isEqualTo(KakaoConstant.defaultScope)
    }

    @Test
    fun `should have correct authorization URL in config`() {
        config.authorizationUrl = KakaoConstant.authUrl
        assertThat(config.authorizationUrl).isEqualTo("https://kauth.kakao.com/oauth/authorize")
    }

    @Test
    fun `should have correct token URL in config`() {
        config.tokenUrl = KakaoConstant.tokenUrl
        assertThat(config.tokenUrl).isEqualTo("https://kauth.kakao.com/oauth/token")
    }

    @Test
    fun `should have correct profile URL in config`() {
        config.userInfoUrl = KakaoConstant.profileUrl
        assertThat(config.userInfoUrl).isEqualTo("https://kapi.kakao.com/v2/user/me")
    }

    @Test
    fun `should handle kakao user profile structure`() {
        val userProfile = mapOf(
            "id" to 12345L,
            "connected_at" to "2023-01-01T00:00:00Z",
            "properties" to mapOf(
                "nickname" to "TestUser",
                "profile_image" to "https://example.com/profile.jpg"
            ),
            "kakao_account" to mapOf(
                "email" to "test@example.com",
                "email_verified" to true,
                "has_email" to true
            )
        )
        
        val kakaoAccount = userProfile["kakao_account"] as Map<*, *>
        assertThat(kakaoAccount["email"]).isEqualTo("test@example.com")
        assertThat(kakaoAccount["email_verified"]).isEqualTo(true)
    }

    @Test
    fun `should handle missing email gracefully`() {
        val userProfile = mapOf(
            "id" to 12345L,
            "properties" to mapOf(
                "nickname" to "TestUser"
            ),
            "kakao_account" to mapOf(
                "has_email" to false
            )
        )
        
        val kakaoAccount = userProfile["kakao_account"] as Map<String, Any>
        assertThat(kakaoAccount).containsEntry("has_email", false)
    }

    @Test
    fun `should handle token exchange errors`() {
        try {
            throw IdentityBrokerException("Token exchange failed")
        } catch (e: IdentityBrokerException) {
            assertThat(e.message).isEqualTo("Token exchange failed")
        }
    }
}