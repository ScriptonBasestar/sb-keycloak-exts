package org.scriptonbasestar.kcexts.selfservice.password

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.*
import org.mockito.kotlin.*
import org.scriptonbasestar.kcexts.selfservice.test.SelfServiceTestFixtures

/**
 * Unit tests for PasswordResource using Mockito
 *
 * Note: These tests focus on input validation and password policy logic.
 * Full authentication flow tests are better suited for integration tests
 * with TestContainers due to complexity of mocking Keycloak's internal types.
 */
class PasswordResourceTest {
    private lateinit var session: KeycloakSession
    private lateinit var realm: RealmModel
    private lateinit var context: KeycloakContext
    private lateinit var resource: PasswordResource

    @BeforeEach
    fun setup() {
        realm = SelfServiceTestFixtures.createMockRealm()
        session = SelfServiceTestFixtures.createMockSession(realm = realm)
        context = session.context

        resource = PasswordResource(session)
    }

    @Test
    fun `getPasswordPolicy should return policy with defaults when no policy configured`() {
        // Given
        whenever(realm.passwordPolicy).thenReturn(null)

        // When
        val response = resource.getPasswordPolicy()

        // Then
        assertEquals(200, response.status)
        assertNotNull(response.entity)
    }

    @Test
    fun `getPasswordPolicy should return policy from realm when configured`() {
        // Given
        val passwordPolicy = mock<org.keycloak.models.PasswordPolicy>()
        whenever(realm.passwordPolicy).thenReturn(passwordPolicy)
        whenever(passwordPolicy.hashAlgorithm).thenReturn("pbkdf2-sha256")
        whenever(passwordPolicy.hashIterations).thenReturn(27500)

        // When
        val response = resource.getPasswordPolicy()

        // Then
        assertEquals(200, response.status)
        assertNotNull(response.entity)
        // Note: hashAlgorithm called twice (Elvis operator + string interpolation)
    }

    @Test
    fun `changePassword should fail without authentication`() {
        // Given
        whenever(context.authenticationSession).thenReturn(null)
        whenever(session.sessions()).thenReturn(mock())

        val request =
            ChangePasswordRequest(
                currentPassword = "OldPassword123!",
                newPassword = "NewPassword456!",
                confirmPassword = "NewPassword456!",
            )

        // When
        val response = resource.changePassword(mock(), request)

        // Then
        assertEquals(401, response.status)
    }

    /**
     * Note: Additional tests for password change flow require mocking
     * Keycloak's internal types (ClientConnection, SubjectCredentialManager)
     * which are not easily accessible in unit tests.
     *
     * For comprehensive testing of the password change flow, use integration
     * tests with TestContainers that spin up actual Keycloak instances.
     *
     * Unit tests here focus on:
     * - Password policy retrieval
     * - Basic authentication requirement
     * - Input validation (handled by implementation)
     */
}
