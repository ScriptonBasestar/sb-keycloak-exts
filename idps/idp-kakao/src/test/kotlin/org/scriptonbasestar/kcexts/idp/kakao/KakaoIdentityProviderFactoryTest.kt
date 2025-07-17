package org.scriptonbasestar.kcexts.idp.kakao

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class KakaoIdentityProviderFactoryTest {
    private lateinit var factory: KakaoIdentityProviderFactory
    private lateinit var mockSession: KeycloakSession
    private lateinit var mockModel: IdentityProviderModel

    @BeforeEach
    fun setUp() {
        factory = KakaoIdentityProviderFactory()
        mockSession = mock()
        mockModel = mock()
    }

    @Test
    fun `should return correct provider id`() {
        assertThat(factory.id).isEqualTo("kakao")
    }

    @Test
    fun `should return correct provider name`() {
        assertThat(factory.name).isEqualTo("Kakao")
    }

    @Test
    fun `should create KakaoIdentityProvider instance`() {
        val provider = factory.create(mockSession, mockModel)

        assertThat(provider).isNotNull
        assertThat(provider).isInstanceOf(KakaoIdentityProvider::class.java)
    }

    @Test
    fun `should create KakaoIdentityProviderConfig`() {
        val config = factory.createConfig()

        assertThat(config).isNotNull
        assertThat(config).isInstanceOf(KakaoIdentityProviderConfig::class.java)
        assertThat(config.alias).isEqualTo("kakao")
    }
}
