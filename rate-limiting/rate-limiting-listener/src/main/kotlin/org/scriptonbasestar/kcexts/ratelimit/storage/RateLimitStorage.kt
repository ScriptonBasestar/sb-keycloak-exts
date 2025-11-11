package org.scriptonbasestar.kcexts.ratelimit.storage

import java.time.Duration

/**
 * Storage backend for rate limiting state
 *
 * Implementations can be in-memory (single instance) or distributed (Redis, etc.)
 */
interface RateLimitStorage {
    /**
     * Get current value for a key
     *
     * @param key Storage key
     * @return Current value, or null if key doesn't exist
     */
    fun get(key: String): Long?

    /**
     * Set value for a key with TTL
     *
     * @param key Storage key
     * @param value Value to set
     * @param ttl Time to live
     */
    fun set(
        key: String,
        value: Long,
        ttl: Duration,
    )

    /**
     * Increment value for a key
     *
     * @param key Storage key
     * @param delta Amount to increment (can be negative for decrement)
     * @return New value after increment
     */
    fun increment(
        key: String,
        delta: Long = 1,
    ): Long

    /**
     * Decrement value for a key
     *
     * @param key Storage key
     * @param delta Amount to decrement
     * @return New value after decrement
     */
    fun decrement(
        key: String,
        delta: Long = 1,
    ): Long

    /**
     * Delete a key
     *
     * @param key Storage key
     * @return true if key was deleted, false if it didn't exist
     */
    fun delete(key: String): Boolean

    /**
     * Check if key exists
     *
     * @param key Storage key
     * @return true if key exists
     */
    fun exists(key: String): Boolean

    /**
     * Set expiration for a key
     *
     * @param key Storage key
     * @param ttl Time to live
     * @return true if expiration was set
     */
    fun expire(
        key: String,
        ttl: Duration,
    ): Boolean

    /**
     * Get remaining TTL for a key
     *
     * @param key Storage key
     * @return Remaining TTL, or null if key doesn't exist or has no expiration
     */
    fun ttl(key: String): Duration?

    /**
     * Close storage connection
     */
    fun close()
}

/**
 * Token bucket state in storage
 */
data class TokenBucketState(
    val tokens: Long,
    val lastRefillTime: Long,
)

/**
 * Sliding window entry in storage
 */
data class SlidingWindowEntry(
    val requestId: String,
    val timestamp: Long,
)
