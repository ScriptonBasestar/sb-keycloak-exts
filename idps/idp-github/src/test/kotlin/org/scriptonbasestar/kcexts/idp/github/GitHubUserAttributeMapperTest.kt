package org.scriptonbasestar.kcexts.idp.github

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitHubUserAttributeMapperTest {
    private val mapper = GitHubUserAttributeMapper()

    @Test
    fun `getId should return correct mapper ID`() {
        assertThat(mapper.id).isEqualTo("github-user-attribute-mapper")
    }

    @Test
    fun `getCompatibleProviders should return GitHub provider ID`() {
        val compatibleProviders = mapper.compatibleProviders

        assertThat(compatibleProviders).containsExactly(GitHubConstant.providerId)
    }

    @Test
    fun `mapper should extend AbstractJsonUserAttributeMapper`() {
        assertThat(mapper).isInstanceOf(org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper::class.java)
    }
}