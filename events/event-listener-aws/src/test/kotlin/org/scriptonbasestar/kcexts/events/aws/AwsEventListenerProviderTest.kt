package org.scriptonbasestar.kcexts.events.aws

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
import org.scriptonbasestar.kcexts.events.aws.config.AwsEventListenerConfig
import org.scriptonbasestar.kcexts.events.aws.metrics.AwsEventMetrics
import org.scriptonbasestar.kcexts.events.common.test.KeycloakEventTestFixtures
import org.scriptonbasestar.kcexts.events.common.test.TestConfigurationBuilders

/**
 * Unit tests for AwsEventListenerProvider.
 *
 * Uses common test utilities from event-listener-common.
 */
class AwsEventListenerProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var config: AwsEventListenerConfig
    private lateinit var connectionManager: AwsConnectionManager
    private lateinit var metrics: AwsEventMetrics
    private lateinit var provider: AwsEventListenerProvider

    private fun createProvider(configOverride: AwsEventListenerConfig = config): AwsEventListenerProvider {
        val env = TestConfigurationBuilders.createTestEnvironment()
        val batchProcessor = TestConfigurationBuilders.createBatchProcessor<AwsEventMessage>()

        return AwsEventListenerProvider(
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
        metrics = AwsEventMetrics()

        // Setup default config behavior
        whenever(config.enableUserEvents).thenReturn(true)
        whenever(config.enableAdminEvents).thenReturn(true)
        whenever(config.useSqs).thenReturn(true)
        whenever(config.useSns).thenReturn(false)
        whenever(config.sqsUserEventsQueueUrl).thenReturn("https://sqs.us-east-1.amazonaws.com/123456789012/keycloak-user-events")
        whenever(config.sqsAdminEventsQueueUrl).thenReturn("https://sqs.us-east-1.amazonaws.com/123456789012/keycloak-admin-events")
        whenever(config.includedEventTypes).thenReturn(setOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER))

        provider = createProvider()
    }

    @Test
    fun `should process user event successfully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        whenever(connectionManager.sendToSqs(any(), any(), any())).thenReturn("message-id-123")

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        verify(connectionManager, times(1)).sendToSqs(any(), any(), any())
    }

    @Test
    fun `should skip user event when disabled`() {
        whenever(config.enableUserEvents).thenReturn(false)
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).sendToSqs(any(), any(), any())
        verify(connectionManager, never()).sendToSns(any(), any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        val registerEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.REGISTER)
        val updatePasswordEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.UPDATE_PASSWORD)
        whenever(connectionManager.sendToSqs(any(), any(), any())).thenReturn("message-id-123")

        // REGISTER is in includedEventTypes
        provider.onEvent(registerEvent)
        verify(connectionManager, times(1)).sendToSqs(any(), any(), any())

        // UPDATE_PASSWORD is not in includedEventTypes - should be skipped
        provider.onEvent(updatePasswordEvent)
        verify(connectionManager, times(1)).sendToSqs(any(), any(), any()) // Still 1, not 2
    }

    @Test
    fun `should allow included event types`() {
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        whenever(connectionManager.sendToSqs(any(), any(), any())).thenReturn("message-id-123")

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).sendToSqs(any(), any(), any())
    }

    @Test
    fun `should handle user event errors gracefully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        whenever(connectionManager.sendToSqs(any(), any(), any())).thenThrow(RuntimeException("AWS connection failed"))

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should process admin event successfully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        whenever(connectionManager.sendToSqs(any(), any(), any())).thenReturn("message-id-123")

        assertDoesNotThrow {
            provider.onEvent(adminEvent, false)
        }

        verify(connectionManager, times(1)).sendToSqs(any(), any(), any())
    }

    @Test
    fun `should skip admin event when disabled`() {
        whenever(config.enableAdminEvents).thenReturn(false)
        val provider = createProvider()
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        provider.onEvent(adminEvent, false)

        verify(connectionManager, never()).sendToSqs(any(), any(), any())
        verify(connectionManager, never()).sendToSns(any(), any(), any())
    }

    @Test
    fun `should handle admin event errors gracefully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        whenever(connectionManager.sendToSqs(any(), any(), any())).thenThrow(RuntimeException("AWS connection failed"))

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
    fun `should send to SQS when useSqs is enabled`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        whenever(connectionManager.sendToSqs(any(), any(), any())).thenReturn("message-id-123")

        provider.onEvent(event)

        verify(connectionManager, times(1)).sendToSqs(any(), any(), any())
        verify(connectionManager, never()).sendToSns(any(), any(), any())
    }

    @Test
    fun `should send to SNS when useSns is enabled`() {
        whenever(config.useSqs).thenReturn(false)
        whenever(config.useSns).thenReturn(true)
        whenever(config.snsUserEventsTopicArn).thenReturn("arn:aws:sns:us-east-1:123456789012:keycloak-user-events")
        val provider = createProvider()

        val event = KeycloakEventTestFixtures.createUserEvent()
        whenever(connectionManager.sendToSns(any(), any(), any())).thenReturn("message-id-456")

        provider.onEvent(event)

        verify(connectionManager, times(1)).sendToSns(any(), any(), any())
        verify(connectionManager, never()).sendToSqs(any(), any(), any())
    }

    @Test
    fun `should include representation when requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedMessage = ""

        whenever(connectionManager.sendToSqs(any(), any(), any())).then { invocation ->
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

        whenever(connectionManager.sendToSqs(any(), any(), any())).then { invocation ->
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

        whenever(connectionManager.sendToSqs(any(), any(), any())).then { invocation ->
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
        whenever(connectionManager.sendToSqs(any(), any(), any())).thenReturn("message-id-123")

        // Test all included event types
        val eventTypes = listOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER)

        eventTypes.forEach { eventType ->
            val event = KeycloakEventTestFixtures.createUserEvent(type = eventType)
            provider.onEvent(event)
        }

        verify(connectionManager, times(eventTypes.size)).sendToSqs(any(), any(), any())
    }
}
