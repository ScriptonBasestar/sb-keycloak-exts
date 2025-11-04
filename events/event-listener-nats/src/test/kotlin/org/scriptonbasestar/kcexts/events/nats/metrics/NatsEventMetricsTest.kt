package org.scriptonbasestar.kcexts.events.nats.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NatsEventMetricsTest {
    private lateinit var metrics: NatsEventMetrics

    @BeforeEach
    fun setup() {
        metrics = NatsEventMetrics()
    }

    @Test
    fun `should record events sent`() {
        metrics.recordEventSent(
            eventType = "LOGIN",
            realm = "master",
            destination = "keycloak.events.user",
            sizeBytes = 256,
        )

        val summary = metrics.getMetricsSummary()
        assertEquals(1L, summary.totalSent)
        assertEquals(0L, summary.totalFailed)
    }

    @Test
    fun `should record events failed`() {
        metrics.recordEventFailed(
            eventType = "LOGIN",
            realm = "master",
            destination = "keycloak.events.user",
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
                destination = "keycloak.events.user",
                sizeBytes = 100,
            )
        }

        repeat(2) {
            metrics.recordEventFailed(
                eventType = "LOGOUT",
                realm = "master",
                destination = "keycloak.events.user",
                errorType = "TimeoutException",
            )
        }

        val summary = metrics.getMetricsSummary()
        assertEquals(5L, summary.totalSent)
        assertEquals(2L, summary.totalFailed)
    }

    @Test
    fun `should track timer samples`() {
        val sample = metrics.startTimer()
        Thread.sleep(10)
        metrics.stopTimer(sample, "LOGIN")

        val summary = metrics.getMetricsSummary()
        assertTrue(summary.avgLatencyMs >= 0)
    }

    @Test
    fun `should build event type breakdown`() {
        metrics.recordEventSent("LOGIN", "master", "subject", 100)
        metrics.recordEventSent("LOGOUT", "master", "subject", 150)
        metrics.recordEventSent("LOGIN", "master", "subject", 200)

        val summary = metrics.getMetricsSummary()
        assertEquals(3L, summary.totalSent)

        val breakdown = summary.eventsByType
        assertTrue(breakdown.containsKey("LOGIN:master:subject"))
        assertTrue(breakdown.containsKey("LOGOUT:master:subject"))
    }

    @Test
    fun `should build errors by type`() {
        metrics.recordEventFailed("LOGIN", "master", "subject", "IOException")
        metrics.recordEventFailed("LOGOUT", "master", "subject", "TimeoutException")
        metrics.recordEventFailed("LOGIN", "master", "subject", "IOException")

        val summary = metrics.getMetricsSummary()
        assertEquals(3L, summary.totalFailed)

        val errorsByType = summary.errorsByType
        assertTrue(errorsByType.containsKey("LOGIN:master:subject:IOException"))
        assertTrue(errorsByType.containsKey("LOGOUT:master:subject:TimeoutException"))
    }

    @Test
    fun `should reset metrics`() {
        metrics.recordEventSent("LOGIN", "master", "subject", 100)
        metrics.recordEventFailed("LOGOUT", "master", "subject", "Error")

        metrics.reset()

        val summary = metrics.getMetricsSummary()
        assertEquals(0L, summary.totalSent)
        assertEquals(0L, summary.totalFailed)
    }

    @Test
    fun `should log metrics without errors`() {
        metrics.recordEventSent("LOGIN", "master", "subject", 256)
        metrics.recordEventFailed("LOGOUT", "master", "subject", "Error")

        // Should not throw exception
        metrics.logMetrics()
    }
}
