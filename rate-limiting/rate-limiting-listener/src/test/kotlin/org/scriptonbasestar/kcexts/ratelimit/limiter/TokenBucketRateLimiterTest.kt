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

class TokenBucketRateLimiterTest {
    private lateinit var storage: RateLimitStorage
    private lateinit var rateLimiter: TokenBucketRateLimiter

    @BeforeEach
    fun setup() {
        storage = InMemoryStorage()
        rateLimiter =
            TokenBucketRateLimiter(
                storage = storage,
                capacity = 10,
                refillRate = 10,
                refillPeriod = Duration.ofSeconds(1),
            )
    }

    @AfterEach
    fun cleanup() {
        storage.close()
    }

    @Test
    fun `should allow requests within capacity`() {
        val key = "test-user-1"

        // First request should be allowed (capacity = 10)
        assertTrue(rateLimiter.tryAcquire(key))

        // Should be able to make 9 more requests
        repeat(9) {
            assertTrue(rateLimiter.tryAcquire(key), "Request ${it + 2} should be allowed")
        }

        // 11th request should be denied (capacity exceeded)
        assertFalse(rateLimiter.tryAcquire(key), "Request 11 should be denied")
    }

    @Test
    fun `should allow multiple permits acquisition`() {
        val key = "test-user-2"

        // Acquire 5 permits
        assertTrue(rateLimiter.tryAcquire(key, 5))

        // Should have 5 remaining
        assertEquals(5L, rateLimiter.availablePermits(key))

        // Acquire 5 more permits
        assertTrue(rateLimiter.tryAcquire(key, 5))

        // Now at capacity (0 remaining)
        assertEquals(0L, rateLimiter.availablePermits(key))

        // Should be denied
        assertFalse(rateLimiter.tryAcquire(key))
    }

    @Test
    fun `should refill tokens after refill period`() {
        val key = "test-user-3"

        // Use all 10 tokens
        assertTrue(rateLimiter.tryAcquire(key, 10))
        assertFalse(rateLimiter.tryAcquire(key), "Should be denied immediately after using all tokens")

        // Wait for refill period (1 second)
        Thread.sleep(1100)

        // Should have refilled (10 tokens added)
        assertTrue(rateLimiter.tryAcquire(key), "Should be allowed after refill period")
        assertEquals(9L, rateLimiter.availablePermits(key), "Should have 9 tokens after consuming 1")
    }

    @Test
    fun `should cap tokens at capacity after refill`() {
        val key = "test-user-4"

        // Use 5 tokens
        assertTrue(rateLimiter.tryAcquire(key, 5))
        assertEquals(5L, rateLimiter.availablePermits(key))

        // Wait for multiple refill periods
        Thread.sleep(3100) // 3 seconds = 3 refill periods = 30 tokens theoretically

        // Should still be capped at capacity (10)
        assertEquals(10L, rateLimiter.availablePermits(key), "Should be capped at capacity")
    }

    @Test
    fun `should track different keys independently`() {
        val key1 = "user-1"
        val key2 = "user-2"

        // Use all tokens for key1
        assertTrue(rateLimiter.tryAcquire(key1, 10))
        assertFalse(rateLimiter.tryAcquire(key1))

        // key2 should still have full capacity
        assertTrue(rateLimiter.tryAcquire(key2))
        assertEquals(9L, rateLimiter.availablePermits(key2))
    }

    @Test
    fun `should reset rate limit for key`() {
        val key = "test-user-5"

        // Use all tokens
        assertTrue(rateLimiter.tryAcquire(key, 10))
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

        // Initial state - full capacity
        assertEquals(10L, rateLimiter.availablePermits(key))

        // After consuming 3
        assertTrue(rateLimiter.tryAcquire(key, 3))
        assertEquals(7L, rateLimiter.availablePermits(key))

        // After consuming 7 more (total 10)
        assertTrue(rateLimiter.tryAcquire(key, 7))
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

        // Cannot acquire more than capacity
        assertThrows(IllegalArgumentException::class.java) {
            rateLimiter.tryAcquire(key, 11)
        }
    }

    @Test
    fun `should have correct algorithm name`() {
        assertEquals("TokenBucket", rateLimiter.algorithmName())
    }

    @Test
    fun `should handle concurrent requests safely`() {
        val key = "concurrent-test"
        val threads = 20
        val requestsPerThread = 10
        val successfulRequests =
            java.util.concurrent.atomic
                .AtomicInteger(0)

        // Create thread pool
        val threadList =
            List(threads) {
                Thread {
                    repeat(requestsPerThread) {
                        if (rateLimiter.tryAcquire(key)) {
                            successfulRequests.incrementAndGet()
                        }
                    }
                }
            }

        // Start all threads
        threadList.forEach { it.start() }

        // Wait for completion
        threadList.forEach { it.join() }

        // Total successful should not exceed capacity (10)
        assertTrue(
            successfulRequests.get() <= 10,
            "Successful requests (${successfulRequests.get()}) should not exceed capacity (10)",
        )
    }
}
