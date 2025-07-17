package org.scriptonbasestar.kcexts.idp.google

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper

class GoogleUserAttributeMapper : AbstractJsonUserAttributeMapper() {
    override fun getId(): String = "google-user-attribute-mapper"

    override fun getCompatibleProviders(): Array<String> = arrayOf(GoogleConstant.providerId)
}
