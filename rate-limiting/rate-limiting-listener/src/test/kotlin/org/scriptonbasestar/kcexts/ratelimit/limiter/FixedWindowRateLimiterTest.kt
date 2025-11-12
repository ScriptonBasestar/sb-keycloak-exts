package org.scriptonbasestar.kcexts.ratelimit.limiter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.scriptonbasestar.kcexts.ratelimit.storage.InMemoryStorage
import java.time.Duration

class FixedWindowRateLimiterTest {
    private lateinit var storage: InMemoryStorage
    private lateinit var rateLimiter: FixedWindowRateLimiter

    @BeforeEach
    fun setup() {
        storage = InMemoryStorage()
        rateLimiter =
            FixedWindowRateLimiter(
                storage = storage,
                windowSize = Duration.ofSeconds(60),
                maxRequests = 10,
            )
    }

    @Test
    fun `should allow requests up to limit`() {
        val key = "user:alice"

        // Allow 10 requests
        repeat(10) { i ->
            assertTrue(rateLimiter.tryAcquire(key), "Request ${i + 1} should be allowed")
        }

        // 11th request should be denied
        assertFalse(rateLimiter.tryAcquire(key), "Request 11 should be denied")
    }

    @Test
    fun `should report available permits correctly`() {
        val key = "user:bob"

        assertEquals(10, rateLimiter.availablePermits(key), "Initial permits should be 10")

        rateLimiter.tryAcquire(key, 3)
        assertEquals(7, rateLimiter.availablePermits(key), "After consuming 3, should have 7 left")

        rateLimiter.tryAcquire(key, 5)
        assertEquals(2, rateLimiter.availablePermits(key), "After consuming 5 more, should have 2 left")

        rateLimiter.tryAcquire(key, 2)
        assertEquals(0, rateLimiter.availablePermits(key), "After consuming all, should have 0 left")
    }

    @Test
    fun `should deny requests exceeding limit`() {
        val key = "user:charlie"

        // Consume all permits
        assertTrue(rateLimiter.tryAcquire(key, 10))

        // Should deny further requests
        assertFalse(rateLimiter.tryAcquire(key, 1))
        assertFalse(rateLimiter.tryAcquire(key, 5))
    }

    @Test
    fun `should handle multiple keys independently`() {
        val key1 = "user:alice"
        val key2 = "user:bob"

        // Consume all permits for key1
        assertTrue(rateLimiter.tryAcquire(key1, 10))
        assertFalse(rateLimiter.tryAcquire(key1, 1))

        // key2 should still have permits
        assertTrue(rateLimiter.tryAcquire(key2, 5))
        assertEquals(5, rateLimiter.availablePermits(key2))
    }

    @Test
    fun `should reset rate limit for key`() {
        val key = "user:dave"

        // Consume all permits
        assertTrue(rateLimiter.tryAcquire(key, 10))
        assertEquals(0, rateLimiter.availablePermits(key))

        // Reset
        rateLimiter.reset(key)

        // Should have full permits again
        assertEquals(10, rateLimiter.availablePermits(key))
        assertTrue(rateLimiter.tryAcquire(key, 10))
    }

    @Test
    fun `should return correct algorithm name`() {
        assertEquals("FixedWindow", rateLimiter.algorithmName())
    }

    @Test
    fun `should acquire multiple permits at once`() {
        val key = "user:eve"

        assertTrue(rateLimiter.tryAcquire(key, 5))
        assertEquals(5, rateLimiter.availablePermits(key))

        assertTrue(rateLimiter.tryAcquire(key, 3))
        assertEquals(2, rateLimiter.availablePermits(key))

        // Try to acquire more than available
        assertFalse(rateLimiter.tryAcquire(key, 5))
        assertEquals(2, rateLimiter.availablePermits(key)) // Should not change
    }

    @Test
    fun `should handle zero available permits correctly`() {
        val key = "user:frank"

        // Consume all
        rateLimiter.tryAcquire(key, 10)

        // Should not go negative
        assertFalse(rateLimiter.tryAcquire(key, 1))
        assertEquals(0, rateLimiter.availablePermits(key))
    }
}
