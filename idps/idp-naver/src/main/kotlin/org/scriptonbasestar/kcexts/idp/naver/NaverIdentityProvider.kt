package org.scriptonbasestar.kcexts.idp.naver

import com.fasterxml.jackson.databind.JsonNode
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper
import org.keycloak.broker.provider.BrokeredIdentityContext
import org.keycloak.broker.provider.IdentityBrokerException
import org.keycloak.broker.provider.util.SimpleHttp
import org.keycloak.broker.social.SocialIdentityProvider
import org.keycloak.events.EventBuilder
import org.keycloak.models.KeycloakSession

class NaverIdentityProvider(
    keycloakSession: KeycloakSession,
    config: NaverIdentityProviderConfig,
) : AbstractOAuth2IdentityProvider<NaverIdentityProviderConfig>(
    keycloakSession,
    config,
), SocialIdentityProvider<NaverIdentityProviderConfig> {

    override fun supportsExternalExchange(): Boolean = true

    override fun getProfileEndpointForValidation(event: EventBuilder): String = NaverConstant.profileUrl

    override fun extractIdentityFromProfile(event: EventBuilder?, profile: JsonNode): BrokeredIdentityContext {
        val user = BrokeredIdentityContext(profile.get("response").get("id").asText())

        val email: String = profile.get("response").get("email").asText()

        user.idpConfig = config
        user.username = email
        user.email = email
        user.idp = this

        AbstractJsonUserAttributeMapper.storeUserProfileForMapper(user, profile, config.alias)

        return user
    }

    override fun doGetFederatedIdentity(accessToken: String): BrokeredIdentityContext {
        return try {
            val profile = SimpleHttp.doGet(NaverConstant.profileUrl, session)
                .param("access_token", accessToken)
                .asJson()
            extractIdentityFromProfile(null, profile)
        } catch (e: Exception) {
            throw IdentityBrokerException("Could not obtain user profile from naver.", e)
        }
    }

    override fun getDefaultScopes(): String = NaverConstant.defaultScope
}