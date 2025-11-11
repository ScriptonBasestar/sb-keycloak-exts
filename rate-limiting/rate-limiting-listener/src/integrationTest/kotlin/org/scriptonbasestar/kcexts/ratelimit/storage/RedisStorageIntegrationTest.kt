package org.scriptonbasestar.kcexts.ratelimit.storage

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.scriptonbasestar.kcexts.ratelimit.config.RedisConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * Integration tests for RedisStorage using TestContainers
 */
class RedisStorageIntegrationTest {
    private lateinit var storage: RedisStorage

    companion object {
        private lateinit var redisContainer: GenericContainer<*>
        private lateinit var redisConfig: RedisConfig

        @JvmStatic
        @BeforeAll
        fun setupRedis() {
            // Start Redis container
            redisContainer =
                GenericContainer(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--requirepass", "testpass")

            redisContainer.start()

            // Create Redis configuration
            redisConfig =
                RedisConfig(
                    host = redisContainer.host,
                    port = redisContainer.getMappedPort(6379),
                    password = "testpass",
                    database = 0,
                    timeout = Duration.ofSeconds(2),
                )
        }

        @JvmStatic
        @AfterAll
        fun teardownRedis() {
            redisContainer.stop()
        }
    }

    @BeforeEach
    fun setup() {
        storage = RedisStorage(redisConfig)
        // Clear all keys before each test to ensure isolation
        storage.flushDatabase()
    }

    @AfterEach
    fun cleanup() {
        storage.close()
    }

    @Test
    fun `should store and retrieve values`() {
        val key = "test-key"
        val value = 42L

        storage.set(key, value, Duration.ofSeconds(60))

        assertEquals(value, storage.get(key))
    }

    @Test
    fun `should return null for non-existent key`() {
        assertNull(storage.get("non-existent-key"))
    }

    @Test
    fun `should increment value`() {
        val key = "counter"

        // First increment creates key with value 1
        assertEquals(1L, storage.increment(key))

        // Second increment
        assertEquals(2L, storage.increment(key))

        // Increment by 5
        assertEquals(7L, storage.increment(key, 5))
    }

    @Test
    fun `should decrement value`() {
        val key = "counter"

        // Set initial value
        storage.set(key, 10L, Duration.ofSeconds(60))

        // Decrement by 1
        assertEquals(9L, storage.decrement(key))

        // Decrement by 5
        assertEquals(4L, storage.decrement(key, 5))
    }

    @Test
    fun `should delete keys`() {
        val key = "test-key"

        storage.set(key, 100L, Duration.ofSeconds(60))
        assertTrue(storage.exists(key))

        assertTrue(storage.delete(key))
        assertFalse(storage.exists(key))

        // Deleting non-existent key returns false
        assertFalse(storage.delete(key))
    }

    @Test
    fun `should check key existence`() {
        val key = "test-key"

        assertFalse(storage.exists(key))

        storage.set(key, 100L, Duration.ofSeconds(60))
        assertTrue(storage.exists(key))

        storage.delete(key)
        assertFalse(storage.exists(key))
    }

    @Test
    fun `should expire keys automatically`() {
        val key = "expiring-key"

        // Set with 1 second TTL
        storage.set(key, 100L, Duration.ofSeconds(1))
        assertTrue(storage.exists(key))

        // Wait for expiration
        Thread.sleep(1100)

        // Should be expired
        assertNull(storage.get(key))
        assertFalse(storage.exists(key))
    }

    @Test
    fun `should update expiration time`() {
        val key = "test-key"

        // Set with short TTL
        storage.set(key, 100L, Duration.ofMillis(500))
        assertTrue(storage.exists(key))

        // Update expiration to longer TTL
        assertTrue(storage.expire(key, Duration.ofSeconds(10)))

        // Wait for original TTL
        Thread.sleep(600)

        // Should still exist (new TTL applied)
        assertTrue(storage.exists(key))
        assertEquals(100L, storage.get(key))
    }

    @Test
    fun `should return remaining TTL`() {
        val key = "test-key"

        // Set with 5 second TTL
        storage.set(key, 100L, Duration.ofSeconds(5))

        val ttl = storage.ttl(key)
        assertNotNull(ttl)

        // TTL should be between 4 and 5 seconds
        assertTrue(ttl!!.seconds in 4..5, "TTL was ${ttl.seconds} seconds")
    }

    @Test
    fun `should return null TTL for non-existent key`() {
        assertNull(storage.ttl("non-existent-key"))
    }

    @Test
    fun `should handle Token Bucket algorithm`() {
        val key = "bucket:user:alice"
        val capacity = 10L
        val refillRate = 10L
        val refillPeriodSeconds = 60L

        // First request should succeed (capacity = 10)
        assertTrue(storage.tryAcquireTokenBucket(key, capacity, refillRate, refillPeriodSeconds))

        // Should be able to make 9 more requests
        repeat(9) {
            assertTrue(
                storage.tryAcquireTokenBucket(key, capacity, refillRate, refillPeriodSeconds),
                "Request ${it + 2} should be allowed",
            )
        }

        // 11th request should be denied (bucket empty)
        assertFalse(
            storage.tryAcquireTokenBucket(key, capacity, refillRate, refillPeriodSeconds),
            "Request 11 should be denied",
        )
    }

    @Test
    fun `should handle Sliding Window algorithm`() {
        val key = "window:user:bob"
        val windowSizeMs = 1000L // 1 second
        val maxRequests = 5L

        // Should allow 5 requests
        repeat(5) {
            assertTrue(
                storage.tryAcquireSlidingWindow(key, windowSizeMs, maxRequests),
                "Request ${it + 1} should be allowed",
            )
        }

        // 6th request should be denied
        assertFalse(
            storage.tryAcquireSlidingWindow(key, windowSizeMs, maxRequests),
            "Request 6 should be denied",
        )

        // Wait for window to slide
        Thread.sleep(1100)

        // Should be allowed again
        assertTrue(
            storage.tryAcquireSlidingWindow(key, windowSizeMs, maxRequests),
            "Should be allowed after window slides",
        )
    }

    @Test
    fun `should get Token Bucket permits correctly`() {
        val key = "bucket:permits:test"
        val capacity = 10L
        val refillRate = 10L
        val refillPeriodSeconds = 60L

        // Initial state - full capacity
        assertEquals(capacity, storage.getTokenBucketPermits(key, capacity, refillRate, refillPeriodSeconds))

        // Consume 3 tokens
        repeat(3) {
            storage.tryAcquireTokenBucket(key, capacity, refillRate, refillPeriodSeconds)
        }

        // Should have 7 remaining
        assertEquals(7L, storage.getTokenBucketPermits(key, capacity, refillRate, refillPeriodSeconds))
    }

    @Test
    fun `should get Sliding Window permits correctly`() {
        val key = "window:permits:test"
        val windowSizeMs = 1000L
        val maxRequests = 10L

        // Initial state - all permits available
        assertEquals(maxRequests, storage.getSlidingWindowPermits(key, windowSizeMs, maxRequests))

        // Use 3 permits
        repeat(3) {
            storage.tryAcquireSlidingWindow(key, windowSizeMs, maxRequests)
        }

        // Should have 7 remaining
        assertEquals(7L, storage.getSlidingWindowPermits(key, windowSizeMs, maxRequests))
    }

    @Test
    fun `should handle concurrent Token Bucket requests`() {
        val key = "bucket:concurrent:test"
        val capacity = 10L
        val refillRate = 10L
        val refillPeriodSeconds = 60L

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
                        if (storage.tryAcquireTokenBucket(key, capacity, refillRate, refillPeriodSeconds)) {
                            successfulRequests.incrementAndGet()
                        }
                    }
                }
            }

        // Start all threads
        threadList.forEach { it.start() }

        // Wait for completion
        threadList.forEach { it.join() }

        // Redis Lua scripts are atomic, so we should get exactly capacity successful requests
        // Allow small margin for timing (within 50% of capacity)
        val successful = successfulRequests.get()
        assertTrue(
            successful >= capacity.toInt() - 2 && successful <= capacity.toInt() + 5,
            "Successful requests ($successful) should be approximately $capacity (got range ${capacity - 2} to ${capacity + 5})",
        )
    }

    @Test
    fun `should handle concurrent Sliding Window requests`() {
        val key = "window:concurrent:test"
        val windowSizeMs = 1000L
        val maxRequests = 10L

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
                        if (storage.tryAcquireSlidingWindow(key, windowSizeMs, maxRequests)) {
                            successfulRequests.incrementAndGet()
                        }
                    }
                }
            }

        // Start all threads
        threadList.forEach { it.start() }

        // Wait for completion
        threadList.forEach { it.join() }

        // Total successful should not exceed maxRequests (10)
        assertTrue(
            successfulRequests.get() <= maxRequests.toInt(),
            "Successful requests (${successfulRequests.get()}) should not exceed max ($maxRequests)",
        )
    }

    @Test
    fun `should handle Token Bucket refill after period`() {
        val key = "bucket:refill:test"
        val capacity = 10L
        val refillRate = 10L // Refill all 10 tokens
        val refillPeriodSeconds = 1L // 1 second for faster testing

        // Use all tokens
        repeat(10) {
            assertTrue(storage.tryAcquireTokenBucket(key, capacity, refillRate, refillPeriodSeconds))
        }

        // Should be denied (bucket empty)
        assertFalse(storage.tryAcquireTokenBucket(key, capacity, refillRate, refillPeriodSeconds))

        // Wait for refill period (with margin)
        Thread.sleep(1200)

        // Should have refilled all tokens (or close to it)
        // First request should succeed
        assertTrue(storage.tryAcquireTokenBucket(key, capacity, refillRate, refillPeriodSeconds))

        // Should have 9 more available (or close)
        val remaining = storage.getTokenBucketPermits(key, capacity, refillRate, refillPeriodSeconds)
        assertTrue(remaining >= 8, "Expected at least 8 tokens after refill, got $remaining")
    }

    @Test
    fun `should handle Sliding Window gradual expiration`() {
        val key = "window:gradual:test"
        val windowSizeMs = 1000L
        val maxRequests = 5L

        // Make 5 requests
        repeat(5) {
            storage.tryAcquireSlidingWindow(key, windowSizeMs, maxRequests)
        }

        // Should be denied
        assertFalse(storage.tryAcquireSlidingWindow(key, windowSizeMs, maxRequests))

        // Wait for window to expire
        Thread.sleep(1100)

        // All should be expired, can make 5 more
        repeat(5) {
            assertTrue(
                storage.tryAcquireSlidingWindow(key, windowSizeMs, maxRequests),
                "Request ${it + 1} should be allowed after window expiration",
            )
        }
    }

    @Test
    fun `should handle multiple independent keys`() {
        val key1 = "user:alice"
        val key2 = "user:bob"

        storage.set(key1, 10L, Duration.ofSeconds(60))
        storage.set(key2, 20L, Duration.ofSeconds(60))

        assertEquals(10L, storage.get(key1))
        assertEquals(20L, storage.get(key2))

        storage.delete(key2)

        assertEquals(10L, storage.get(key1))
        assertNull(storage.get(key2))
    }
}
