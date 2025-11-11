package org.scriptonbasestar.kcexts.selfservice.profile

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
 * Profile Management REST API Resource
 *
 * Endpoints:
 * - GET  /profile  - Get current user profile
 * - PUT  /profile  - Update current user profile
 *
 * Authentication: Requires valid Keycloak session token
 */
@Path("/")
class ProfileResource(
    private val session: KeycloakSession,
) {
    private val logger = Logger.getLogger(ProfileResource::class.java)
    private val emailService = EmailNotificationService(session)

    /**
     * Get current user profile
     *
     * GET /realms/{realm}/self-service/profile
     *
     * Requires: Bearer token in Authorization header
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getProfile(
        @Context headers: HttpHeaders,
    ): Response {
        val user =
            getCurrentUser()
                ?: return Response
                    .status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponses.error<UserProfile>("UNAUTHORIZED", "Authentication required"))
                    .build()

        return try {
            val profile = mapUserToProfile(user)
            Response
                .ok()
                .entity(ApiResponses.success(profile))
                .build()
        } catch (e: Exception) {
            logger.error("Failed to get profile", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponses.error<UserProfile>("INTERNAL_ERROR", "Failed to retrieve profile"))
                .build()
        }
    }

    /**
     * Update current user profile
     *
     * PUT /realms/{realm}/self-service/profile
     *
     * Request Body:
     * {
     *   "firstName": "John",
     *   "lastName": "Doe",
     *   "email": "newemail@example.com",
     *   "attributes": {
     *     "phoneNumber": "+82-10-1234-5678",
     *     "department": "Engineering"
     *   }
     * }
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateProfile(
        @Context headers: HttpHeaders,
        request: UpdateProfileRequest,
    ): Response {
        val user =
            getCurrentUser()
                ?: return Response
                    .status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponses.error<UserProfile>("UNAUTHORIZED", "Authentication required"))
                    .build()

        return try {
            // Track changes for logging and email notification
            val changes = mutableMapOf<String, Pair<String?, String?>>()

            // Update first name
            request.firstName?.let {
                if (it != user.firstName) {
                    changes["firstName"] = user.firstName to it
                    user.firstName = it
                }
            }

            // Update last name
            request.lastName?.let {
                if (it != user.lastName) {
                    changes["lastName"] = user.lastName to it
                    user.lastName = it
                }
            }

            // Update email (requires re-verification)
            request.email?.let {
                if (it != user.email && it.isNotBlank()) {
                    changes["email"] = user.email to it
                    user.email = it
                    user.isEmailVerified = false

                    // TODO: Send email verification for new address
                    logger.info("Email changed for user: ${user.username}, verification required")
                }
            }

            // Update custom attributes
            request.attributes?.forEach { (key, value) ->
                val oldValue = user.getFirstAttribute(key)
                if (oldValue != value) {
                    changes[key] = oldValue to value
                    user.setSingleAttribute(key, value)
                }
            }

            logger.info("Profile updated for user: ${user.username}, changes: ${changes.keys}")

            val profile = mapUserToProfile(user)
            Response
                .ok()
                .entity(ApiResponses.success(profile, "Profile updated successfully"))
                .build()
        } catch (e: IllegalArgumentException) {
            logger.warn("Profile update validation failed: ${e.message}")
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(ApiResponses.error<UserProfile>("VALIDATION_ERROR", e.message ?: "Validation failed"))
                .build()
        } catch (e: Exception) {
            logger.error("Failed to update profile", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponses.error<UserProfile>("INTERNAL_ERROR", "Failed to update profile"))
                .build()
        }
    }

    /**
     * Get current authenticated user
     */
    private fun getCurrentUser(): UserModel? {
        // Get user from Keycloak session context
        val authSession = session.context.authenticationSession
        if (authSession != null) {
            return authSession.authenticatedUser
        }

        // Alternative: Get from user session
        val userSession =
            session.sessions().getUserSession(
                session.context.realm,
                session.context.connection.remoteAddr,
            )

        return userSession?.user
    }

    /**
     * Map UserModel to UserProfile DTO
     */
    private fun mapUserToProfile(user: UserModel): UserProfile =
        UserProfile(
            id = user.id,
            username = user.username,
            email = user.email,
            emailVerified = user.isEmailVerified,
            firstName = user.firstName,
            lastName = user.lastName,
            attributes =
                user.attributes.filterKeys {
                    // Only include user-modifiable attributes
                    it in listOf("phoneNumber", "department", "company", "location")
                },
            avatarUrl = user.getFirstAttribute("avatarUrl"),
            createdAt = user.createdTimestamp?.let { Instant.ofEpochMilli(it) },
        )
}
