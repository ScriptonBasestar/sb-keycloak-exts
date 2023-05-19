package org.scriptonbasestar.kcexts.idp.naver

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper

class NaverUserAttributeMapper: AbstractJsonUserAttributeMapper() {
    override fun getId(): String = "naver-user-attribute-mapper"

    override fun getCompatibleProviders(): Array<String> = arrayOf(NaverConstant.providerId)
}