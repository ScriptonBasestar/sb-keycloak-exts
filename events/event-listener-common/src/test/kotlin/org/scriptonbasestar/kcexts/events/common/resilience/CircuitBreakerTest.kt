package org.scriptonbasestar.kcexts.events.common.resilience

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CircuitBreakerTest {
    @Test
    fun `should start in CLOSED state`() {
        val cb = CircuitBreaker("test")
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState())
    }

    @Test
    fun `should allow requests in CLOSED state`() {
        val cb = CircuitBreaker("test")
        var callCount = 0
        val operation = {
            callCount++
            "success"
        }

        val result = cb.execute(operation)

        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `should transition to OPEN after failure threshold`() {
        val cb =
            CircuitBreaker(
                name = "test",
                failureThreshold = 3,
            )

        val operation = { throw RuntimeException("test error") }

        // First 2 failures - circuit should stay CLOSED
        repeat(2) {
            assertThrows<RuntimeException> { cb.execute(operation) }
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState())
        }

        // 3rd failure - circuit should open
        assertThrows<RuntimeException> { cb.execute(operation) }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState())
    }

    @Test
    fun `should reject requests in OPEN state`() {
        val cb =
            CircuitBreaker(
                name = "test",
                failureThreshold = 1,
            )

        var callCount = 0
        val operation = {
            callCount++
            throw RuntimeException("test error")
        }

        // Trip the circuit
        assertThrows<RuntimeException> { cb.execute(operation) }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState())

        // Should reject next request without calling operation
        assertThrows<CircuitBreakerOpenException> { cb.execute(operation) }
        assertEquals(1, callCount) // Should only be called once
    }

    @Test
    fun `should transition to HALF_OPEN after timeout`() {
        val cb =
            CircuitBreaker(
                name = "test",
                failureThreshold = 1,
                openTimeout = Duration.ofMillis(100),
            )

        val operation = { throw RuntimeException("test error") }

        // Trip the circuit
        assertThrows<RuntimeException> { cb.execute(operation) }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState())

        // Wait for timeout
        Thread.sleep(150)

        // Should transition to HALF_OPEN
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState())
    }

    @Test
    fun `should transition to CLOSED after success threshold in HALF_OPEN`() {
        val cb =
            CircuitBreaker(
                name = "test",
                failureThreshold = 1,
                successThreshold = 2,
                openTimeout = Duration.ofMillis(100),
            )

        var callCount = 0
        val operation = {
            callCount++
            if (callCount == 1) throw RuntimeException("test error") else "success"
        }

        // Trip the circuit
        assertThrows<RuntimeException> { cb.execute(operation) }
        Thread.sleep(150)

        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState())

        // First success in HALF_OPEN
        cb.execute(operation)
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState())

        // Second success - should close circuit
        cb.execute(operation)
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState())
    }

    @Test
    fun `should transition back to OPEN on failure in HALF_OPEN`() {
        val cb =
            CircuitBreaker(
                name = "test",
                failureThreshold = 1,
                openTimeout = Duration.ofMillis(100),
            )

        val operation = { throw RuntimeException("test error") }

        // Trip the circuit
        assertThrows<RuntimeException> { cb.execute(operation) }
        Thread.sleep(150)

        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState())

        // Failure in HALF_OPEN should reopen circuit
        assertThrows<RuntimeException> { cb.execute(operation) }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState())
    }

    @Test
    fun `should reset failure count on success in CLOSED state`() {
        val cb =
            CircuitBreaker(
                name = "test",
                failureThreshold = 3,
            )

        var callCount = 0
        val operation = {
            callCount++
            when (callCount) {
                1, 2 -> throw RuntimeException("error") // First two failures
                3 -> "success" // Success
                4, 5 -> throw RuntimeException("error") // Two more failures
                else -> "success"
            }
        }

        // Two failures
        repeat(2) {
            assertThrows<RuntimeException> { cb.execute(operation) }
        }
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState())

        // Success - should reset failure count
        cb.execute(operation)

        // Two more failures - circuit should still be CLOSED (reset worked)
        repeat(2) {
            assertThrows<RuntimeException> { cb.execute(operation) }
        }
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState())
    }

    @Test
    fun `should return metrics`() {
        val cb =
            CircuitBreaker(
                name = "test-metrics",
                failureThreshold = 2,
            )

        val operation = { throw RuntimeException("error") }

        assertThrows<RuntimeException> { cb.execute(operation) }

        val metrics = cb.getMetrics()
        assertEquals("test-metrics", metrics.name)
        assertEquals(CircuitBreaker.State.CLOSED, metrics.state)
        assertEquals(1, metrics.failureCount)
        assertEquals(0, metrics.successCount)
        assertNotNull(metrics.lastFailureTime)
        assertNotNull(metrics.lastStateChange)
    }

    @Test
    fun `should manually reset to CLOSED`() {
        val cb =
            CircuitBreaker(
                name = "test",
                failureThreshold = 1,
            )

        val operation = { throw RuntimeException("error") }

        // Trip the circuit
        assertThrows<RuntimeException> { cb.execute(operation) }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState())

        // Manually reset
        cb.reset()
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState())
    }

    @Test
    fun `should manually trip to OPEN`() {
        val cb = CircuitBreaker("test")
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState())

        cb.trip()
        assertEquals(CircuitBreaker.State.OPEN, cb.getState())
    }
}
