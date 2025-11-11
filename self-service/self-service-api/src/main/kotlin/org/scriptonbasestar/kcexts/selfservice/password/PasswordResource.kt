package org.scriptonbasestar.kcexts.selfservice.password

import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import org.keycloak.models.KeycloakSession
import org.keycloak.models.UserModel
import org.scriptonbasestar.kcexts.selfservice.model.ApiResponses
import org.scriptonbasestar.kcexts.selfservice.notification.EmailNotificationService
import java.time.Instant

/**
 * Password Management REST API Resource
 *
 * Endpoints:
 * - GET  /password/policy  - Get password policy
 * - POST /password/change  - Change password
 *
 * Features:
 * - Password policy retrieval
 * - Current password verification
 * - Password change with validation
 * - Email notification on password change
 */
@Path("/")
class PasswordResource(
    private val session: KeycloakSession,
) {
    private val logger = Logger.getLogger(PasswordResource::class.java)
    private val emailService = EmailNotificationService(session)

    /**
     * Get password policy
     *
     * GET /realms/{realm}/self-service/password/policy
     */
    @GET
    @Path("/policy")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPasswordPolicy(): Response {
        val realm = session.context.realm
        val policy = realm.passwordPolicy

        return try {
            val passwordPolicy =
                if (policy != null) {
                    PasswordPolicy(
                        minLength = 8,
                        hashAlgorithm = policy.hashAlgorithm,
                        hashIterations = policy.hashIterations,
                        requirements =
                            listOf(
                                "Minimum 8 characters",
                                "Must be different from current password",
                            ),
                    )
                } else {
                    PasswordPolicy()
                }

            Response
                .ok()
                .entity(ApiResponses.success(passwordPolicy, "Password policy retrieved"))
                .build()
        } catch (e: Exception) {
            logger.error("Failed to get password policy", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponses.error<PasswordPolicy>("INTERNAL_ERROR", "Failed to retrieve password policy"))
                .build()
        }
    }

    /**
     * Change password
     *
     * POST /realms/{realm}/self-service/password/change
     */
    @POST
    @Path("/change")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun changePassword(
        @Context headers: HttpHeaders,
        request: ChangePasswordRequest,
    ): Response {
        val user =
            getCurrentUser()
                ?: return Response
                    .status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponses.error<ChangePasswordResponse>("UNAUTHORIZED", "Authentication required"))
                    .build()

        return try {
            // 1. Validate request
            validateChangePasswordRequest(request)

            // 2. Verify current password
            if (!verifyCurrentPassword(user, request.currentPassword)) {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(
                        ApiResponses.error<ChangePasswordResponse>(
                            "INVALID_CURRENT_PASSWORD",
                            "Current password is incorrect",
                        ),
                    ).build()
            }

            // 3. Validate new password (basic validation)
            val validationResult = validateNewPassword(request.newPassword)
            if (!validationResult.valid) {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(
                        ApiResponses.error<ChangePasswordResponse>(
                            "POLICY_VIOLATION",
                            validationResult.errors.joinToString("; "),
                        ),
                    ).build()
            }

            // 4. Update password
            val credential = org.keycloak.models.UserCredentialModel.password(request.newPassword)
            user.credentialManager().updateCredential(credential)

            // 5. Update password change timestamp
            user.setSingleAttribute("lastPasswordChange", Instant.now().toString())

            // 6. Send email notification
            val ipAddress = session.context.connection.remoteAddr
            emailService.sendPasswordChangedEmail(user, ipAddress)

            logger.info("Password changed successfully for user: ${user.username}")

            val response =
                ChangePasswordResponse(
                    success = true,
                    message = "Password changed successfully",
                )

            Response
                .ok()
                .entity(ApiResponses.success(response, "Password changed successfully"))
                .build()
        } catch (e: IllegalArgumentException) {
            logger.warn("Password change validation failed: ${e.message}")
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(ApiResponses.error<ChangePasswordResponse>("VALIDATION_ERROR", e.message ?: "Validation failed"))
                .build()
        } catch (e: Exception) {
            logger.error("Password change failed", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponses.error<ChangePasswordResponse>("INTERNAL_ERROR", "Failed to change password"))
                .build()
        }
    }

    /**
     * Get current authenticated user
     */
    private fun getCurrentUser(): UserModel? {
        val authSession = session.context.authenticationSession
        if (authSession != null) {
            return authSession.authenticatedUser
        }

        val userSession =
            session.sessions().getUserSession(
                session.context.realm,
                session.context.connection.remoteAddr,
            )

        return userSession?.user
    }

    /**
     * Verify current password
     */
    private fun verifyCurrentPassword(
        user: UserModel,
        password: String,
    ): Boolean {
        val credential = org.keycloak.models.UserCredentialModel.password(password)
        return user.credentialManager().isValid(credential)
    }

    /**
     * Validate new password (basic validation)
     */
    private fun validateNewPassword(newPassword: String): PasswordValidationResult {
        val errors = mutableListOf<String>()

        // Basic validation
        if (newPassword.length < 8) {
            errors.add("Password must be at least 8 characters")
        }

        return PasswordValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
        )
    }

    /**
     * Validate change password request
     */
    private fun validateChangePasswordRequest(request: ChangePasswordRequest) {
        require(request.currentPassword.isNotBlank()) { "Current password is required" }
        require(request.newPassword.isNotBlank()) { "New password is required" }
        require(request.confirmPassword.isNotBlank()) { "Confirm password is required" }
        require(request.newPassword == request.confirmPassword) { "Passwords do not match" }
        require(request.currentPassword != request.newPassword) { "New password must be different from current password" }
    }
}
