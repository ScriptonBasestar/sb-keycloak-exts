package org.scriptonbasestar.kcexts.idp.naver

import org.keycloak.broker.provider.AbstractIdentityProviderFactory
import org.keycloak.broker.social.SocialIdentityProviderFactory
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession

class NaverIdentityProviderFactory :
    AbstractIdentityProviderFactory<NaverIdentityProvider>(),
    SocialIdentityProviderFactory<NaverIdentityProvider> {
    override fun create(
        session: KeycloakSession,
        model: IdentityProviderModel,
    ): NaverIdentityProvider = NaverIdentityProvider(session, NaverIdentityProviderConfig(model))

    override fun getId(): String = NaverConstant.providerId

    override fun getName(): String = NaverConstant.providerName

    override fun createConfig(): IdentityProviderModel = NaverIdentityProviderConfig()
}
