package org.scriptonbasestar.kctxts.idp.line

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class LineIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = LineConstant.providerId
    }

    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = LineConstant.providerId
    }
}
