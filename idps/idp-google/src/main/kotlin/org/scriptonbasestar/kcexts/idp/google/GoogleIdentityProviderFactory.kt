package org.scriptonbasestar.kcexts.idp.google

import org.keycloak.broker.provider.AbstractIdentityProviderFactory
import org.keycloak.broker.social.SocialIdentityProviderFactory
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession

class GoogleIdentityProviderFactory :
    AbstractIdentityProviderFactory<GoogleIdentityProvider>(),
    SocialIdentityProviderFactory<GoogleIdentityProvider> {

    override fun create(session: KeycloakSession, model: IdentityProviderModel): GoogleIdentityProvider {
        return GoogleIdentityProvider(session, GoogleIdentityProviderConfig(model))
    }

    override fun getId(): String = GoogleConstant.providerId

    override fun getName(): String = GoogleConstant.providerName

    override fun createConfig(): IdentityProviderModel = GoogleIdentityProviderConfig()
}