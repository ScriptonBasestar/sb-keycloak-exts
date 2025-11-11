package org.scriptonbasestar.kcexts.selfservice.password

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import org.keycloak.models.KeycloakSession

/**
 * Password Management REST API Resource (Stub)
 *
 * Endpoints:
 * - GET  /password/policy  - Get password policy
 * - POST /password/change  - Change password
 *
 * TODO: Implement full password management
 */
@Path("/")
class PasswordResource(
    private val session: KeycloakSession,
) {
    private val logger = Logger.getLogger(PasswordResource::class.java)

    /**
     * Get password policy (stub)
     */
    @GET
    @Path("/policy")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPasswordPolicy(): Response =
        Response
            .ok()
            .entity(
                mapOf(
                    "success" to true,
                    "data" to mapOf("message" to "Password policy - Coming soon"),
                    "message" to "Stub implementation",
                ),
            ).build()

    /**
     * Change password (stub)
     */
    @POST
    @Path("/change")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun changePassword(request: Map<String, String>): Response =
        Response
            .ok()
            .entity(
                mapOf(
                    "success" to true,
                    "message" to "Password change - Coming soon",
                ),
            ).build()
}
