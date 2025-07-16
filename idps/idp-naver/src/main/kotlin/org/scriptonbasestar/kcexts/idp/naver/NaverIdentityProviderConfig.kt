package org.scriptonbasestar.kcexts.idp.naver

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class NaverIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = NaverConstant.providerId
        this.authorizationUrl = NaverConstant.authUrl
        this.tokenUrl = NaverConstant.tokenUrl
        this.userInfoUrl = NaverConstant.profileUrl
        this.defaultScope = NaverConstant.defaultScope
    }
    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = NaverConstant.providerId
        this.authorizationUrl = NaverConstant.authUrl
        this.tokenUrl = NaverConstant.tokenUrl
        this.userInfoUrl = NaverConstant.profileUrl
        this.defaultScope = NaverConstant.defaultScope
    }
}
