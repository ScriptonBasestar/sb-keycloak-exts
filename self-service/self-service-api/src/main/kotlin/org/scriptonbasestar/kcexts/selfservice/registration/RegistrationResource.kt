package org.scriptonbasestar.kcexts.selfservice.registration

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.selfservice.model.ApiResponses

/**
 * Registration REST API Resource
 *
 * Endpoints:
 * - POST /registration                    - Register new user
 * - GET  /registration/verify?token=xxx   - Verify email
 * - POST /registration/resend             - Resend verification email
 */
@Path("/")
class RegistrationResource(
    private val session: KeycloakSession,
) {
    private val logger = Logger.getLogger(RegistrationResource::class.java)
    private val workflow = RegistrationWorkflow(session)

    /**
     * Register a new user
     *
     * POST /realms/{realm}/self-service/registration
     *
     * Request Body:
     * {
     *   "username": "john",
     *   "email": "john@example.com",
     *   "firstName": "John",
     *   "lastName": "Doe",
     *   "password": "SecurePass123!",
     *   "confirmPassword": "SecurePass123!",
     *   "consents": {
     *     "terms_of_service": true,
     *     "privacy_policy": true
     *   }
     * }
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun register(request: RegistrationRequest): Response =
        try {
            val response = workflow.register(request)
            Response
                .status(Response.Status.CREATED)
                .entity(ApiResponses.success(response, "Registration successful"))
                .build()
        } catch (e: IllegalArgumentException) {
            logger.warn("Registration validation failed: ${e.message}")
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(ApiResponses.error<RegistrationResponse>("VALIDATION_ERROR", e.message ?: "Validation failed"))
                .build()
        } catch (e: IllegalStateException) {
            logger.warn("Registration state error: ${e.message}")
            Response
                .status(Response.Status.CONFLICT)
                .entity(ApiResponses.error<RegistrationResponse>("STATE_ERROR", e.message ?: "Invalid state"))
                .build()
        } catch (e: Exception) {
            logger.error("Registration failed", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponses.error<RegistrationResponse>("INTERNAL_ERROR", "Registration failed"))
                .build()
        }

    /**
     * Verify email with token
     *
     * GET /realms/{realm}/self-service/registration/verify?token=xxx
     */
    @GET
    @Path("/verify")
    @Produces(MediaType.APPLICATION_JSON)
    fun verifyEmail(
        @QueryParam("token") token: String?,
    ): Response {
        if (token.isNullOrBlank()) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(ApiResponses.error<VerificationResponse>("INVALID_TOKEN", "Token is required"))
                .build()
        }

        return try {
            val response = workflow.verifyEmail(token)

            if (response.success) {
                Response
                    .ok()
                    .entity(ApiResponses.success(response, "Email verified successfully"))
                    .build()
            } else {
                Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(
                        ApiResponses.error<VerificationResponse>(
                            "VERIFICATION_FAILED",
                            response.error ?: "Verification failed",
                        ),
                    ).build()
            }
        } catch (e: Exception) {
            logger.error("Email verification failed", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponses.error<VerificationResponse>("INTERNAL_ERROR", "Verification failed"))
                .build()
        }
    }

    /**
     * Resend verification email
     *
     * POST /realms/{realm}/self-service/registration/resend
     *
     * Request Body:
     * {
     *   "email": "john@example.com"
     * }
     */
    @POST
    @Path("/resend")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun resendVerification(request: ResendVerificationRequest): Response =
        try {
            val emailSent = workflow.resendVerification(request.email)

            if (emailSent) {
                Response
                    .ok()
                    .entity(ApiResponses.success<String>("Verification email resent successfully"))
                    .build()
            } else {
                Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponses.error<String>("EMAIL_SEND_FAILED", "Failed to send email"))
                    .build()
            }
        } catch (e: IllegalArgumentException) {
            logger.warn("Resend verification failed: ${e.message}")
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(ApiResponses.error<String>("VALIDATION_ERROR", e.message ?: "Invalid email"))
                .build()
        } catch (e: IllegalStateException) {
            logger.warn("Resend verification state error: ${e.message}")
            Response
                .status(Response.Status.CONFLICT)
                .entity(ApiResponses.error<String>("STATE_ERROR", e.message ?: "Email already verified"))
                .build()
        } catch (e: Exception) {
            logger.error("Resend verification failed", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponses.error<String>("INTERNAL_ERROR", "Failed to resend verification email"))
                .build()
        }
}
