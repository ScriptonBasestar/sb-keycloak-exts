package org.scriptonbasestar.kcexts.events.kafka

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.events.common.test.KeycloakEventTestFixtures
import org.scriptonbasestar.kcexts.events.common.test.TestConfigurationBuilders
import org.scriptonbasestar.kcexts.events.kafka.metrics.KafkaEventMetrics

/**
 * Unit tests for KafkaEventListenerProvider.
 *
 * Uses common test utilities from event-listener-common.
 */
class KafkaEventListenerProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var config: KafkaEventListenerConfig
    private lateinit var connectionManager: KafkaConnectionManager
    private lateinit var metrics: KafkaEventMetrics
    private lateinit var provider: KafkaEventListenerProvider

    private fun createProvider(configOverride: KafkaEventListenerConfig = config): KafkaEventListenerProvider {
        val env = TestConfigurationBuilders.createTestEnvironment()
        val batchProcessor = TestConfigurationBuilders.createBatchProcessor<KafkaEventMessage>()

        return KafkaEventListenerProvider(
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
        metrics = KafkaEventMetrics()

        // Setup default config behavior
        whenever(config.enableUserEvents).thenReturn(true)
        whenever(config.enableAdminEvents).thenReturn(true)
        whenever(config.eventTopic).thenReturn("keycloak-events")
        whenever(config.adminEventTopic).thenReturn("keycloak-admin-events")
        whenever(config.includedEventTypes).thenReturn(setOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER))

        provider = createProvider()
    }

    @Test
    fun `should process user event successfully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).sendEvent(any(), any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        verify(connectionManager, times(1)).sendEvent(eq("keycloak-events"), any(), any())
    }

    @Test
    fun `should skip user event when disabled`() {
        whenever(config.enableUserEvents).thenReturn(false)
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).sendEvent(any(), any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        val registerEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.REGISTER)
        val updatePasswordEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.UPDATE_PASSWORD)
        doNothing().whenever(connectionManager).sendEvent(any(), any(), any())

        // REGISTER is in includedEventTypes
        provider.onEvent(registerEvent)
        verify(connectionManager, times(1)).sendEvent(any(), any(), any())

        // UPDATE_PASSWORD is not in includedEventTypes - should be skipped
        provider.onEvent(updatePasswordEvent)
        verify(connectionManager, times(1)).sendEvent(any(), any(), any()) // Still 1, not 2
    }

    @Test
    fun `should allow included event types`() {
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        doNothing().whenever(connectionManager).sendEvent(any(), any(), any())

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).sendEvent(eq("keycloak-events"), any(), any())
    }

    @Test
    fun `should handle user event errors gracefully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doThrow(RuntimeException("Kafka connection failed"))
            .whenever(connectionManager)
            .sendEvent(any(), any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should process admin event successfully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doNothing().whenever(connectionManager).sendEvent(any(), any(), any())

        assertDoesNotThrow {
            provider.onEvent(adminEvent, false)
        }

        verify(connectionManager, times(1)).sendEvent(eq("keycloak-admin-events"), any(), any())
    }

    @Test
    fun `should skip admin event when disabled`() {
        whenever(config.enableAdminEvents).thenReturn(false)
        val provider = createProvider()
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        provider.onEvent(adminEvent, false)

        verify(connectionManager, never()).sendEvent(any(), any(), any())
    }

    @Test
    fun `should handle admin event errors gracefully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doThrow(RuntimeException("Kafka connection failed"))
            .whenever(connectionManager)
            .sendEvent(any(), any(), any())

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
    fun `should generate correct key for user events`() {
        val event = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN, userId = "user-123")
        var capturedKey = ""
        var capturedValue = ""

        whenever(connectionManager.sendEvent(any(), any(), any())).then { invocation ->
            capturedKey = invocation.getArgument(1) // key is second argument
            capturedValue = invocation.getArgument(2) // value is third argument
        }

        provider.onEvent(event)

        assert(capturedKey.contains("test-realm"))
        assert(capturedKey.contains("LOGIN"))
        assert(capturedKey.contains("user-123"))
        assert(capturedValue.isNotEmpty())
    }

    @Test
    fun `should send to correct topic for user events`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).sendEvent(any(), any(), any())

        provider.onEvent(event)

        verify(connectionManager).sendEvent(eq("keycloak-events"), any(), any())
    }

    @Test
    fun `should send to correct topic for admin events`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doNothing().whenever(connectionManager).sendEvent(any(), any(), any())

        provider.onEvent(adminEvent, false)

        verify(connectionManager).sendEvent(eq("keycloak-admin-events"), any(), any())
    }

    @Test
    fun `should include representation when requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedValue = ""

        whenever(connectionManager.sendEvent(any(), any(), any())).then { invocation ->
            capturedValue = invocation.getArgument(2) // value is third argument
        }

        provider.onEvent(adminEvent, true)

        assert(capturedValue.contains("representation"))
        assert(capturedValue.contains("testuser"))
    }

    @Test
    fun `should exclude representation when not requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedValue = ""

        whenever(connectionManager.sendEvent(any(), any(), any())).then { invocation ->
            capturedValue = invocation.getArgument(2) // value is third argument
        }

        provider.onEvent(adminEvent, false)

        // representation field should be null in JSON
        assert(capturedValue.contains("\"representation\":null"))
    }

    @Test
    fun `should serialize event to JSON`() {
        val event =
            KeycloakEventTestFixtures.createUserEvent(
                type = EventType.LOGIN,
                realmId = "test-realm",
                userId = "user-123",
            )
        var capturedValue = ""

        whenever(connectionManager.sendEvent(any(), any(), any())).then { invocation ->
            capturedValue = invocation.getArgument(2) // value is third argument
        }

        provider.onEvent(event)

        // Verify JSON structure
        assert(capturedValue.contains("\"type\":\"LOGIN\""))
        assert(capturedValue.contains("\"realmId\":\"test-realm\""))
        assert(capturedValue.contains("\"userId\":\"user-123\""))
    }

    @Test
    fun `should handle all common event types`() {
        doNothing().whenever(connectionManager).sendEvent(any(), any(), any())

        // Test all included event types
        val eventTypes = listOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER)

        eventTypes.forEach { eventType ->
            val event = KeycloakEventTestFixtures.createUserEvent(type = eventType)
            provider.onEvent(event)
        }

        verify(connectionManager, times(eventTypes.size)).sendEvent(any(), any(), any())
    }
}
