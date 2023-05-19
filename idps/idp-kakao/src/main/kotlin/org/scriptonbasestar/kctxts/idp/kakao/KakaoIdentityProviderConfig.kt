package org.scriptonbasestar.kctxts.idp.kakao

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class KakaoIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = KakaoConstant.providerId
    }

    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = KakaoConstant.providerId
    }

}