package org.scriptonbasestar.kcexts.metering.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.scriptonbasestar.kcexts.events.common.model.KeycloakEvent
import java.time.Instant

class UserEventMetricTest {
    @Test
    fun `fromKeycloakEvent should correctly transform login event`() {
        // Given
        val event =
            KeycloakEvent(
                id = "event-123",
                time = 1694123456789L,
                type = "LOGIN",
                realmId = "test-realm",
                clientId = "test-client",
                userId = "user-123",
                sessionId = "session-456",
                ipAddress = "192.168.1.100",
                details = mapOf("username" to "testuser", "auth_method" to "openid-connect"),
            )

        // When
        val metric = UserEventMetric.fromKeycloakEvent(event)

        // Then
        assertEquals(Instant.ofEpochMilli(1694123456789L), metric.timestamp)
        assertEquals("LOGIN", metric.eventType)
        assertEquals("test-realm", metric.realmId)
        assertEquals("test-client", metric.clientId)
        assertEquals("user-123", metric.userId)
        assertEquals("session-456", metric.sessionId)
        assertEquals("192.168.1.100", metric.ipAddress)
        assertTrue(metric.success)
        assertEquals(2, metric.details.size)
        assertEquals("testuser", metric.details["username"])
    }

    @Test
    fun `fromKeycloakEvent should mark error events as failed`() {
        // Given
        val event =
            KeycloakEvent(
                id = "event-456",
                time = System.currentTimeMillis(),
                type = "LOGIN_ERROR",
                realmId = "test-realm",
                clientId = "test-client",
                userId = null,
                sessionId = null,
                ipAddress = "192.168.1.200",
                details = mapOf("error" to "invalid_credentials"),
            )

        // When
        val metric = UserEventMetric.fromKeycloakEvent(event)

        // Then
        assertEquals("LOGIN_ERROR", metric.eventType)
        assertFalse(metric.success)
        assertNull(metric.userId)
        assertNull(metric.sessionId)
    }

    @Test
    fun `fromKeycloakEvent should handle empty details`() {
        // Given
        val event =
            KeycloakEvent(
                id = "event-789",
                time = System.currentTimeMillis(),
                type = "LOGOUT",
                realmId = "test-realm",
                clientId = "test-client",
                userId = "user-789",
                sessionId = "session-789",
                ipAddress = "192.168.1.150",
                details = null,
            )

        // When
        val metric = UserEventMetric.fromKeycloakEvent(event)

        // Then
        assertEquals("LOGOUT", metric.eventType)
        assertTrue(metric.success)
        assertTrue(metric.details.isEmpty())
    }
}
