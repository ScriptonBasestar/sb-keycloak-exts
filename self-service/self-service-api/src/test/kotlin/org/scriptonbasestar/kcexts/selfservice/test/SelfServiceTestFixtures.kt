package org.scriptonbasestar.kcexts.selfservice.test

import org.keycloak.models.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test fixtures for Self-Service API tests
 *
 * Provides reusable builders for creating mock Keycloak objects
 * following the pattern established in event-listener-common.
 */
object SelfServiceTestFixtures {
    /**
     * Create a mock KeycloakSession with basic setup
     */
    fun createMockSession(
        realm: RealmModel = createMockRealm(),
        users: UserProvider = mock(),
    ): KeycloakSession {
        val session = mock<KeycloakSession>()
        val context = mock<KeycloakContext>()
        val uriInfo = mock<org.keycloak.models.KeycloakUriInfo>()
        val baseUri = mock<java.net.URI>()
        val connection = mock<org.keycloak.common.ClientConnection>()

        whenever(session.context).thenReturn(context)
        whenever(context.realm).thenReturn(realm)
        whenever(context.uri).thenReturn(uriInfo)
        whenever(context.connection).thenReturn(connection)
        whenever(uriInfo.baseUri).thenReturn(baseUri)
        whenever(baseUri.toString()).thenReturn("http://localhost:8080/")
        whenever(connection.remoteAddr).thenReturn("127.0.0.1")
        whenever(session.users()).thenReturn(users)

        // Mock email provider
        val emailProvider = mock<org.keycloak.email.EmailTemplateProvider>()
        whenever(session.getProvider(org.keycloak.email.EmailTemplateProvider::class.java)).thenReturn(emailProvider)
        whenever(emailProvider.setRealm(any())).thenReturn(emailProvider)
        whenever(emailProvider.setUser(any())).thenReturn(emailProvider)
        whenever(emailProvider.setAttribute(any(), any())).thenReturn(emailProvider)
        whenever(emailProvider.send(any(), any(), any())).then { /* no-op */ }

        return session
    }

    /**
     * Create a mock RealmModel with sensible defaults
     */
    fun createMockRealm(
        name: String = "test-realm",
        passwordPolicy: PasswordPolicy? = null,
    ): RealmModel {
        val realm = mock<RealmModel>()
        whenever(realm.name).thenReturn(name)
        whenever(realm.passwordPolicy).thenReturn(passwordPolicy)
        return realm
    }

    /**
     * Create a mock UserModel with sensible defaults
     */
    fun createMockUser(
        id: String = "test-user-${System.currentTimeMillis()}",
        username: String = "testuser",
        email: String = "test@example.com",
        firstName: String? = "Test",
        lastName: String? = "User",
        isEnabled: Boolean = true,
        isEmailVerified: Boolean = false,
    ): UserModel {
        val user = mock<UserModel>()
        whenever(user.id).thenReturn(id)
        whenever(user.username).thenReturn(username)
        whenever(user.email).thenReturn(email)
        whenever(user.firstName).thenReturn(firstName)
        whenever(user.lastName).thenReturn(lastName)
        whenever(user.isEnabled).thenReturn(isEnabled)
        whenever(user.isEmailVerified).thenReturn(isEmailVerified)

        // Mock credential manager for password operations
        val credentialManager = mock<org.keycloak.models.SubjectCredentialManager>()
        whenever(user.credentialManager()).thenReturn(credentialManager)
        whenever(credentialManager.updateCredential(any())).thenReturn(true)
        whenever(credentialManager.isValid(any<org.keycloak.credential.CredentialInput>())).thenReturn(true)

        return user
    }

    /**
     * Create a mock UserProvider with common operations
     */
    fun createMockUserProvider(): UserProvider = mock()

    /**
     * Builder class for UserModel configuration
     */
    data class UserBuilder(
        var id: String = "test-user-${System.currentTimeMillis()}",
        var username: String = "testuser",
        var email: String = "test@example.com",
        var firstName: String? = "Test",
        var lastName: String? = "User",
        var isEnabled: Boolean = true,
        var isEmailVerified: Boolean = false,
        var attributes: MutableMap<String, List<String>> = mutableMapOf(),
    )

    /**
     * Create a mock UserModel using builder pattern
     */
    fun createMockUser(builder: UserBuilder.() -> Unit): UserModel {
        val config = UserBuilder().apply(builder)
        return createMockUser(
            id = config.id,
            username = config.username,
            email = config.email,
            firstName = config.firstName,
            lastName = config.lastName,
            isEnabled = config.isEnabled,
            isEmailVerified = config.isEmailVerified,
        )
    }
}
