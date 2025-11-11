package org.scriptonbasestar.kcexts.ratelimit.limiter

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.scriptonbasestar.kcexts.ratelimit.storage.InMemoryStorage
import org.scriptonbasestar.kcexts.ratelimit.storage.RateLimitStorage
import java.time.Duration

class SlidingWindowRateLimiterTest {
    private lateinit var storage: RateLimitStorage
    private lateinit var rateLimiter: SlidingWindowRateLimiter

    @BeforeEach
    fun setup() {
        storage = InMemoryStorage()
        rateLimiter =
            SlidingWindowRateLimiter(
                storage = storage,
                windowSize = Duration.ofSeconds(1),
                maxRequests = 10,
            )
    }

    @AfterEach
    fun cleanup() {
        storage.close()
    }

    @Test
    fun `should allow requests within window limit`() {
        val key = "test-user-1"

        // Should allow 10 requests (max)
        repeat(10) {
            assertTrue(rateLimiter.tryAcquire(key), "Request ${it + 1} should be allowed")
        }

        // 11th request should be denied
        assertFalse(rateLimiter.tryAcquire(key), "Request 11 should be denied")
    }

    @Test
    fun `should allow requests after window slides`() {
        val key = "test-user-2"

        // Use all 10 requests
        repeat(10) {
            assertTrue(rateLimiter.tryAcquire(key))
        }

        // Should be denied
        assertFalse(rateLimiter.tryAcquire(key))

        // Wait for window to slide (1 second)
        Thread.sleep(1100)

        // Should be allowed again
        assertTrue(rateLimiter.tryAcquire(key), "Should be allowed after window slides")
    }

    @Test
    fun `should remove old requests from window`() {
        val key = "test-user-3"

        // Make 5 requests
        repeat(5) {
            assertTrue(rateLimiter.tryAcquire(key))
        }

        // Wait half the window
        Thread.sleep(500)

        // Make 5 more requests
        repeat(5) {
            assertTrue(rateLimiter.tryAcquire(key))
        }

        // Now at capacity (10 requests in last 1 second)
        assertFalse(rateLimiter.tryAcquire(key))

        // Wait for first batch to expire (another 600ms)
        Thread.sleep(600)

        // First 5 requests should be outside window now
        // Should be able to make 5 more requests
        repeat(5) {
            assertTrue(rateLimiter.tryAcquire(key), "Request ${it + 1} should be allowed after first batch expires")
        }
    }

    @Test
    fun `should allow multiple permits acquisition`() {
        val key = "test-user-4"

        // Acquire 5 permits at once
        assertTrue(rateLimiter.tryAcquire(key, 5))
        assertEquals(5L, rateLimiter.availablePermits(key))

        // Acquire 5 more
        assertTrue(rateLimiter.tryAcquire(key, 5))
        assertEquals(0L, rateLimiter.availablePermits(key))

        // Should be denied
        assertFalse(rateLimiter.tryAcquire(key))
    }

    @Test
    fun `should track different keys independently`() {
        val key1 = "user-1"
        val key2 = "user-2"

        // Use all requests for key1
        repeat(10) {
            assertTrue(rateLimiter.tryAcquire(key1))
        }
        assertFalse(rateLimiter.tryAcquire(key1))

        // key2 should still have full capacity
        assertTrue(rateLimiter.tryAcquire(key2))
        assertEquals(9L, rateLimiter.availablePermits(key2))
    }

    @Test
    fun `should reset rate limit for key`() {
        val key = "test-user-5"

        // Use all requests
        repeat(10) {
            assertTrue(rateLimiter.tryAcquire(key))
        }
        assertFalse(rateLimiter.tryAcquire(key))

        // Reset
        rateLimiter.reset(key)

        // Should have full capacity again
        assertTrue(rateLimiter.tryAcquire(key))
        assertEquals(9L, rateLimiter.availablePermits(key))
    }

    @Test
    fun `should return available permits correctly`() {
        val key = "test-user-6"

        // Initial state
        assertEquals(10L, rateLimiter.availablePermits(key))

        // After 3 requests
        repeat(3) {
            assertTrue(rateLimiter.tryAcquire(key))
        }
        assertEquals(7L, rateLimiter.availablePermits(key))

        // After 7 more (total 10)
        repeat(7) {
            assertTrue(rateLimiter.tryAcquire(key))
        }
        assertEquals(0L, rateLimiter.availablePermits(key))
    }

    @Test
    fun `should throw exception for invalid parameters`() {
        val key = "test-user-7"

        // Cannot acquire 0 permits
        assertThrows(IllegalArgumentException::class.java) {
            rateLimiter.tryAcquire(key, 0)
        }

        // Cannot acquire negative permits
        assertThrows(IllegalArgumentException::class.java) {
            rateLimiter.tryAcquire(key, -1)
        }

        // Cannot acquire more than max requests
        assertThrows(IllegalArgumentException::class.java) {
            rateLimiter.tryAcquire(key, 11)
        }
    }

    @Test
    fun `should have correct algorithm name`() {
        assertEquals("SlidingWindow", rateLimiter.algorithmName())
    }

    @Test
    fun `should handle gradual window expiration`() {
        val key = "test-gradual"

        // Make 5 requests
        repeat(5) {
            assertTrue(rateLimiter.tryAcquire(key))
        }

        // Should still have 5 available
        assertEquals(5L, rateLimiter.availablePermits(key))

        // Wait for window to expire
        Thread.sleep(1100)

        // All should be expired, full capacity available
        assertEquals(10L, rateLimiter.availablePermits(key))

        // Should be able to make all 10 requests again
        repeat(10) {
            assertTrue(rateLimiter.tryAcquire(key))
        }

        // Now at capacity
        assertFalse(rateLimiter.tryAcquire(key))
    }

    @Test
    fun `should handle burst requests correctly`() {
        val key = "burst-test"

        // Make all 10 requests at once (burst)
        repeat(10) {
            assertTrue(rateLimiter.tryAcquire(key))
        }

        // Should be denied
        assertFalse(rateLimiter.tryAcquire(key))

        // Available should be 0
        assertEquals(0L, rateLimiter.availablePermits(key))

        // Wait for window to expire
        Thread.sleep(1100)

        // All 10 should be available again
        assertEquals(10L, rateLimiter.availablePermits(key))
    }
}
