package org.scriptonbasestar.kcexts.idp.kakao

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class KakaoIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = KakaoConstant.providerId
        this.authorizationUrl = KakaoConstant.authUrl
        this.tokenUrl = KakaoConstant.tokenUrl
        this.userInfoUrl = KakaoConstant.profileUrl
        this.defaultScope = KakaoConstant.defaultScope
    }

    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = KakaoConstant.providerId
        this.authorizationUrl = KakaoConstant.authUrl
        this.tokenUrl = KakaoConstant.tokenUrl
        this.userInfoUrl = KakaoConstant.profileUrl
        this.defaultScope = KakaoConstant.defaultScope
    }
}
