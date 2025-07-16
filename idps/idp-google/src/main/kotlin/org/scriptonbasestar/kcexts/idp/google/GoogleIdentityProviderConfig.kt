package org.scriptonbasestar.kcexts.idp.google

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class GoogleIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = GoogleConstant.providerId
        this.authorizationUrl = GoogleConstant.authUrl
        this.tokenUrl = GoogleConstant.tokenUrl
        this.userInfoUrl = GoogleConstant.profileUrl
        this.defaultScope = GoogleConstant.defaultScope
    }

    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = GoogleConstant.providerId
        this.authorizationUrl = GoogleConstant.authUrl
        this.tokenUrl = GoogleConstant.tokenUrl
        this.userInfoUrl = GoogleConstant.profileUrl
        this.defaultScope = GoogleConstant.defaultScope
    }
}