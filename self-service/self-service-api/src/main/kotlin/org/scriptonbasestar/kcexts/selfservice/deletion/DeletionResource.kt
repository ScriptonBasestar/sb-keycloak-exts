package org.scriptonbasestar.kcexts.selfservice.deletion

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.selfservice.model.ApiResponses

/**
 * Account Deletion REST API Resource (Stub)
 *
 * Endpoints:
 * - POST /deletion/request    - Request account deletion (30-day grace period)
 * - POST /deletion/cancel     - Cancel pending deletion
 * - DELETE /deletion/immediate - Immediate deletion (admin only)
 *
 * TODO: Implement full GDPR-compliant deletion workflow
 */
@Path("/")
class DeletionResource(
    private val session: KeycloakSession,
) {
    private val logger = Logger.getLogger(DeletionResource::class.java)

    /**
     * Request account deletion (stub)
     */
    @POST
    @Path("/request")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun requestDeletion(request: Map<String, Any>): Response =
        Response
            .ok()
            .entity(
                ApiResponses.success<String>(
                    "Account deletion request - Coming soon. " +
                        "Will include 30-day grace period and GDPR compliance.",
                ),
            ).build()

    /**
     * Cancel pending deletion (stub)
     */
    @POST
    @Path("/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    fun cancelDeletion(): Response =
        Response
            .ok()
            .entity(ApiResponses.success<String>("Deletion cancellation - Coming soon"))
            .build()

    /**
     * Immediate deletion (stub)
     */
    @DELETE
    @Path("/immediate")
    @Produces(MediaType.APPLICATION_JSON)
    fun immediateDelete(): Response =
        Response
            .status(Response.Status.FORBIDDEN)
            .entity(ApiResponses.error<String>("NOT_IMPLEMENTED", "Immediate deletion requires admin approval"))
            .build()
}
