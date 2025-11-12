package org.scriptonbasestar.kcexts.metering.processor

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.events.common.model.KeycloakEvent
import org.scriptonbasestar.kcexts.metering.metrics.MetricsExporter
import org.scriptonbasestar.kcexts.metering.model.UserEventMetric
import org.scriptonbasestar.kcexts.metering.storage.StorageBackend

class EventProcessorTest {
    private lateinit var storage: StorageBackend
    private lateinit var metricsExporter: MetricsExporter
    private lateinit var processor: EventProcessor

    @BeforeEach
    fun setup() {
        storage = mock<StorageBackend>()
        metricsExporter = mock<MetricsExporter>()
        processor = EventProcessor(storage, metricsExporter)
    }

    @Test
    fun `processUserEvent should store event and update metrics`() {
        // Given
        val event =
            KeycloakEvent(
                id = "event-123",
                time = System.currentTimeMillis(),
                type = "LOGIN",
                realmId = "test-realm",
                clientId = "test-client",
                userId = "user-123",
                sessionId = "session-456",
                ipAddress = "192.168.1.100",
                details = emptyMap(),
            )

        // When
        processor.processUserEvent(event)

        // Then
        verify(storage).storeUserEvent(any<UserEventMetric>())
        verify(metricsExporter).recordEvent(
            eventType = "LOGIN",
            realmId = "test-realm",
            clientId = "test-client",
            success = true,
        )
    }

    @Test
    fun `processUserEvent should handle missing clientId`() {
        // Given
        val event =
            KeycloakEvent(
                id = "event-456",
                time = System.currentTimeMillis(),
                type = "REGISTER",
                realmId = "test-realm",
                clientId = null,
                userId = "user-456",
                sessionId = null,
                ipAddress = "192.168.1.200",
                details = emptyMap(),
            )

        // When
        processor.processUserEvent(event)

        // Then
        verify(storage).storeUserEvent(any<UserEventMetric>())
        verify(metricsExporter).recordEvent(
            eventType = "REGISTER",
            realmId = "test-realm",
            clientId = "unknown",
            success = true,
        )
    }

    @Test
    fun `processUserEvent should record error when storage fails`() {
        // Given
        val event =
            KeycloakEvent(
                id = "event-789",
                time = System.currentTimeMillis(),
                type = "LOGIN",
                realmId = "test-realm",
                clientId = "test-client",
                userId = "user-789",
                sessionId = "session-789",
                ipAddress = "192.168.1.150",
                details = emptyMap(),
            )

        whenever(storage.storeUserEvent(any())).thenThrow(RuntimeException("Storage error"))

        // When
        processor.processUserEvent(event)

        // Then
        verify(storage).storeUserEvent(any<UserEventMetric>())
        verify(metricsExporter).recordError("LOGIN", "RuntimeException")
    }

    @Test
    fun `processUserEventsBatch should store events in batch`() {
        // Given
        val events =
            listOf(
                KeycloakEvent(
                    id = "event-1",
                    time = System.currentTimeMillis(),
                    type = "LOGIN",
                    realmId = "realm1",
                    clientId = "client1",
                    userId = "user1",
                    sessionId = "session1",
                    ipAddress = "192.168.1.1",
                    details = emptyMap(),
                ),
                KeycloakEvent(
                    id = "event-2",
                    time = System.currentTimeMillis(),
                    type = "LOGOUT",
                    realmId = "realm1",
                    clientId = "client1",
                    userId = "user1",
                    sessionId = "session1",
                    ipAddress = "192.168.1.1",
                    details = emptyMap(),
                ),
            )

        // When
        processor.processUserEventsBatch(events)

        // Then
        verify(storage).batchStoreUserEvents(argThat { metrics -> metrics.size == 2 })
        verify(metricsExporter, times(2)).recordEvent(any(), any(), any(), any())
    }

    @Test
    fun `processUserEventsBatch should handle empty list`() {
        // Given
        val events = emptyList<KeycloakEvent>()

        // When
        processor.processUserEventsBatch(events)

        // Then
        verify(storage, never()).batchStoreUserEvents(any())
        verify(metricsExporter, never()).recordEvent(any(), any(), any(), any())
    }
}
