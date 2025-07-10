package org.scriptonbasestar.kctxts.idp.kakao

import org.keycloak.broker.provider.AbstractIdentityProviderFactory
import org.keycloak.broker.social.SocialIdentityProviderFactory
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession

class KakaoIdentityProviderFactory :
    AbstractIdentityProviderFactory<KakaoIdentityProvider>(),
    SocialIdentityProviderFactory<KakaoIdentityProvider> {
    override fun create(
        session: KeycloakSession,
        model: IdentityProviderModel,
    ): KakaoIdentityProvider {
        return KakaoIdentityProvider(session, KakaoIdentityProviderConfig(model))
    }

    override fun getId(): String = KakaoConstant.providerId

    override fun getName(): String = KakaoConstant.providerName

    override fun createConfig(): IdentityProviderModel = KakaoIdentityProviderConfig()
}
