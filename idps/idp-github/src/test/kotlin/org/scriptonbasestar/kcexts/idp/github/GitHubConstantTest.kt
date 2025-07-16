package org.scriptonbasestar.kcexts.idp.github

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitHubConstantTest {
    @Test
    fun `providerId should be github`() {
        assertThat(GitHubConstant.providerId).isEqualTo("github")
    }

    @Test
    fun `providerName should be GitHub`() {
        assertThat(GitHubConstant.providerName).isEqualTo("GitHub")
    }

    @Test
    fun `authUrl should be valid GitHub OAuth2 authorization URL`() {
        assertThat(GitHubConstant.authUrl).isEqualTo("https://github.com/login/oauth/authorize")
    }

    @Test
    fun `tokenUrl should be valid GitHub OAuth2 token URL`() {
        assertThat(GitHubConstant.tokenUrl).isEqualTo("https://github.com/login/oauth/access_token")
    }

    @Test
    fun `profileUrl should be valid GitHub API user URL`() {
        assertThat(GitHubConstant.profileUrl).isEqualTo("https://api.github.com/user")
    }

    @Test
    fun `defaultScope should include required scopes`() {
        assertThat(GitHubConstant.defaultScope).isEqualTo("user:email")
    }
}