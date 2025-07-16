package org.scriptonbasestar.kcexts.idp.kakao

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper

class KakaoUserAttributeMapper : AbstractJsonUserAttributeMapper() {
    override fun getId(): String = "kakao-user-attribute-mapper"

    override fun getCompatibleProviders(): Array<String> = arrayOf(KakaoConstant.providerId)
}
