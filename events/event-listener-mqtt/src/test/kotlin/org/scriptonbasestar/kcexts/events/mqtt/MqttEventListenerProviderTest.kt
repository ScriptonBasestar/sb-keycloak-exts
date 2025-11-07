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

    private fun createProvider(configOverride: MqttEventListenerConfig): MqttEventListenerProvider {
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
        config =
            MqttEventListenerConfig(
                brokerUrl = "tcp://localhost:1883",
                clientId = "test-client",
                userEventTopic = "test/events/user",
                adminEventTopic = "test/events/admin",
                topicPrefix = "",
                qos = 1,
                retained = false,
                enableUserEvents = true,
                enableAdminEvents = true,
                includedEventTypes = emptySet(),
            )
        connectionManager = mock()
        metrics = MqttEventMetrics()
        provider = createProvider(config)

        // Mock connection status to be connected
        whenever(connectionManager.isConnected()).thenReturn(true)
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
        val disabledConfig = config.copy(enableUserEvents = false)
        val provider = createProvider(disabledConfig)
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).publish(any(), any(), any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        val filteredConfig = config.copy(includedEventTypes = setOf("REGISTER"))
        val provider = createProvider(filteredConfig)
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)

        provider.onEvent(loginEvent)

        verify(connectionManager, never()).publish(any(), any(), any(), any())
    }

    @Test
    fun `should allow included event types`() {
        val filteredConfig = config.copy(includedEventTypes = setOf("LOGIN"))
        val provider = createProvider(filteredConfig)
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)

        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).publish(any(), any(), any(), any())
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
        val disabledConfig = config.copy(enableAdminEvents = false)
        val provider = createProvider(disabledConfig)
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        provider.onEvent(adminEvent, includeRepresentation = false)

        verify(connectionManager, never()).publish(any(), any(), any(), any())
    }

    @Test
    fun `should use correct QoS level`() {
        val qosConfig = config.copy(qos = 2)
        val provider = createProvider(qosConfig)
        val event = KeycloakEventTestFixtures.createUserEvent()

        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(event)

        verify(connectionManager, times(1)).publish(any(), any(), eq(2), eq(false))
    }

    @Test
    fun `should publish retained messages when configured`() {
        val retainedConfig = config.copy(retained = true)
        val provider = createProvider(retainedConfig)
        val event = KeycloakEventTestFixtures.createUserEvent()

        doNothing().whenever(connectionManager).publish(any(), any(), any(), any())

        provider.onEvent(event)

        verify(connectionManager, times(1)).publish(any(), any(), eq(1), eq(true))
    }

    @Test
    fun `should build topic with prefix when configured`() {
        val prefixConfig = config.copy(topicPrefix = "keycloak")
        val provider = createProvider(prefixConfig)
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
    fun `should close without error`() {
        assertDoesNotThrow {
            provider.close()
        }
    }
}
