package org.scriptonbasestar.kcexts.events.azure

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
import org.scriptonbasestar.kcexts.events.azure.config.AzureEventListenerConfig
import org.scriptonbasestar.kcexts.events.azure.metrics.AzureEventMetrics
import org.scriptonbasestar.kcexts.events.common.test.KeycloakEventTestFixtures
import org.scriptonbasestar.kcexts.events.common.test.TestConfigurationBuilders

/**
 * Unit tests for AzureEventListenerProvider.
 *
 * Uses common test utilities from event-listener-common.
 */
class AzureEventListenerProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var config: AzureEventListenerConfig
    private lateinit var connectionManager: AzureConnectionManager
    private lateinit var senderKey: String
    private lateinit var metrics: AzureEventMetrics
    private lateinit var provider: AzureEventListenerProvider

    private fun createProvider(configOverride: AzureEventListenerConfig = config): AzureEventListenerProvider {
        val env = TestConfigurationBuilders.createTestEnvironment()
        val batchProcessor = TestConfigurationBuilders.createBatchProcessor<AzureEventMessage>()

        return AzureEventListenerProvider(
            session,
            configOverride,
            connectionManager,
            senderKey,
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
        senderKey = "test-sender"
        metrics = AzureEventMetrics()

        // Setup default config behavior
        whenever(config.enableUserEvents).thenReturn(true)
        whenever(config.enableAdminEvents).thenReturn(true)
        whenever(config.useQueue).thenReturn(true)
        whenever(config.useTopic).thenReturn(false)
        whenever(config.userEventsQueueName).thenReturn("keycloak-user-events")
        whenever(config.adminEventsQueueName).thenReturn("keycloak-admin-events")
        whenever(config.includedEventTypes).thenReturn(setOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER))

        provider = createProvider()
    }

    @Test
    fun `should process user event successfully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).sendToQueue(any(), any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        verify(connectionManager, times(1)).sendToQueue(eq("keycloak-user-events"), any(), any())
    }

    @Test
    fun `should skip user event when disabled`() {
        whenever(config.enableUserEvents).thenReturn(false)
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).sendToQueue(any(), any(), any())
        verify(connectionManager, never()).sendToTopic(any(), any(), any())
    }

    @Test
    fun `should skip user event when no destination configured`() {
        whenever(config.useQueue).thenReturn(false)
        whenever(config.useTopic).thenReturn(false)
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).sendToQueue(any(), any(), any())
        verify(connectionManager, never()).sendToTopic(any(), any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        val registerEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.REGISTER)
        val updatePasswordEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.UPDATE_PASSWORD)
        doNothing().whenever(connectionManager).sendToQueue(any(), any(), any())

        // REGISTER is in includedEventTypes
        provider.onEvent(registerEvent)
        verify(connectionManager, times(1)).sendToQueue(any(), any(), any())

        // UPDATE_PASSWORD is not in includedEventTypes - should be skipped
        provider.onEvent(updatePasswordEvent)
        verify(connectionManager, times(1)).sendToQueue(any(), any(), any()) // Still 1, not 2
    }

    @Test
    fun `should allow included event types`() {
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        doNothing().whenever(connectionManager).sendToQueue(any(), any(), any())

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).sendToQueue(eq("keycloak-user-events"), any(), any())
    }

    @Test
    fun `should handle user event errors gracefully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doThrow(RuntimeException("Azure connection failed"))
            .whenever(connectionManager).sendToQueue(any(), any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should process admin event successfully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doNothing().whenever(connectionManager).sendToQueue(any(), any(), any())

        assertDoesNotThrow {
            provider.onEvent(adminEvent, false)
        }

        verify(connectionManager, times(1)).sendToQueue(eq("keycloak-admin-events"), any(), any())
    }

    @Test
    fun `should skip admin event when disabled`() {
        whenever(config.enableAdminEvents).thenReturn(false)
        val provider = createProvider()
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        provider.onEvent(adminEvent, false)

        verify(connectionManager, never()).sendToQueue(any(), any(), any())
        verify(connectionManager, never()).sendToTopic(any(), any(), any())
    }

    @Test
    fun `should handle admin event errors gracefully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doThrow(RuntimeException("Azure connection failed"))
            .whenever(connectionManager).sendToQueue(any(), any(), any())

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
    fun `should send to queue when useQueue is enabled`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).sendToQueue(any(), any(), any())

        provider.onEvent(event)

        verify(connectionManager, times(1)).sendToQueue(any(), any(), any())
        verify(connectionManager, never()).sendToTopic(any(), any(), any())
    }

    @Test
    fun `should send to topic when useTopic is enabled`() {
        whenever(config.useQueue).thenReturn(false)
        whenever(config.useTopic).thenReturn(true)
        whenever(config.userEventsTopicName).thenReturn("keycloak-user-events-topic")
        val provider = createProvider()

        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).sendToTopic(any(), any(), any())

        provider.onEvent(event)

        verify(connectionManager, times(1)).sendToTopic(eq("keycloak-user-events-topic"), any(), any())
        verify(connectionManager, never()).sendToQueue(any(), any(), any())
    }

    @Test
    fun `should include representation when requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedMessage = ""

        whenever(connectionManager.sendToQueue(any(), any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1) // messageBody is second argument
        }

        provider.onEvent(adminEvent, true)

        assert(capturedMessage.contains("representation"))
        assert(capturedMessage.contains("testuser"))
    }

    @Test
    fun `should exclude representation when not requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedMessage = ""

        whenever(connectionManager.sendToQueue(any(), any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1) // messageBody is second argument
        }

        provider.onEvent(adminEvent, false)

        // representation field should be null in JSON
        assert(capturedMessage.contains("\"representation\":null"))
    }

    @Test
    fun `should serialize event to JSON`() {
        val event = KeycloakEventTestFixtures.createUserEvent(
            type = EventType.LOGIN,
            realmId = "test-realm",
            userId = "user-123",
        )
        var capturedMessage = ""

        whenever(connectionManager.sendToQueue(any(), any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1) // messageBody is second argument
        }

        provider.onEvent(event)

        // Verify JSON structure
        assert(capturedMessage.contains("\"type\":\"LOGIN\""))
        assert(capturedMessage.contains("\"realmId\":\"test-realm\""))
        assert(capturedMessage.contains("\"userId\":\"user-123\""))
    }

    @Test
    fun `should handle all common event types`() {
        doNothing().whenever(connectionManager).sendToQueue(any(), any(), any())

        // Test all included event types
        val eventTypes = listOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER)

        eventTypes.forEach { eventType ->
            val event = KeycloakEventTestFixtures.createUserEvent(type = eventType)
            provider.onEvent(event)
        }

        verify(connectionManager, times(eventTypes.size)).sendToQueue(any(), any(), any())
    }
}
