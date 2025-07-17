package org.scriptonbasestar.kcexts.idp.github

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.mock

class GitHubIdentityProviderFactoryTest {
    private val factory = GitHubIdentityProviderFactory()

    @Test
    fun `create should return GitHubIdentityProvider instance`() {
        val session: KeycloakSession = mock()
        val model = IdentityProviderModel()
        model.alias = GitHubConstant.providerId

        val provider = factory.create(session, model)

        assertThat(provider).isInstanceOf(GitHubIdentityProvider::class.java)
    }

    @Test
    fun `getId should return correct provider ID`() {
        assertThat(factory.id).isEqualTo(GitHubConstant.providerId)
    }

    @Test
    fun `getName should return correct provider name`() {
        assertThat(factory.name).isEqualTo(GitHubConstant.providerName)
    }

    @Test
    fun `createConfig should return GitHubIdentityProviderConfig instance`() {
        val config = factory.createConfig()

        assertThat(config).isInstanceOf(GitHubIdentityProviderConfig::class.java)
    }
}
