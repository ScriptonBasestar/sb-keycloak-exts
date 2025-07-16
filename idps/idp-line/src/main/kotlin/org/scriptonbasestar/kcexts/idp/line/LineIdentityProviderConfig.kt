package org.scriptonbasestar.kcexts.idp.line

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class LineIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = LineConstant.providerId
        this.authorizationUrl = LineConstant.authUrl
        this.tokenUrl = LineConstant.tokenUrl
        this.userInfoUrl = LineConstant.profileUrl
        this.defaultScope = LineConstant.defaultScope
    }

    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = LineConstant.providerId
        this.authorizationUrl = LineConstant.authUrl
        this.tokenUrl = LineConstant.tokenUrl
        this.userInfoUrl = LineConstant.profileUrl
        this.defaultScope = LineConstant.defaultScope
    }
}
