package org.scriptonbasestar.kcexts.ratelimit.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RateLimitMetricsTest {
    @BeforeEach
    fun setup() {
        RateLimitMetrics.reset()
    }

    @Test
    fun `should record allowed events`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")

        val counters = RateLimitMetrics.getEventCounters()

        val key = mapOf("realm" to "master", "event_type" to "LOGIN", "result" to "allowed")
        assertEquals(3L, counters[key])
    }

    @Test
    fun `should record denied events`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")

        val counters = RateLimitMetrics.getEventCounters()

        val key = mapOf("realm" to "master", "event_type" to "LOGIN", "result" to "denied")
        assertEquals(2L, counters[key])
    }

    @Test
    fun `should track multiple event types independently`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "UPDATE_PROFILE", "allowed")

        val counters = RateLimitMetrics.getEventCounters()

        val loginKey = mapOf("realm" to "master", "event_type" to "LOGIN", "result" to "allowed")
        val profileKey = mapOf("realm" to "master", "event_type" to "UPDATE_PROFILE", "result" to "allowed")

        assertEquals(2L, counters[loginKey])
        assertEquals(1L, counters[profileKey])
    }

    @Test
    fun `should track multiple realms independently`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("tenant1", "LOGIN", "allowed")

        val counters = RateLimitMetrics.getEventCounters()

        val masterKey = mapOf("realm" to "master", "event_type" to "LOGIN", "result" to "allowed")
        val tenant1Key = mapOf("realm" to "tenant1", "event_type" to "LOGIN", "result" to "allowed")

        assertEquals(2L, counters[masterKey])
        assertEquals(1L, counters[tenant1Key])
    }

    @Test
    fun `should update available permits`() {
        RateLimitMetrics.updateAvailablePermits("master", "PER_USER", "alice", 10)
        RateLimitMetrics.updateAvailablePermits("master", "PER_USER", "alice", 8)

        val permits = RateLimitMetrics.getAvailablePermits()

        val key = mapOf("realm" to "master", "strategy" to "PER_USER", "key" to "alice")
        assertEquals(8L, permits[key])
    }

    @Test
    fun `should track multiple keys independently`() {
        RateLimitMetrics.updateAvailablePermits("master", "PER_USER", "alice", 10)
        RateLimitMetrics.updateAvailablePermits("master", "PER_USER", "bob", 5)

        val permits = RateLimitMetrics.getAvailablePermits()

        val aliceKey = mapOf("realm" to "master", "strategy" to "PER_USER", "key" to "alice")
        val bobKey = mapOf("realm" to "master", "strategy" to "PER_USER", "key" to "bob")

        assertEquals(10L, permits[aliceKey])
        assertEquals(5L, permits[bobKey])
    }

    @Test
    fun `should get total denied events`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")
        RateLimitMetrics.recordEvent("master", "UPDATE_PROFILE", "denied")
        RateLimitMetrics.recordEvent("tenant1", "LOGIN", "denied")

        assertEquals(4L, RateLimitMetrics.getTotalDeniedEvents())
    }

    @Test
    fun `should get total allowed events`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "UPDATE_PROFILE", "allowed")
        RateLimitMetrics.recordEvent("tenant1", "LOGIN", "allowed")

        assertEquals(4L, RateLimitMetrics.getTotalAllowedEvents())
    }

    @Test
    fun `should calculate denial rate`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")

        val denialRate = RateLimitMetrics.getDenialRate("master", "LOGIN")
        assertEquals(0.25, denialRate!!, 0.001) // 1 denied out of 4 total = 25%
    }

    @Test
    fun `should return null denial rate for non-existent metrics`() {
        val denialRate = RateLimitMetrics.getDenialRate("master", "NON_EXISTENT")
        assertNull(denialRate)
    }

    @Test
    fun `should calculate 100% denial rate`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")

        val denialRate = RateLimitMetrics.getDenialRate("master", "LOGIN")
        assertEquals(1.0, denialRate!!, 0.001) // 100%
    }

    @Test
    fun `should calculate 0% denial rate`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")

        val denialRate = RateLimitMetrics.getDenialRate("master", "LOGIN")
        assertEquals(0.0, denialRate!!, 0.001) // 0%
    }

    @Test
    fun `should reset all metrics`() {
        RateLimitMetrics.recordEvent("master", "LOGIN", "allowed")
        RateLimitMetrics.recordEvent("master", "LOGIN", "denied")
        RateLimitMetrics.updateAvailablePermits("master", "PER_USER", "alice", 10)

        RateLimitMetrics.reset()

        assertEquals(0L, RateLimitMetrics.getTotalAllowedEvents())
        assertEquals(0L, RateLimitMetrics.getTotalDeniedEvents())
        assertEquals(0, RateLimitMetrics.getEventCounters().size)
        assertEquals(0, RateLimitMetrics.getAvailablePermits().size)
    }
}
