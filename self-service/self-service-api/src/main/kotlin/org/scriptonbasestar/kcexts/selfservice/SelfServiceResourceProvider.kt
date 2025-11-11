package org.scriptonbasestar.kcexts.selfservice

import jakarta.ws.rs.Path
import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider
import org.scriptonbasestar.kcexts.selfservice.consent.ConsentResource
import org.scriptonbasestar.kcexts.selfservice.deletion.DeletionResource
import org.scriptonbasestar.kcexts.selfservice.password.PasswordResource
import org.scriptonbasestar.kcexts.selfservice.profile.ProfileResource
import org.scriptonbasestar.kcexts.selfservice.registration.RegistrationResource

/**
 * Self-Service REST API Provider
 *
 * Provides self-service endpoints for users:
 * - Registration with email verification
 * - Profile management
 * - Consent management (GDPR compliance)
 * - Password change
 * - Account deletion (soft delete with 30-day grace period)
 *
 * Base URL: /realms/{realm}/self-service/
 *
 * Endpoints:
 * - POST   /self-service/registration               - Register new user
 * - GET    /self-service/registration/verify        - Verify email
 * - GET    /self-service/profile                    - Get profile
 * - PUT    /self-service/profile                    - Update profile
 * - GET    /self-service/consents                   - Get consents
 * - POST   /self-service/consents                   - Record consent
 * - GET    /self-service/password/policy            - Get password policy
 * - POST   /self-service/password/change            - Change password
 * - POST   /self-service/deletion/request           - Request account deletion
 * - POST   /self-service/deletion/cancel            - Cancel deletion
 */
@Path("/")
class SelfServiceResourceProvider(
    private val session: KeycloakSession,
) : RealmResourceProvider {
    @Path("registration")
    fun registration(): RegistrationResource = RegistrationResource(session)

    @Path("profile")
    fun profile(): ProfileResource = ProfileResource(session)

    @Path("consents")
    fun consents(): ConsentResource = ConsentResource(session)

    @Path("password")
    fun password(): PasswordResource = PasswordResource(session)

    @Path("deletion")
    fun deletion(): DeletionResource = DeletionResource(session)

    override fun close() {
        // Cleanup if needed
    }

    override fun getResource(): Any = this
}
