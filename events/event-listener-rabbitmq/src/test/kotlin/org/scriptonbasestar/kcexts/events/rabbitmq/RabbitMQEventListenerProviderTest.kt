package org.scriptonbasestar.kcexts.events.rabbitmq

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.events.common.test.KeycloakEventTestFixtures
import org.scriptonbasestar.kcexts.events.common.test.TestConfigurationBuilders
import org.scriptonbasestar.kcexts.events.rabbitmq.metrics.RabbitMQEventMetrics

/**
 * Unit tests for RabbitMQEventListenerProvider.
 *
 * Refactored to use common test utilities from event-listener-common.
 */
class RabbitMQEventListenerProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var config: RabbitMQEventListenerConfig
    private lateinit var connectionManager: RabbitMQConnectionManager
    private lateinit var metrics: RabbitMQEventMetrics
    private lateinit var provider: RabbitMQEventListenerProvider

    private fun createProvider(configOverride: RabbitMQEventListenerConfig): RabbitMQEventListenerProvider {
        val env = TestConfigurationBuilders.createTestEnvironment()
        val batchProcessor = TestConfigurationBuilders.createBatchProcessor<RabbitMQEventMessage>()

        return RabbitMQEventListenerProvider(
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
            RabbitMQEventListenerConfig(
                host = "localhost",
                port = 5672,
                username = "guest",
                password = "guest",
                exchangeName = "test-exchange",
                enableUserEvents = true,
                enableAdminEvents = true,
                includedEventTypes = emptySet(),
            )
        connectionManager = mock()
        metrics = RabbitMQEventMetrics()
        provider = createProvider(config)
    }

    @Test
    fun `should process user event successfully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).publishMessage(any(), any())

        assertDoesNotThrow {
            createProvider(config).onEvent(event)
        }

        verify(connectionManager, times(1)).publishMessage(any(), any())
    }

    @Test
    fun `should skip user event when disabled`() {
        val disabledConfig = config.copy(enableUserEvents = false)
        val provider = createProvider(disabledConfig)
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).publishMessage(any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        val filteredConfig = config.copy(includedEventTypes = setOf("REGISTER"))
        val provider = createProvider(filteredConfig)
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)

        provider.onEvent(loginEvent)

        verify(connectionManager, never()).publishMessage(any(), any())
    }

    @Test
    fun `should allow included event types`() {
        val filteredConfig = config.copy(includedEventTypes = setOf("LOGIN"))
        val provider = createProvider(filteredConfig)
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        doNothing().whenever(connectionManager).publishMessage(any(), any())

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).publishMessage(any(), any())
    }

    @Test
    fun `should handle user event errors gracefully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doThrow(RuntimeException("Connection failed"))
            .whenever(connectionManager).publishMessage(any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should process admin event successfully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doNothing().whenever(connectionManager).publishMessage(any(), any())

        assertDoesNotThrow {
            provider.onEvent(adminEvent, false)
        }

        verify(connectionManager, times(1)).publishMessage(any(), any())
    }

    @Test
    fun `should skip admin event when disabled`() {
        val disabledConfig = config.copy(enableAdminEvents = false)
        val provider = createProvider(disabledConfig)
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        provider.onEvent(adminEvent, false)

        verify(connectionManager, never()).publishMessage(any(), any())
    }

    @Test
    fun `should handle admin event errors gracefully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doThrow(RuntimeException("Connection failed"))
            .whenever(connectionManager).publishMessage(any(), any())

        assertDoesNotThrow {
            createProvider(config).onEvent(adminEvent, false)
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
    fun `should generate correct routing key for user events`() {
        val event = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        var capturedRoutingKey = ""
        var capturedMessage = ""

        whenever(connectionManager.publishMessage(any(), any())).then { invocation ->
            capturedRoutingKey = invocation.getArgument(0)
            capturedMessage = invocation.getArgument(1)
        }

        provider.onEvent(event)

        assert(capturedRoutingKey.contains("keycloak.events.user"))
        assert(capturedRoutingKey.contains("test-realm"))
        assert(capturedRoutingKey.contains("LOGIN"))
        assert(capturedMessage.isNotEmpty())
    }

    @Test
    fun `should generate correct routing key for admin events`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        var capturedRoutingKey = ""

        whenever(connectionManager.publishMessage(any(), any())).then { invocation ->
            capturedRoutingKey = invocation.getArgument(0)
        }

        provider.onEvent(adminEvent, false)

        assert(capturedRoutingKey.contains("keycloak.events.admin"))
        assert(capturedRoutingKey.contains("test-realm"))
        assert(capturedRoutingKey.contains("CREATE"))
    }

    @Test
    fun `should include representation when requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedMessage = ""

        whenever(connectionManager.publishMessage(any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1)
        }

        provider.onEvent(adminEvent, true)
        assert(capturedMessage.contains("representation"))
    }

    @Test
    fun `should exclude representation when not requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = "{\"username\":\"testuser\"}"
            }
        var capturedMessage = ""

        whenever(connectionManager.publishMessage(any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1)
        }

        provider.onEvent(adminEvent, false)

        // Should still process, but representation should be stripped
        assert(!capturedMessage.contains("representation"))
    }
}
