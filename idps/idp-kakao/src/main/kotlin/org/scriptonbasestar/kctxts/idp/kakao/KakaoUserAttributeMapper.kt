package org.scriptonbasestar.kctxts.idp.kakao

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper

class KakaoUserAttributeMapper : AbstractJsonUserAttributeMapper() {
    override fun getId(): String = "line-user-attribute-mapper"

    override fun getCompatibleProviders(): Array<String> = arrayOf(KakaoConstant.providerId)
}