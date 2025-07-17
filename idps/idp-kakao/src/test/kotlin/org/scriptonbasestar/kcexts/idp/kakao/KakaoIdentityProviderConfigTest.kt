package org.scriptonbasestar.kcexts.idp.kakao

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KakaoIdentityProviderConfigTest {
    @Test
    fun `should create config with default constructor`() {
        val config = KakaoIdentityProviderConfig()

        assertThat(config.alias).isEqualTo("kakao")
        assertThat(config.authorizationUrl).isEqualTo(KakaoConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(KakaoConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(KakaoConstant.profileUrl)
    }

    @Test
    fun `should create config from IdentityProviderModel`() {
        val mockModel = mock<IdentityProviderModel>()
        whenever(mockModel.config).thenReturn(HashMap())

        val config = KakaoIdentityProviderConfig(mockModel)

        assertThat(config.alias).isEqualTo("kakao")
        assertThat(config.authorizationUrl).isEqualTo(KakaoConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(KakaoConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(KakaoConstant.profileUrl)
    }

    @Test
    fun `should preserve custom config from model`() {
        val mockModel = mock<IdentityProviderModel>()
        val customConfig = HashMap<String, String>()
        customConfig["clientId"] = "test-client-id"
        customConfig["clientSecret"] = "test-client-secret"
        whenever(mockModel.config).thenReturn(customConfig)

        val config = KakaoIdentityProviderConfig(mockModel)

        assertThat(config.clientId).isEqualTo("test-client-id")
        assertThat(config.clientSecret).isEqualTo("test-client-secret")
    }
}
