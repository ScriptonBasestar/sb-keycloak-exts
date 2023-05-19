package org.scriptonbasestar.kctxts.idp.line

import org.keycloak.broker.provider.AbstractIdentityProviderFactory
import org.keycloak.broker.social.SocialIdentityProviderFactory
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession

class LineIdentityProviderFactory : AbstractIdentityProviderFactory<LineIdentityProvider>(),
    SocialIdentityProviderFactory<LineIdentityProvider> {
    override fun create(session: KeycloakSession, model: IdentityProviderModel): LineIdentityProvider {
        return LineIdentityProvider(session, LineIdentityProviderConfig(model))
    }

    override fun getId(): String = LineConstant.providerId

    override fun getName(): String = LineConstant.providerName

    override fun createConfig(): IdentityProviderModel = LineIdentityProviderConfig()
}