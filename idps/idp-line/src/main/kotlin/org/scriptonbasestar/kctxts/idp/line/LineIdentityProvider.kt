package org.scriptonbasestar.kctxts.idp.line

import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider
import org.keycloak.broker.social.SocialIdentityProvider
import org.keycloak.models.KeycloakSession

class LineIdentityProvider(
    keycloakSession: KeycloakSession,
    config: LineIdentityProviderConfig
) : AbstractOAuth2IdentityProvider<LineIdentityProviderConfig>(
    keycloakSession,
    config
),
    SocialIdentityProvider<LineIdentityProviderConfig> {
    override fun getDefaultScopes(): String = LineConstant.defaultScope
}
