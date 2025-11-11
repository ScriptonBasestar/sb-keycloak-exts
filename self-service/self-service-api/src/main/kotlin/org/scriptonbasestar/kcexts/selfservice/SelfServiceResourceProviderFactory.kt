package org.scriptonbasestar.kcexts.selfservice

import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.services.resource.RealmResourceProvider
import org.keycloak.services.resource.RealmResourceProviderFactory

/**
 * Self-Service Resource Provider Factory
 *
 * Registers the Self-Service API endpoints with Keycloak.
 *
 * Endpoints available at: /realms/{realm}/self-service/...
 */
class SelfServiceResourceProviderFactory : RealmResourceProviderFactory {
    companion object {
        const val ID = "self-service"
    }

    override fun create(session: KeycloakSession): RealmResourceProvider = SelfServiceResourceProvider(session)

    override fun init(config: Config.Scope?) {
        // Initialize configuration if needed
    }

    override fun postInit(factory: KeycloakSessionFactory?) {
        // Post-initialization tasks
    }

    override fun close() {
        // Cleanup resources
    }

    override fun getId(): String = ID
}
