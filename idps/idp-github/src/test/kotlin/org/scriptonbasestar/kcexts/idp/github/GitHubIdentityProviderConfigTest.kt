package org.scriptonbasestar.kcexts.idp.github

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel

class GitHubIdentityProviderConfigTest {
    @Test
    fun `default constructor should initialize with GitHub constants`() {
        val config = GitHubIdentityProviderConfig()

        assertThat(config.alias).isEqualTo(GitHubConstant.providerId)
        assertThat(config.authorizationUrl).isEqualTo(GitHubConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(GitHubConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(GitHubConstant.profileUrl)
        assertThat(config.defaultScope).isEqualTo(GitHubConstant.defaultScope)
    }

    @Test
    fun `model constructor should initialize with GitHub constants`() {
        val model = IdentityProviderModel()
        model.alias = "test-alias"

        val config = GitHubIdentityProviderConfig(model)

        assertThat(config.alias).isEqualTo(GitHubConstant.providerId)
        assertThat(config.authorizationUrl).isEqualTo(GitHubConstant.authUrl)
        assertThat(config.tokenUrl).isEqualTo(GitHubConstant.tokenUrl)
        assertThat(config.userInfoUrl).isEqualTo(GitHubConstant.profileUrl)
        assertThat(config.defaultScope).isEqualTo(GitHubConstant.defaultScope)
    }
}
