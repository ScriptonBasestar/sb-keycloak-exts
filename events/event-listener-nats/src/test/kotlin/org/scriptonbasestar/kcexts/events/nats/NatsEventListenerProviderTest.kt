package org.scriptonbasestar.kcexts.events.nats

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
import org.scriptonbasestar.kcexts.events.nats.metrics.NatsEventMetrics

/**
 * Unit tests for NatsEventListenerProvider.
 *
 * Refactored to use common test utilities from event-listener-common.
 */
class NatsEventListenerProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var config: NatsEventListenerConfig
    private lateinit var connectionManager: NatsConnectionManager
    private lateinit var metrics: NatsEventMetrics
    private lateinit var provider: NatsEventListenerProvider

    private fun createProvider(configOverride: NatsEventListenerConfig): NatsEventListenerProvider {
        val env = TestConfigurationBuilders.createTestEnvironment()
        val batchProcessor = TestConfigurationBuilders.createBatchProcessor<NatsEventMessage>()

        return NatsEventListenerProvider(
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
            NatsEventListenerConfig(
                serverUrl = "nats://localhost:4222",
                userEventSubject = "test.events.user",
                adminEventSubject = "test.events.admin",
                enableUserEvents = true,
                enableAdminEvents = true,
                includedEventTypes = emptySet(),
            )
        connectionManager = mock()
        metrics = NatsEventMetrics()
        provider = createProvider(config)
    }

    @Test
    fun `should process user event successfully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doNothing().whenever(connectionManager).publish(any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        verify(connectionManager, times(1)).publish(any(), any())
    }

    @Test
    fun `should skip user event when disabled`() {
        val disabledConfig = config.copy(enableUserEvents = false)
        val provider = createProvider(disabledConfig)
        val event = KeycloakEventTestFixtures.createUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).publish(any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        val filteredConfig = config.copy(includedEventTypes = setOf("REGISTER"))
        val provider = createProvider(filteredConfig)
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)

        provider.onEvent(loginEvent)

        verify(connectionManager, never()).publish(any(), any())
    }

    @Test
    fun `should allow included event types`() {
        val filteredConfig = config.copy(includedEventTypes = setOf("LOGIN"))
        val provider = createProvider(filteredConfig)
        val loginEvent = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        doNothing().whenever(connectionManager).publish(any(), any())

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).publish(any(), any())
    }

    @Test
    fun `should handle user event errors gracefully`() {
        val event = KeycloakEventTestFixtures.createUserEvent()
        doThrow(RuntimeException("Connection failed"))
            .whenever(connectionManager).publish(any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        val summary = metrics.getMetricsSummary()
        assert(summary.totalFailed > 0)
    }

    @Test
    fun `should process admin event successfully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doNothing().whenever(connectionManager).publish(any(), any())

        assertDoesNotThrow {
            provider.onEvent(adminEvent, false)
        }

        verify(connectionManager, times(1)).publish(any(), any())
    }

    @Test
    fun `should skip admin event when disabled`() {
        val disabledConfig = config.copy(enableAdminEvents = false)
        val provider = createProvider(disabledConfig)
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        provider.onEvent(adminEvent, false)

        verify(connectionManager, never()).publish(any(), any())
    }

    @Test
    fun `should handle admin event errors gracefully`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        doThrow(RuntimeException("Connection failed"))
            .whenever(connectionManager).publish(any(), any())

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
    fun `should generate correct subject for user events`() {
        val event = KeycloakEventTestFixtures.createUserEvent(type = EventType.LOGIN)
        var capturedSubject = ""
        var capturedMessage = ""

        whenever(connectionManager.publish(any(), any())).then { invocation ->
            capturedSubject = invocation.getArgument(0)
            capturedMessage = invocation.getArgument(1)
        }

        provider.onEvent(event)

        assert(capturedSubject.contains("test.events.user"))
        assert(capturedSubject.contains("test-realm"))
        assert(capturedSubject.contains("LOGIN"))
        assert(capturedMessage.isNotEmpty())
    }

    @Test
    fun `should generate correct subject for admin events`() {
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
        var capturedSubject = ""

        whenever(connectionManager.publish(any(), any())).then { invocation ->
            capturedSubject = invocation.getArgument(0)
        }

        provider.onEvent(adminEvent, false)

        assert(capturedSubject.contains("test.events.admin"))
        assert(capturedSubject.contains("test-realm"))
        assert(capturedSubject.contains("CREATE"))
    }

    @Test
    fun `should include representation when requested`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                representation = """{"username":"testuser"}"""
            }
        var capturedMessage = ""

        whenever(connectionManager.publish(any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1)
        }

        provider.onEvent(adminEvent, true)

        assert(capturedMessage.contains("representation"))
    }
}
