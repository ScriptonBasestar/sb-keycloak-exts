package org.scriptonbasestar.kcexts.idp.github

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper

class GitHubUserAttributeMapper : AbstractJsonUserAttributeMapper() {
    override fun getId(): String = "github-user-attribute-mapper"

    override fun getCompatibleProviders(): Array<String> = arrayOf(GitHubConstant.providerId)
}