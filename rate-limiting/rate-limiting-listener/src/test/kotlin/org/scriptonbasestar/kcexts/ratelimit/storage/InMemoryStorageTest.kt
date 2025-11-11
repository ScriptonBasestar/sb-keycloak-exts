package org.scriptonbasestar.kcexts.ratelimit.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class InMemoryStorageTest {
    private lateinit var storage: InMemoryStorage

    @BeforeEach
    fun setup() {
        storage = InMemoryStorage()
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
    fun `should return null TTL for expired key`() {
        val key = "expired-key"

        storage.set(key, 100L, Duration.ofMillis(100))
        Thread.sleep(200)

        assertNull(storage.ttl(key))
    }

    @Test
    fun `should handle multiple keys independently`() {
        storage.set("key1", 10L, Duration.ofSeconds(60))
        storage.set("key2", 20L, Duration.ofSeconds(60))
        storage.set("key3", 30L, Duration.ofSeconds(60))

        assertEquals(10L, storage.get("key1"))
        assertEquals(20L, storage.get("key2"))
        assertEquals(30L, storage.get("key3"))

        storage.delete("key2")

        assertEquals(10L, storage.get("key1"))
        assertNull(storage.get("key2"))
        assertEquals(30L, storage.get("key3"))
    }

    @Test
    fun `should provide storage statistics`() {
        // Initially empty
        var stats = storage.stats()
        assertEquals(0, stats.totalKeys)
        assertEquals(0, stats.expiredKeys)

        // Add some keys
        storage.set("key1", 100L, Duration.ofSeconds(60))
        storage.set("key2", 200L, Duration.ofMillis(100))
        storage.set("key3", 300L, Duration.ofSeconds(60))

        stats = storage.stats()
        assertEquals(3, stats.totalKeys)

        // Wait for one key to expire
        Thread.sleep(200)

        stats = storage.stats()
        assertEquals(3, stats.totalKeys) // Still 3 keys (expired not cleaned yet)
        assertEquals(1, stats.expiredKeys) // But 1 is expired
    }

    @Test
    fun `should be thread-safe`() {
        val key = "concurrent-counter"
        val threads = 10
        val incrementsPerThread = 100

        val threadList =
            List(threads) {
                Thread {
                    repeat(incrementsPerThread) {
                        storage.increment(key)
                    }
                }
            }

        threadList.forEach { it.start() }
        threadList.forEach { it.join() }

        // Total should be threads * incrementsPerThread
        assertEquals((threads * incrementsPerThread).toLong(), storage.get(key))
    }

    @Test
    fun `should handle concurrent set and get operations`() {
        val keyPrefix = "concurrent-key"
        val operations = 100

        val threads =
            listOf(
                // Writer thread
                Thread {
                    repeat(operations) {
                        storage.set("$keyPrefix-$it", it.toLong(), Duration.ofSeconds(60))
                    }
                },
                // Reader thread
                Thread {
                    repeat(operations) {
                        storage.get("$keyPrefix-$it")
                    }
                },
                // Deleter thread
                Thread {
                    repeat(operations) {
                        if (it % 2 == 0) {
                            storage.delete("$keyPrefix-$it")
                        }
                    }
                },
            )

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // No exceptions should be thrown
        // Some keys should exist, some should not (deleted)
        val stats = storage.stats()
        assertTrue(stats.totalKeys >= 0)
    }

    @Test
    fun `should expire old entries during cleanup`() {
        // Add keys with short TTL
        repeat(10) {
            storage.set("temp-$it", it.toLong(), Duration.ofMillis(100))
        }

        var stats = storage.stats()
        assertEquals(10, stats.totalKeys)

        // Wait for expiration
        Thread.sleep(200)

        // Trigger cleanup by accessing expired keys
        repeat(10) {
            storage.get("temp-$it")
        }

        // Or wait for automatic cleanup (runs every 60 seconds)
        // For this test, we verify that accessing expired keys removes them
        stats = storage.stats()
        assertTrue(stats.totalKeys < 10, "Expired keys should be removed")
    }
}
