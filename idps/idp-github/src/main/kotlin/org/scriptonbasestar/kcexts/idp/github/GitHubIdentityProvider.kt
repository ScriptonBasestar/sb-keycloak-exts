package org.scriptonbasestar.kcexts.idp.github

import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider
import org.keycloak.broker.social.SocialIdentityProvider
import org.keycloak.models.KeycloakSession

class GitHubIdentityProvider(
    keycloakSession: KeycloakSession,
    config: GitHubIdentityProviderConfig,
) : AbstractOAuth2IdentityProvider<GitHubIdentityProviderConfig>(
        keycloakSession,
        config,
    ),
    SocialIdentityProvider<GitHubIdentityProviderConfig> {
    override fun getDefaultScopes(): String = GitHubConstant.defaultScope
}