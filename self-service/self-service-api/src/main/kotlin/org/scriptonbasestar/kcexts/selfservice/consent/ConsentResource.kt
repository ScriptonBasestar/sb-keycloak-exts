package org.scriptonbasestar.kcexts.selfservice.consent

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.selfservice.model.ApiResponses

/**
 * Consent Management REST API Resource (Stub)
 *
 * Endpoints:
 * - GET  /consents          - Get user consents
 * - POST /consents          - Record consent
 * - GET  /consents/history  - Get consent history
 *
 * TODO: Implement full consent management with JPA Entity
 */
@Path("/")
class ConsentResource(
    private val session: KeycloakSession,
) {
    private val logger = Logger.getLogger(ConsentResource::class.java)

    /**
     * Get user consents (stub)
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getConsents(): Response =
        Response
            .ok()
            .entity(
                ApiResponses.success<Map<String, Any>>(
                    mapOf("message" to "Consent management - Coming soon"),
                    "Stub implementation",
                ),
            ).build()

    /**
     * Record consent (stub)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun recordConsent(request: Map<String, Any>): Response =
        Response
            .ok()
            .entity(ApiResponses.success<String>("Consent recorded (stub)"))
            .build()

    /**
     * Get consent history (stub)
     */
    @GET
    @Path("/history")
    @Produces(MediaType.APPLICATION_JSON)
    fun getConsentHistory(): Response =
        Response
            .ok()
            .entity(ApiResponses.success<List<Any>>(emptyList(), "Stub implementation"))
            .build()
}
