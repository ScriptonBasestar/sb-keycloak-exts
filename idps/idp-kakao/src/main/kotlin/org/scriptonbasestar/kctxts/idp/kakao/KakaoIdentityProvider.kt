package org.scriptonbasestar.kctxts.idp.kakao

import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider
import org.keycloak.broker.social.SocialIdentityProvider
import org.keycloak.models.KeycloakSession

class KakaoIdentityProvider(
    keycloakSession: KeycloakSession,
    config: KakaoIdentityProviderConfig,
) : AbstractOAuth2IdentityProvider<KakaoIdentityProviderConfig>(
        keycloakSession,
        config,
    ),
    SocialIdentityProvider<KakaoIdentityProviderConfig> {
    override fun getDefaultScopes(): String = KakaoConstant.defaultScope
}
