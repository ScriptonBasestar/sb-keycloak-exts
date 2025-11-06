package org.scriptonbasestar.kcexts.events.redis

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.events.common.test.KeycloakEventTestFixtures
import org.scriptonbasestar.kcexts.events.common.test.TestConfigurationBuilders
import org.scriptonbasestar.kcexts.events.redis.config.RedisEventListenerConfig
import org.scriptonbasestar.kcexts.events.redis.metrics.RedisEventMetrics

/**
 * Unit tests for RedisEventListenerProvider.
 *
 * Uses common test utilities from event-listener-common.
 */
class RedisEventListenerProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var config: RedisEventListenerConfig
    private lateinit var connectionManager: RedisConnectionManager
    private lateinit var metrics: RedisEventMetrics
    private lateinit var provider: RedisEventListenerProvider

    private fun createProvider(configOverride: RedisEventListenerConfig = config): RedisEventListenerProvider {
        val env = TestConfigurationBuilders.createTestEnvironment()
        val batchProcessor = TestConfigurationBuilders.createBatchProcessor<RedisEventMessage>()

        return RedisEventListenerProvider(
            session,
            configOverride,
            connectionManager,
            metrics,
            env.circuitBreaker,
            env.retryPolicy,
            env.deadLetterQueue,
            batchProcessor,
        )
    }

    @BeforeEach
    fun setup() {
        session = mock()
        config = mock()
        connectionManager = mock()
        metrics = RedisEventMetrics()

        // Setup default config behavior
        whenever(config.enableUserEvents).thenReturn(true)
        whenever(config.enableAdminEvents).thenReturn(true)
        whenever(config.userEventsStream).thenReturn("keycloak:events:user")
        whenever(config.adminEventsStream).thenReturn("keycloak:events:admin")
        whenever(config.includedEventTypes).thenReturn(setOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER))
        whenever(config.streamMaxLength).thenReturn(10000L)

        provider = createProvider()
    }

    @Test
    fun `should process user event successfully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        whenever(connectionManager.sendEvent(any(), any())).thenReturn("test-message-id")

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        verify(connectionManager, times(1)).sendEvent(eq("keycloak:events:user"), any())
    }

    @Test
    fun `should skip user event when disabled`() {
        whenever(config.enableUserEvents).thenReturn(false)
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).sendEvent(any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        val registerEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.REGISTER)
        val updatePasswordEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.UPDATE_PASSWORD)
        whenever(connectionManager.sendEvent(any(), any())).thenReturn("test-message-id")

        // REGISTER is in includedEventTypes
        provider.onEvent(registerEvent)
        verify(connectionManager, times(1)).sendEvent(any(), any())

        // UPDATE_PASSWORD is not in includedEventTypes - should be skipped
        provider.onEvent(updatePasswordEvent)
        verify(connectionManager, times(1)).sendEvent(any(), any()) // Still 1, not 2
    }

    @Test
    fun `should allow included event types`() {
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        whenever(connectionManager.sendEvent(any(), any())).thenReturn("test-message-id")

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).sendEvent(eq("keycloak:events:user"), any())
    }

    @Test
    fun `should handle user event errors gracefully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        whenever(connectionManager.sendEvent(any(), any())).thenThrow(RuntimeException("Redis connection failed"))

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should process admin event successfully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        whenever(connectionManager.sendEvent(any(), any())).thenReturn("test-message-id")

        assertDoesNotThrow {
            provider.onEvent(adminEvent, false)
        }

        verify(connectionManager, times(1)).sendEvent(eq("keycloak:events:admin"), any())
    }

    @Test
    fun `should skip admin event when disabled`() {
        whenever(config.enableAdminEvents).thenReturn(false)
        val provider = createProvider()
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        provider.onEvent(adminEvent, false)

        verify(connectionManager, never()).sendEvent(any(), any())
    }

    @Test
    fun `should handle admin event errors gracefully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        whenever(connectionManager.sendEvent(any(), any())).thenThrow(RuntimeException("Redis connection failed"))

        assertDoesNotThrow {
            provider.onEvent(adminEvent, false)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should close provider without errors`() {
        assertDoesNotThrow {
            provider.close()
        }
    }

    @Test
    fun `should send to correct stream for user events`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        whenever(connectionManager.sendEvent(any(), any())).thenReturn("test-message-id")

        provider.onEvent(event)

        verify(connectionManager).sendEvent(eq("keycloak:events:user"), any())
    }

    @Test
    fun `should send to correct stream for admin events`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        whenever(connectionManager.sendEvent(any(), any())).thenReturn("test-message-id")

        provider.onEvent(adminEvent, false)

        verify(connectionManager).sendEvent(eq("keycloak:events:admin"), any())
    }

    @Test
    fun `should include representation when requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedFields: Map<String, String> = emptyMap()

        whenever(connectionManager.sendEvent(any(), any())).then { invocation ->
            capturedFields = invocation.getArgument(1) // fields is second argument
        }

        provider.onEvent(adminEvent, true)

        val dataField = capturedFields["data"] ?: ""
        assert(dataField.contains("representation"))
        assert(dataField.contains("testuser"))
    }

    @Test
    fun `should exclude representation when not requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedFields: Map<String, String> = emptyMap()

        whenever(connectionManager.sendEvent(any(), any())).then { invocation ->
            capturedFields = invocation.getArgument(1) // fields is second argument
        }

        provider.onEvent(adminEvent, false)

        val dataField = capturedFields["data"] ?: ""
        // representation field should be null in JSON
        assert(dataField.contains("\"representation\":null"))
    }

    @Test
    fun `should serialize event to JSON`() {
        val event =
            KeycloakEventTestFixtures.createUserEvent(
                type = EventType.LOGIN,
                realmId = "test-realm",
                userId = "user-123",
            )
        var capturedFields: Map<String, String> = emptyMap()

        whenever(connectionManager.sendEvent(any(), any())).then { invocation ->
            capturedFields = invocation.getArgument(1) // fields is second argument
        }

        provider.onEvent(event)

        val dataField = capturedFields["data"] ?: ""
        // Verify JSON structure
        assert(dataField.contains("\"type\":\"LOGIN\""))
        assert(dataField.contains("\"realmId\":\"test-realm\""))
        assert(dataField.contains("\"userId\":\"user-123\""))

        // Verify stream fields
        assert(capturedFields["type"] == "LOGIN")
        assert(capturedFields["realmId"] == "test-realm")
        assert(capturedFields["userId"] == "user-123")
    }

    @Test
    fun `should handle all common event types`() {
        whenever(connectionManager.sendEvent(any(), any())).thenReturn("test-message-id")

        // Test all included event types
        val eventTypes = listOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER)

        eventTypes.forEach { eventType ->
            val event = KeycloakEventTestFixtures.createUserEvent(type = eventType)
            provider.onEvent(event)
        }

        verify(connectionManager, times(eventTypes.size)).sendEvent(any(), any())
    }
}
