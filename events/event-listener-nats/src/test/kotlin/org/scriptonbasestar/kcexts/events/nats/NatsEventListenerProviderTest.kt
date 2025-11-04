package org.scriptonbasestar.kcexts.events.nats

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.events.Event
import org.keycloak.events.EventType
import org.keycloak.events.admin.AdminEvent
import org.keycloak.events.admin.AuthDetails
import org.keycloak.events.admin.OperationType
import org.keycloak.models.KeycloakSession
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.events.nats.metrics.NatsEventMetrics

class NatsEventListenerProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var config: NatsEventListenerConfig
    private lateinit var connectionManager: NatsConnectionManager
    private lateinit var metrics: NatsEventMetrics
    private lateinit var provider: NatsEventListenerProvider

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
        provider = NatsEventListenerProvider(session, config, connectionManager, metrics)
    }

    @Test
    fun `should process user event successfully`() {
        val event = createMockUserEvent()
        doNothing().whenever(connectionManager).publish(any(), any())

        assertDoesNotThrow {
            provider.onEvent(event)
        }

        verify(connectionManager, times(1)).publish(any(), any())
    }

    @Test
    fun `should skip user event when disabled`() {
        val disabledConfig = config.copy(enableUserEvents = false)
        val provider = NatsEventListenerProvider(session, disabledConfig, connectionManager, metrics)
        val event = createMockUserEvent()

        provider.onEvent(event)

        verify(connectionManager, never()).publish(any(), any())
    }

    @Test
    fun `should filter user events by type`() {
        val filteredConfig = config.copy(includedEventTypes = setOf("REGISTER"))
        val provider = NatsEventListenerProvider(session, filteredConfig, connectionManager, metrics)
        val loginEvent = createMockUserEvent(EventType.LOGIN)

        provider.onEvent(loginEvent)

        verify(connectionManager, never()).publish(any(), any())
    }

    @Test
    fun `should allow included event types`() {
        val filteredConfig = config.copy(includedEventTypes = setOf("LOGIN"))
        val provider = NatsEventListenerProvider(session, filteredConfig, connectionManager, metrics)
        val loginEvent = createMockUserEvent(EventType.LOGIN)
        doNothing().whenever(connectionManager).publish(any(), any())

        provider.onEvent(loginEvent)

        verify(connectionManager, times(1)).publish(any(), any())
    }

    @Test
    fun `should handle user event errors gracefully`() {
        val event = createMockUserEvent()
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
        val adminEvent = createMockAdminEvent()
        doNothing().whenever(connectionManager).publish(any(), any())

        assertDoesNotThrow {
            provider.onEvent(adminEvent, false)
        }

        verify(connectionManager, times(1)).publish(any(), any())
    }

    @Test
    fun `should skip admin event when disabled`() {
        val disabledConfig = config.copy(enableAdminEvents = false)
        val provider = NatsEventListenerProvider(session, disabledConfig, connectionManager, metrics)
        val adminEvent = createMockAdminEvent()

        provider.onEvent(adminEvent, false)

        verify(connectionManager, never()).publish(any(), any())
    }

    @Test
    fun `should handle admin event errors gracefully`() {
        val adminEvent = createMockAdminEvent()
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
        val event = createMockUserEvent(EventType.LOGIN)
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
        val adminEvent = createMockAdminEvent()
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
        val adminEvent = createMockAdminEvent()
        whenever(adminEvent.representation).thenReturn("{\"username\":\"testuser\"}")
        var capturedMessage = ""

        whenever(connectionManager.publish(any(), any())).then { invocation ->
            capturedMessage = invocation.getArgument(1)
        }

        provider.onEvent(adminEvent, true)

        assert(capturedMessage.contains("representation"))
    }

    private fun createMockUserEvent(type: EventType = EventType.LOGIN): Event {
        val event = mock<Event>()
        whenever(event.type).thenReturn(type)
        whenever(event.time).thenReturn(System.currentTimeMillis())
        whenever(event.realmId).thenReturn("test-realm")
        whenever(event.clientId).thenReturn("test-client")
        whenever(event.userId).thenReturn("test-user")
        whenever(event.sessionId).thenReturn("test-session")
        whenever(event.ipAddress).thenReturn("192.168.1.1")
        whenever(event.details).thenReturn(mapOf("detail1" to "value1"))
        return event
    }

    private fun createMockAdminEvent(): AdminEvent {
        val adminEvent = mock<AdminEvent>()
        val authDetails = mock<AuthDetails>()

        whenever(authDetails.realmId).thenReturn("test-realm")
        whenever(authDetails.clientId).thenReturn("admin-cli")
        whenever(authDetails.userId).thenReturn("admin-user")
        whenever(authDetails.ipAddress).thenReturn("192.168.1.1")

        whenever(adminEvent.time).thenReturn(System.currentTimeMillis())
        whenever(adminEvent.operationType).thenReturn(OperationType.CREATE)
        whenever(adminEvent.realmId).thenReturn("test-realm")
        whenever(adminEvent.authDetails).thenReturn(authDetails)
        whenever(adminEvent.resourcePath).thenReturn("users/test-user-id")
        whenever(adminEvent.representation).thenReturn(null)

        return adminEvent
    }
}
