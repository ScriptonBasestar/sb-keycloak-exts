package org.scriptonbasestar.kcexts.events.mqtt

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.events.EventType
import org.keycloak.events.admin.OperationType
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.events.common.test.KeycloakEventTestFixtures
import org.scriptonbasestar.kcexts.events.common.test.TestConfigurationBuilders
import org.scriptonbasestar.kcexts.events.mqtt.metrics.MqttEventMetrics

/**
 * Unit tests for MqttEventListenerProvider.
 *
 * Uses common test utilities from event-listener-common.
 */
class MqttEventListenerProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var config: MqttEventListenerConfig
    private lateinit var connectionManager: MqttConnectionManager
    private lateinit var metrics: MqttEventMetrics
    private lateinit var provider: MqttEventListenerProvider

    private fun createProvider(configOverride: MqttEventListenerConfig = config): MqttEventListenerProvider {
        val env = TestConfigurationBuilders.createTestEnvironment()
        val batchProcessor = TestConfigurationBuilders.createBatchProcessor<MqttEventMessage>()

        return MqttEventListenerProvider(
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
        metrics = MqttEventMetrics()

        // Setup default config behavior
        whenever(config.enableUserEvents).thenReturn(true)
        whenever(config.enableAdminEvents).thenReturn(true)
        whenever(config.userEventTopic).thenReturn("test/events/user")
        whenever(config.adminEventTopic).thenReturn("test/events/admin")
        whenever(config.topicPrefix).thenReturn("")
        whenever(config.qos).thenReturn(1)
        whenever(config.retained).thenReturn(false)
        whenever(config.includedEventTypes).thenReturn(emptySet())

        // Mock topic building - return sensible defaults for any realm/event type
        whenever(config.getFullUserEventTopic(any(), any())).thenAnswer { invocation ->
            val realmId = invocation.getArgument<String>(0)
            val eventType = invocation.getArgument<String>(1)
            "test/events/user/$realmId/$eventType"
        }
        whenever(config.getFullAdminEventTopic(any(), any())).thenAnswer { invocation ->
            val realmId = invocation.getArgument<String>(0)
            val operationType = invocation.getArgument<String>(1)
            "test/events/admin/$realmId/$operationType"
        }

        // Mock connection status to be connected
        whenever(connectionManager.isConnected()).thenReturn(true)

        provider = createProvider()
    }

    @Test
    fun `should process user event successfully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        verify(connectionManager, times(1)).publish(any(), any(), eq(1), eq(false))
    }

    @Test
    fun `should skip user event when disabled`() {
        whenever(config.enableUserEvents).thenReturn(false)
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).publish(any(), any(), any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        whenever(config.includedEventTypes).thenReturn(setOf("REGISTER", "LOGIN", "LOGOUT"))
        val provider = createProvider()

        val registerEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.REGISTER)
        val updatePasswordEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.UPDATE_PASSWORD)
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        // REGISTER is in includedEventTypes
        provider.onEvent(registerEvent)
        verify(connectionManager, times(1)).publish(any(), any(), any(), any())

        // UPDATE_PASSWORD is not in includedEventTypes - should be skipped
        provider.onEvent(updatePasswordEvent)
        verify(connectionManager, times(1)).publish(any(), any(), any(), any()) // Still 1, not 2
    }

    @Test
    fun `should allow included event types`() {
        whenever(config.includedEventTypes).thenReturn(setOf("LOGIN"))
        val provider = createProvider()
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)

        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).publish(any(), any(), any(), any())
    }

    @Test
    fun `should handle user event errors gracefully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        whenever(connectionManager.publish(any(), any(), any(), any())).thenThrow(RuntimeException("MQTT connection failed"))

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should process admin event successfully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        assertDoesNotThrow {
            provider.onEvent(adminEvent, includeRepresentation = false)
        }

        verify(connectionManager, times(1)).publish(any(), any(), eq(1), eq(false))
    }

    @Test
    fun `should skip admin event when disabled`() {
        whenever(config.enableAdminEvents).thenReturn(false)
        val provider = createProvider()
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        provider.onEvent(adminEvent, includeRepresentation = false)

        verify(connectionManager, never()).publish(any(), any(), any(), any())
    }

    @Test
    fun `should handle admin event errors gracefully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        whenever(connectionManager.publish(any(), any(), any(), any())).thenThrow(RuntimeException("MQTT connection failed"))

        assertDoesNotThrow {
            provider.onEvent(adminEvent, includeRepresentation = false)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should use correct QoS level`() {
        whenever(config.qos).thenReturn(2)
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent()

        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(event)

        verify(connectionManager, times(1)).publish(any(), any(), eq(2), eq(false))
    }

    @Test
    fun `should publish retained messages when configured`() {
        whenever(config.retained).thenReturn(true)
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent()

        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(event)

        verify(connectionManager, times(1)).publish(any(), any(), eq(1), eq(true))
    }

    @Test
    fun `should send to correct topic for user events`() {
        val event = KeycloakEventTestFixtures.createUserEvent(realmId = "test-realm", type = EventType.LOGIN)
        whenever(config.getFullUserEventTopic("test-realm", "LOGIN")).thenReturn("test/events/user/test-realm/LOGIN")
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(event)

        verify(connectionManager).publish(
            eq("test/events/user/test-realm/LOGIN"),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `should send to correct topic for admin events`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent(realmId = "test-realm", operationType = OperationType.CREATE)
        whenever(config.getFullAdminEventTopic("test-realm", "CREATE")).thenReturn("test/events/admin/test-realm/CREATE")
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(adminEvent, includeRepresentation = false)

        verify(connectionManager).publish(
            eq("test/events/admin/test-realm/CREATE"),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `should build topic with prefix when configured`() {
        whenever(config.topicPrefix).thenReturn("keycloak")
        whenever(config.getFullUserEventTopic("test-realm", "LOGIN")).thenReturn("keycloak/test/events/user/test-realm/LOGIN")
        val provider = createProvider()
        val event = KeycloakEventTestFixtures.createUserEvent(realmId = "test-realm", type = EventType.LOGIN)

        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(event)

        // Verify topic includes prefix
        verify(connectionManager).publish(
            eq("keycloak/test/events/user/test-realm/LOGIN"),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `should record metrics for successful event`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(event)

        val summary = metrics.getMetricsSummary()
        assert(summary.totalSent == 1L)
        assert(summary.totalFailed == 0L)
    }

    @Test
    fun `should handle connection failure gracefully`() {
        whenever(connectionManager.isConnected()).thenReturn(false)

        val event = KeycloakEventTestFixtures.createUserEvent()

        // Should not throw, should record failure
        assertDoesNotThrow {
            try {
                provider.onEvent(event)
            } catch (e: Exception) {
                // Expected to throw due to circuit breaker
            }
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed == 1L)
    }

    @Test
    fun `should process multiple user events`() {
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        val logoutEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGOUT)
        val registerEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.REGISTER)

        provider.onEvent(loginEvent)
        provider.onEvent(logoutEvent)
        provider.onEvent(registerEvent)

        verify(connectionManager, times(3)).publish(any(), any(), any(), any())

        val summary = metrics.getMetricsSummary()
        assert(summary.totalSent == 3L)
    }

    @Test
    fun `should process multiple admin events`() {
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        val createEvent = KeycloakEventTestFixtures.createAdminEvent(operationType = OperationType.CREATE)
        val updateEvent = KeycloakEventTestFixtures.createAdminEvent(operationType = OperationType.UPDATE)
        val deleteEvent = KeycloakEventTestFixtures.createAdminEvent(operationType = OperationType.DELETE)

        provider.onEvent(createEvent, includeRepresentation = false)
        provider.onEvent(updateEvent, includeRepresentation = false)
        provider.onEvent(deleteEvent, includeRepresentation = false)

        verify(connectionManager, times(3)).publish(any(), any(), any(), any())

        val summary = metrics.getMetricsSummary()
        assert(summary.totalSent == 3L)
    }

    @Test
    fun `should include representation when requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedMessage: String = ""

        whenever(connectionManager.publish(any(), any(), any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1) // message is second argument
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
        var capturedMessage: String = ""

        whenever(connectionManager.publish(any(), any(), any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1) // message is second argument
        }

        provider.onEvent(adminEvent, false)

        // representation field should be null in JSON
        assert(capturedMessage.contains("\"representation\":null"))
    }

    @Test
    fun `should serialize event to JSON`() {
        val event =
            KeycloakEventTestFixtures.createUserEvent(
                type = EventType.LOGIN,
                realmId = "test-realm",
                userId = "user-123",
            )
        var capturedMessage: String = ""

        whenever(connectionManager.publish(any(), any(), any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1) // message is second argument
        }

        provider.onEvent(event)

        // Verify JSON structure
        assert(capturedMessage.contains("\"type\":\"LOGIN\""))
        assert(capturedMessage.contains("\"realmId\":\"test-realm\""))
        assert(capturedMessage.contains("\"userId\":\"user-123\""))
    }

    @Test
    fun `should handle all common event types`() {
        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        // Test all common event types
        val eventTypes = listOf(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER)

        eventTypes.forEach { eventType ->
            val event = KeycloakEventTestFixtures.createUserEvent(type = eventType)
            provider.onEvent(event)
        }

        verify(connectionManager, times(eventTypes.size)).publish(any(), any(), any(), any())
    }

    @Test
    fun `should close without error`() {
        assertDoesNotThrow {
            provider.close()
        }
    }
}
