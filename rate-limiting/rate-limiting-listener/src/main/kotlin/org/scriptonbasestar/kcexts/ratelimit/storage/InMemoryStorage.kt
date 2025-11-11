package org.scriptonbasestar.kcexts.ratelimit.storage

import org.jboss.logging.Logger
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * In-memory storage backend for rate limiting
 *
 * Suitable for:
 * - Single instance deployments
 * - Development/testing
 * - Fallback when Redis unavailable
 *
 * Not suitable for:
 * - Multi-instance deployments (no shared state)
 * - High availability requirements
 *
 * Features:
 * - Thread-safe using ConcurrentHashMap
 * - Automatic expiration cleanup
 * - Low latency (no network calls)
 */
class InMemoryStorage : RateLimitStorage {
    private val logger = Logger.getLogger(InMemoryStorage::class.java)

    // Storage: key -> (value, expirationTime)
    private val storage = ConcurrentHashMap<String, StorageEntry>()

    // Cleanup scheduler
    private val cleanupExecutor =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "RateLimitStorageCleanup").apply { isDaemon = true }
        }

    init {
        // Schedule cleanup every 60 seconds
        cleanupExecutor.scheduleAtFixedRate(
            { cleanup() },
            60,
            60,
            TimeUnit.SECONDS,
        )
        logger.info("InMemoryStorage initialized with automatic cleanup")
    }

    override fun get(key: String): Long? {
        val entry = storage[key] ?: return null

        // Check expiration
        if (entry.isExpired()) {
            storage.remove(key)
            return null
        }

        return entry.value
    }

    override fun set(
        key: String,
        value: Long,
        ttl: Duration,
    ) {
        val expirationTime = System.currentTimeMillis() + ttl.toMillis()
        storage[key] = StorageEntry(value, expirationTime)
    }

    override fun increment(
        key: String,
        delta: Long,
    ): Long =
        synchronized(storage) {
            val entry = storage[key]

            if (entry != null && !entry.isExpired()) {
                val newValue = entry.value + delta
                storage[key] = entry.copy(value = newValue)
                newValue
            } else {
                // Key doesn't exist or expired, start from delta
                val expirationTime = System.currentTimeMillis() + Duration.ofHours(1).toMillis()
                storage[key] = StorageEntry(delta, expirationTime)
                delta
            }
        }

    override fun decrement(
        key: String,
        delta: Long,
    ): Long = increment(key, -delta)

    override fun delete(key: String): Boolean = storage.remove(key) != null

    override fun exists(key: String): Boolean {
        val entry = storage[key] ?: return false

        if (entry.isExpired()) {
            storage.remove(key)
            return false
        }

        return true
    }

    override fun expire(
        key: String,
        ttl: Duration,
    ): Boolean {
        val entry = storage[key] ?: return false

        if (entry.isExpired()) {
            storage.remove(key)
            return false
        }

        val expirationTime = System.currentTimeMillis() + ttl.toMillis()
        storage[key] = entry.copy(expirationTime = expirationTime)
        return true
    }

    override fun ttl(key: String): Duration? {
        val entry = storage[key] ?: return null

        if (entry.isExpired()) {
            storage.remove(key)
            return null
        }

        val remainingMs = entry.expirationTime - System.currentTimeMillis()
        return if (remainingMs > 0) Duration.ofMillis(remainingMs) else null
    }

    override fun close() {
        cleanupExecutor.shutdown()
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cleanupExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        storage.clear()
        logger.info("InMemoryStorage closed")
    }

    /**
     * Remove expired entries
     */
    private fun cleanup() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<String>()

        storage.forEach { (key, entry) ->
            if (entry.expirationTime < now) {
                keysToRemove.add(key)
            }
        }

        keysToRemove.forEach { storage.remove(it) }

        if (keysToRemove.isNotEmpty()) {
            logger.debug("Cleaned up ${keysToRemove.size} expired entries")
        }
    }

    /**
     * Get storage statistics (for testing/monitoring)
     */
    fun stats(): StorageStats =
        StorageStats(
            totalKeys = storage.size,
            expiredKeys =
                storage.values.count {
                    it.isExpired()
                },
        )

    private data class StorageEntry(
        val value: Long,
        val expirationTime: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expirationTime
    }

    data class StorageStats(
        val totalKeys: Int,
        val expiredKeys: Int,
    )
}
