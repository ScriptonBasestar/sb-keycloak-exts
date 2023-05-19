package org.scriptonbasestar.kctxts.idp.line

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper

class LineUserAttributeMapper : AbstractJsonUserAttributeMapper() {
    override fun getId(): String = "line-user-attribute-mapper"

    override fun getCompatibleProviders(): Array<String> = arrayOf(LineConstant.providerId)
}
