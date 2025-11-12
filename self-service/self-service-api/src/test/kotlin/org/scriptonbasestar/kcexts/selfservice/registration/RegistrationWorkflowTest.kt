package org.scriptonbasestar.kcexts.selfservice.registration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.selfservice.test.SelfServiceTestFixtures
import java.time.Instant
import java.util.stream.Stream

/**
 * Unit tests for RegistrationWorkflow using Mockito
 *
 * Follows the testing pattern from event-listener modules
 */
class RegistrationWorkflowTest {
    private lateinit var session: KeycloakSession
    private lateinit var realm: RealmModel
    private lateinit var users: UserProvider
    private lateinit var user: UserModel
    private lateinit var workflow: RegistrationWorkflow

    @BeforeEach
    fun setup() {
        realm = SelfServiceTestFixtures.createMockRealm()
        users = SelfServiceTestFixtures.createMockUserProvider()
        session = SelfServiceTestFixtures.createMockSession(realm = realm, users = users)
        user = SelfServiceTestFixtures.createMockUser()

        // Mock EmailNotificationService static method calls
        // Note: In real implementation, consider using dependency injection
        workflow = RegistrationWorkflow(session)
    }

    @Test
    fun `register should create user successfully`() {
        // Given
        val request =
            RegistrationRequest(
                username = "testuser",
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                password = "Password123!",
                confirmPassword = "Password123!",
            )

        whenever(users.getUserByUsername(any(), eq("testuser"))).thenReturn(null)
        whenever(users.getUserByEmail(any(), eq("test@example.com"))).thenReturn(null)
        whenever(users.addUser(any(), eq("testuser"))).thenReturn(user)

        // When
        val response = workflow.register(request)

        // Then
        assertEquals(user.id, response.userId)
        assertEquals(RegistrationStatus.PENDING_VERIFICATION, response.status)
        assertTrue(response.verificationRequired)
        verify(users).addUser(realm, "testuser")
    }

    @Test
    fun `register should fail with duplicate username`() {
        // Given
        val request =
            RegistrationRequest(
                username = "existinguser",
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                password = "Password123!",
                confirmPassword = "Password123!",
            )

        whenever(users.getUserByUsername(any(), eq("existinguser"))).thenReturn(user)

        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                workflow.register(request)
            }
        assertEquals("Username already exists", exception.message)
        verify(users, never()).addUser(any(), any())
    }

    @Test
    fun `register should fail with duplicate email`() {
        // Given
        val request =
            RegistrationRequest(
                username = "testuser",
                email = "existing@example.com",
                firstName = "Test",
                lastName = "User",
                password = "Password123!",
                confirmPassword = "Password123!",
            )

        whenever(users.getUserByUsername(any(), eq("testuser"))).thenReturn(null)
        whenever(users.getUserByEmail(any(), eq("existing@example.com"))).thenReturn(user)

        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                workflow.register(request)
            }
        assertEquals("Email already exists", exception.message)
        verify(users, never()).addUser(any(), any())
    }

    @Test
    fun `register should fail with short username`() {
        // Given
        val request =
            RegistrationRequest(
                username = "ab",
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                password = "Password123!",
                confirmPassword = "Password123!",
            )

        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                workflow.register(request)
            }
        assertEquals("Username must be at least 3 characters", exception.message)
    }

    @Test
    fun `register should fail with invalid email`() {
        // Given
        val request =
            RegistrationRequest(
                username = "testuser",
                email = "invalid-email",
                firstName = "Test",
                lastName = "User",
                password = "Password123!",
                confirmPassword = "Password123!",
            )

        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                workflow.register(request)
            }
        assertEquals("Invalid email format", exception.message)
    }

    @Test
    fun `register should fail with short password`() {
        // Given
        val request =
            RegistrationRequest(
                username = "testuser",
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                password = "short",
                confirmPassword = "short",
            )

        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                workflow.register(request)
            }
        assertEquals("Password must be at least 8 characters", exception.message)
    }

    @Test
    fun `register should fail with password mismatch`() {
        // Given
        val request =
            RegistrationRequest(
                username = "testuser",
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                password = "Password123!",
                confirmPassword = "DifferentPassword!",
            )

        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                workflow.register(request)
            }
        assertEquals("Passwords do not match", exception.message)
    }

    @Test
    fun `verifyEmail should activate user successfully`() {
        // Given
        val token = "valid-token"
        val expiry = Instant.now().plusSeconds(3600).toString()

        whenever(user.getFirstAttribute("verificationTokenExpiry")).thenReturn(expiry)
        whenever(users.searchForUserByUserAttributeStream(any(), eq("verificationToken"), eq(token))).thenReturn(
            Stream.of(user),
        )

        // When
        val response = workflow.verifyEmail(token)

        // Then
        assertTrue(response.success)
        assertEquals("Email verified successfully. You can now log in.", response.message)
    }

    @Test
    fun `verifyEmail should fail with invalid token`() {
        // Given
        val token = "invalid-token"

        whenever(users.searchForUserByUserAttributeStream(any(), eq("verificationToken"), eq(token))).thenReturn(
            Stream.empty(),
        )

        // When
        val response = workflow.verifyEmail(token)

        // Then
        assertFalse(response.success)
        assertEquals("Invalid or expired verification token", response.error)
    }

    @Test
    fun `verifyEmail should fail with expired token`() {
        // Given
        val token = "expired-token"
        val expiry = Instant.now().minusSeconds(3600).toString()

        whenever(user.getFirstAttribute("verificationTokenExpiry")).thenReturn(expiry)
        whenever(users.searchForUserByUserAttributeStream(any(), eq("verificationToken"), eq(token))).thenReturn(
            Stream.of(user),
        )

        // When
        val response = workflow.verifyEmail(token)

        // Then
        assertFalse(response.success)
        assertEquals("Verification token has expired. Please request a new one.", response.error)
    }

    @Test
    fun `resendVerification should generate new token`() {
        // Given
        val email = "test@example.com"

        whenever(users.getUserByEmail(any(), eq(email))).thenReturn(user)
        whenever(user.isEmailVerified).thenReturn(false)

        // When
        val result = workflow.resendVerification(email)

        // Then
        assertTrue(result)
    }

    @Test
    fun `resendVerification should fail if email not found`() {
        // Given
        val email = "notfound@example.com"

        whenever(users.getUserByEmail(any(), eq(email))).thenReturn(null)

        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                workflow.resendVerification(email)
            }
        assertEquals("Email not found", exception.message)
    }

    @Test
    fun `resendVerification should fail if email already verified`() {
        // Given
        val email = "verified@example.com"

        whenever(users.getUserByEmail(any(), eq(email))).thenReturn(user)
        whenever(user.isEmailVerified).thenReturn(true)

        // When & Then
        val exception =
            assertThrows<IllegalStateException> {
                workflow.resendVerification(email)
            }
        assertEquals("Email already verified", exception.message)
    }
}
