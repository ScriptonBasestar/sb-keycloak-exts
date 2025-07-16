package org.scriptonbasestar.kcexts.idp.google

import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider
import org.keycloak.broker.social.SocialIdentityProvider
import org.keycloak.models.KeycloakSession

class GoogleIdentityProvider(
    keycloakSession: KeycloakSession,
    config: GoogleIdentityProviderConfig,
) : AbstractOAuth2IdentityProvider<GoogleIdentityProviderConfig>(
        keycloakSession,
        config,
    ),
    SocialIdentityProvider<GoogleIdentityProviderConfig> {
    override fun getDefaultScopes(): String = GoogleConstant.defaultScope
}