package org.scriptonbasestar.kcexts.idp.github

import org.keycloak.broker.provider.AbstractIdentityProviderFactory
import org.keycloak.broker.social.SocialIdentityProviderFactory
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession

class GitHubIdentityProviderFactory :
    AbstractIdentityProviderFactory<GitHubIdentityProvider>(),
    SocialIdentityProviderFactory<GitHubIdentityProvider> {

    override fun create(session: KeycloakSession, model: IdentityProviderModel): GitHubIdentityProvider {
        return GitHubIdentityProvider(session, GitHubIdentityProviderConfig(model))
    }

    override fun getId(): String = GitHubConstant.providerId

    override fun getName(): String = GitHubConstant.providerName

    override fun createConfig(): IdentityProviderModel = GitHubIdentityProviderConfig()
}