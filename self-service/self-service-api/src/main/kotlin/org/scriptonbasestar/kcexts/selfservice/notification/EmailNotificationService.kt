package org.scriptonbasestar.kcexts.selfservice.notification

import org.jboss.logging.Logger
import org.keycloak.email.EmailException
import org.keycloak.email.EmailTemplateProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.models.UserModel

/**
 * Email Notification Service
 *
 * Uses Keycloak's built-in EmailTemplateProvider for sending emails.
 * Templates are located in: src/main/resources/theme-resources/templates/
 */
class EmailNotificationService(
    private val session: KeycloakSession,
) {
    private val logger = Logger.getLogger(EmailNotificationService::class.java)

    private val emailProvider: EmailTemplateProvider by lazy {
        session.getProvider(EmailTemplateProvider::class.java)
    }

    /**
     * Send email verification link
     */
    fun sendVerificationEmail(
        user: UserModel,
        token: String,
    ): Boolean {
        val realm = session.context.realm
        val baseUri = session.context.uri.baseUri
        val link = "${baseUri}realms/${realm.name}/self-service/registration/verify?token=$token"

        return try {
            emailProvider
                .setRealm(realm)
                .setUser(user)
                .setAttribute("link", link)
                .setAttribute("linkExpiration", "24")
                .setAttribute("firstName", user.firstName ?: "User")
                .send(
                    "emailVerificationSubject",
                    "email-verification.ftl",
                    mapOf(
                        "link" to link,
                        "expirationHours" to 24,
                    ),
                )
            logger.info("Verification email sent to: ${user.email}")
            true
        } catch (e: EmailException) {
            logger.error("Failed to send verification email to: ${user.email}", e)
            false
        }
    }

    /**
     * Send welcome email after successful verification
     */
    fun sendWelcomeEmail(user: UserModel): Boolean {
        val realm = session.context.realm

        return try {
            emailProvider
                .setRealm(realm)
                .setUser(user)
                .setAttribute("firstName", user.firstName ?: "User")
                .send(
                    "welcomeSubject",
                    "welcome-email.ftl",
                    mapOf("firstName" to (user.firstName ?: "User")),
                )
            logger.info("Welcome email sent to: ${user.email}")
            true
        } catch (e: EmailException) {
            logger.error("Failed to send welcome email to: ${user.email}", e)
            false
        }
    }

    /**
     * Send password changed notification
     */
    fun sendPasswordChangedNotification(user: UserModel): Boolean {
        val realm = session.context.realm

        return try {
            emailProvider
                .setRealm(realm)
                .setUser(user)
                .setAttribute("firstName", user.firstName ?: "User")
                .send(
                    "passwordChangedSubject",
                    "password-changed.ftl",
                    mapOf("firstName" to (user.firstName ?: "User")),
                )
            logger.info("Password changed notification sent to: ${user.email}")
            true
        } catch (e: EmailException) {
            logger.error("Failed to send password changed notification to: ${user.email}", e)
            false
        }
    }

    /**
     * Send account deletion confirmation
     */
    fun sendDeletionConfirmationEmail(
        user: UserModel,
        scheduledDate: String,
    ): Boolean {
        val realm = session.context.realm

        return try {
            emailProvider
                .setRealm(realm)
                .setUser(user)
                .setAttribute("firstName", user.firstName ?: "User")
                .setAttribute("scheduledDate", scheduledDate)
                .send(
                    "accountDeletionSubject",
                    "account-deletion-confirmation.ftl",
                    mapOf(
                        "firstName" to (user.firstName ?: "User"),
                        "scheduledDate" to scheduledDate,
                    ),
                )
            logger.info("Deletion confirmation email sent to: ${user.email}")
            true
        } catch (e: EmailException) {
            logger.error("Failed to send deletion confirmation email to: ${user.email}", e)
            false
        }
    }

    /**
     * Send account deletion cancelled notification
     */
    fun sendDeletionCancelledEmail(user: UserModel): Boolean {
        val realm = session.context.realm

        return try {
            emailProvider
                .setRealm(realm)
                .setUser(user)
                .setAttribute("firstName", user.firstName ?: "User")
                .send(
                    "accountDeletionCancelledSubject",
                    "account-deletion-cancelled.ftl",
                    mapOf("firstName" to (user.firstName ?: "User")),
                )
            logger.info("Deletion cancelled email sent to: ${user.email}")
            true
        } catch (e: EmailException) {
            logger.error("Failed to send deletion cancelled email to: ${user.email}", e)
            false
        }
    }
}
