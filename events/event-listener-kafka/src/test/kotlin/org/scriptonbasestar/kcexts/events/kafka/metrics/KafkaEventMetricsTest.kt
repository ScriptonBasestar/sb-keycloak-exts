package org.scriptonbasestar.kcexts.events.kafka.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KafkaEventMetricsTest {
    private lateinit var metrics: KafkaEventMetrics

    @BeforeEach
    fun setup() {
        metrics = KafkaEventMetrics()
    }

    @Test
    fun `should record events sent using common interface`() {
        metrics.recordEventSent(
            eventType = "LOGIN",
            realm = "master",
            destination = "keycloak-events",
            sizeBytes = 256,
        )

        val summary = metrics.getMetricsSummary()
        assertEquals(1L, summary.totalSent)
        assertEquals(0L, summary.totalFailed)
    }

    @Test
    fun `should record events failed using common interface`() {
        metrics.recordEventFailed(
            eventType = "LOGIN",
            realm = "master",
            destination = "keycloak-events",
            errorType = "IOException",
        )

        val summary = metrics.getMetricsSummary()
        assertEquals(0L, summary.totalSent)
        assertEquals(1L, summary.totalFailed)
    }

    @Test
    fun `should accumulate multiple events`() {
        repeat(5) {
            metrics.recordEventSent(
                eventType = "LOGIN",
                realm = "master",
                destination = "keycloak-events",
                sizeBytes = 100,
            )
        }

        repeat(2) {
            metrics.recordEventFailed(
                eventType = "LOGOUT",
                realm = "master",
                destination = "keycloak-events",
                errorType = "TimeoutException",
            )
        }

        val summary = metrics.getMetricsSummary()
        assertEquals(5L, summary.totalSent)
        assertEquals(2L, summary.totalFailed)
    }

    @Test
    fun `should track latency with timer`() {
        val sample = metrics.startTimer()
        Thread.sleep(10)
        metrics.stopTimer(sample, "LOGIN")

        val summary = metrics.getMetricsSummary()
        assertTrue(summary.avgLatencyMs >= 0)
    }

    @Test
    fun `should build event type breakdown`() {
        metrics.recordEventSent("LOGIN", "master", "topic", 100)
        metrics.recordEventSent("LOGOUT", "master", "topic", 150)
        metrics.recordEventSent("LOGIN", "master", "topic", 200)

        val summary = metrics.getMetricsSummary()
        assertEquals(3L, summary.totalSent)

        val breakdown = summary.eventsByType
        assertTrue(breakdown.containsKey("LOGIN:master:topic"))
        assertTrue(breakdown.containsKey("LOGOUT:master:topic"))
    }

    @Test
    fun `should build errors by type`() {
        metrics.recordEventFailed("LOGIN", "master", "topic", "IOException")
        metrics.recordEventFailed("LOGOUT", "master", "topic", "TimeoutException")
        metrics.recordEventFailed("LOGIN", "master", "topic", "IOException")

        val summary = metrics.getMetricsSummary()
        assertEquals(3L, summary.totalFailed)

        val errorsByType = summary.errorsByType
        assertTrue(errorsByType.containsKey("LOGIN:master:topic:IOException"))
        assertTrue(errorsByType.containsKey("LOGOUT:master:topic:TimeoutException"))
    }

    @Test
    fun `should reset metrics`() {
        metrics.recordEventSent("LOGIN", "master", "topic", 100)
        metrics.recordEventFailed("LOGOUT", "master", "topic", "Error")

        metrics.reset()

        val summary = metrics.getMetricsSummary()
        assertEquals(0L, summary.totalSent)
        assertEquals(0L, summary.totalFailed)
    }

    @Test
    fun `should maintain Kafka-specific metrics`() {
        metrics.recordEventSent("LOGIN", "master", "topic", 256)
        metrics.incrementActiveSessions()
        metrics.updateConnectionStatus(true)

        val kafkaSummary = metrics.getKafkaMetricsSummary()
        assertEquals(1L, kafkaSummary.totalEventsSent)
        assertEquals(1L, kafkaSummary.activeSessions)
        assertTrue(kafkaSummary.connectionStatus)
        assertEquals(256L, kafkaSummary.averageEventSizeBytes)
    }

    @Test
    fun `should log metrics without errors`() {
        metrics.recordEventSent("LOGIN", "master", "topic", 256)
        metrics.recordEventFailed("LOGOUT", "master", "topic", "Error")

        // Should not throw exception
        metrics.logMetricsSummary()
    }
}
