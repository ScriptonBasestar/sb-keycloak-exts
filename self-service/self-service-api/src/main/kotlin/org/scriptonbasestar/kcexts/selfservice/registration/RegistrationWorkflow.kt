package org.scriptonbasestar.kcexts.selfservice.registration

import org.jboss.logging.Logger
import org.keycloak.models.KeycloakSession
import org.keycloak.models.UserModel
import org.scriptonbasestar.kcexts.selfservice.notification.EmailNotificationService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * User Registration Workflow
 *
 * Handles user registration with email verification:
 * 1. Validate registration request
 * 2. Create user (disabled state)
 * 3. Generate verification token
 * 4. Send verification email
 * 5. Verify token and activate user
 */
class RegistrationWorkflow(
    private val session: KeycloakSession,
) {
    private val logger = Logger.getLogger(RegistrationWorkflow::class.java)
    private val emailService = EmailNotificationService(session)

    /**
     * Register a new user
     */
    fun register(request: RegistrationRequest): RegistrationResponse {
        val realm = session.context.realm

        // 1. Validate input
        validateRegistrationRequest(request)

        // 2. Check for duplicate username
        if (session.users().getUserByUsername(realm, request.username) != null) {
            throw IllegalArgumentException("Username already exists")
        }

        // 3. Check for duplicate email
        if (request.email.isNotBlank() && session.users().getUserByEmail(realm, request.email) != null) {
            throw IllegalArgumentException("Email already exists")
        }

        // 4. Create user (disabled until email verification)
        val user = session.users().addUser(realm, request.username)
        user.email = request.email
        user.firstName = request.firstName
        user.lastName = request.lastName
        user.isEnabled = false // Disabled until email verification
        user.isEmailVerified = false

        // 5. Set password using UserCredentialModel
        val credential =
            org.keycloak.models.UserCredentialModel
                .password(request.password)
        user.credentialManager().updateCredential(credential)

        // 6. Set initial attributes
        user.setSingleAttribute("registrationStatus", "pending_verification")
        user.setSingleAttribute("registrationDate", Instant.now().toString())

        // 7. Store consents if provided
        request.consents?.forEach { (type, granted) ->
            user.setSingleAttribute("consent.$type", granted.toString())
        }

        // 8. Store additional attributes
        request.attributes?.forEach { (key, value) ->
            user.setSingleAttribute(key, value)
        }

        // 9. Generate and store verification token
        val verificationToken = generateVerificationToken()
        user.setSingleAttribute("verificationToken", verificationToken)
        user.setSingleAttribute(
            "verificationTokenExpiry",
            Instant.now().plus(24, ChronoUnit.HOURS).toString(),
        )

        // 10. Send verification email
        val emailSent = emailService.sendVerificationEmail(user, verificationToken)

        logger.info("User registered: ${user.username} (ID: ${user.id}), Email sent: $emailSent")

        return RegistrationResponse(
            userId = user.id,
            status = RegistrationStatus.PENDING_VERIFICATION,
            message = "Registration successful. Please check your email to verify your account.",
            verificationRequired = true,
        )
    }

    /**
     * Verify email with token
     */
    fun verifyEmail(token: String): VerificationResponse {
        val realm = session.context.realm

        // 1. Find user by verification token
        val user =
            findUserByVerificationToken(token)
                ?: return VerificationResponse(
                    success = false,
                    error = "Invalid or expired verification token",
                )

        // 2. Check token expiry
        val expiry =
            user
                .getFirstAttribute("verificationTokenExpiry")
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }

        if (expiry == null || Instant.now().isAfter(expiry)) {
            return VerificationResponse(
                success = false,
                error = "Verification token has expired. Please request a new one.",
            )
        }

        // 3. Activate user
        user.isEnabled = true
        user.isEmailVerified = true
        user.setSingleAttribute("registrationStatus", "verified")
        user.removeAttribute("verificationToken")
        user.removeAttribute("verificationTokenExpiry")

        // 4. Send welcome email
        emailService.sendWelcomeEmail(user)

        logger.info("User email verified: ${user.username} (ID: ${user.id})")

        return VerificationResponse(
            success = true,
            message = "Email verified successfully. You can now log in.",
        )
    }

    /**
     * Resend verification email
     */
    fun resendVerification(email: String): Boolean {
        val realm = session.context.realm

        val user =
            session.users().getUserByEmail(realm, email)
                ?: throw IllegalArgumentException("Email not found")

        if (user.isEmailVerified) {
            throw IllegalStateException("Email already verified")
        }

        // Generate new token
        val verificationToken = generateVerificationToken()
        user.setSingleAttribute("verificationToken", verificationToken)
        user.setSingleAttribute(
            "verificationTokenExpiry",
            Instant.now().plus(24, ChronoUnit.HOURS).toString(),
        )

        // Resend email
        val emailSent = emailService.sendVerificationEmail(user, verificationToken)

        logger.info("Verification email resent to: $email")

        return emailSent
    }

    /**
     * Validate registration request
     */
    private fun validateRegistrationRequest(request: RegistrationRequest) {
        require(request.username.isNotBlank()) { "Username is required" }
        require(request.email.isNotBlank()) { "Email is required" }
        require(request.password.isNotBlank()) { "Password is required" }
        require(request.username.length >= 3) { "Username must be at least 3 characters" }
        require(request.email.contains("@")) { "Invalid email format" }
        require(request.password.length >= 8) { "Password must be at least 8 characters" }

        // Check password confirmation if provided
        if (request.confirmPassword != null) {
            require(request.password == request.confirmPassword) { "Passwords do not match" }
        }
    }

    /**
     * Generate random verification token
     */
    private fun generateVerificationToken(): String = UUID.randomUUID().toString().replace("-", "")

    /**
     * Find user by verification token
     */
    private fun findUserByVerificationToken(token: String): UserModel? {
        val realm = session.context.realm

        return session
            .users()
            .searchForUserByUserAttributeStream(realm, "verificationToken", token)
            .findFirst()
            .orElse(null)
    }
}
