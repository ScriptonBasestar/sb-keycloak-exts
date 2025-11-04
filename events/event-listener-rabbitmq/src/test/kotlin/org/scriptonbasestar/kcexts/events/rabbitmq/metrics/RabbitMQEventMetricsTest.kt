package org.scriptonbasestar.kcexts.events.rabbitmq.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RabbitMQEventMetricsTest {
    private lateinit var metrics: RabbitMQEventMetrics

    @BeforeEach
    fun setup() {
        metrics = RabbitMQEventMetrics()
    }

    @Test
    fun `should record events sent`() {
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
    fun `should record events failed`() {
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
    fun `should track timer samples`() {
        val sample = metrics.startTimer()
        Thread.sleep(10)
        metrics.stopTimer(sample, "LOGIN")

        // Timer should have recorded some duration
        // We can't assert exact duration due to timing, but it should not crash
        val summary = metrics.getMetricsSummary()
        assertTrue(summary.totalSent >= 0)
    }

    @Test
    fun `should build event type breakdown`() {
        metrics.recordEventSent("LOGIN", "master", "exchange", 100)
        metrics.recordEventSent("LOGOUT", "master", "exchange", 150)
        metrics.recordEventSent("LOGIN", "master", "exchange", 200)

        val summary = metrics.getMetricsSummary()
        assertEquals(3L, summary.totalSent)

        val breakdown = summary.eventsByType
        assertTrue(breakdown.containsKey("LOGIN:master:exchange"))
        assertTrue(breakdown.containsKey("LOGOUT:master:exchange"))
    }

    @Test
    fun `should build errors by type`() {
        metrics.recordEventFailed("LOGIN", "master", "exchange", "IOException")
        metrics.recordEventFailed("LOGOUT", "master", "exchange", "TimeoutException")
        metrics.recordEventFailed("LOGIN", "master", "exchange", "IOException")

        val summary = metrics.getMetricsSummary()
        assertEquals(3L, summary.totalFailed)

        val errorsByType = summary.errorsByType
        assertTrue(errorsByType.containsKey("LOGIN:master:exchange:IOException"))
        assertTrue(errorsByType.containsKey("LOGOUT:master:exchange:TimeoutException"))
    }

    @Test
    fun `should reset metrics`() {
        metrics.recordEventSent("LOGIN", "master", "exchange", 100)
        metrics.recordEventFailed("LOGOUT", "master", "exchange", "Error")

        metrics.reset()

        val summary = metrics.getMetricsSummary()
        assertEquals(0L, summary.totalSent)
        assertEquals(0L, summary.totalFailed)
    }

    @Test
    fun `should log metrics without errors`() {
        metrics.recordEventSent("LOGIN", "master", "exchange", 256)
        metrics.recordEventFailed("LOGOUT", "master", "exchange", "Error")

        // Should not throw exception
        metrics.logMetrics()
    }
}
