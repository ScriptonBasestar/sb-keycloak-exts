package org.scriptonbasestar.kcexts.ratelimit.limiter

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.ratelimit.storage.RateLimitStorage
import java.time.Duration

/**
 * Token Bucket Rate Limiter
 *
 * Algorithm:
 * - Fixed capacity bucket holds tokens
 * - Tokens refill at constant rate
 * - Each request consumes 1 token
 * - Request allowed if tokens available
 *
 * Advantages:
 * - Allows bursts up to bucket capacity
 * - Simple to understand and implement
 * - Good for APIs with variable load
 *
 * @param storage Storage backend (in-memory or Redis)
 * @param capacity Maximum bucket size (max burst)
 * @param refillRate Tokens added per refill period
 * @param refillPeriod How often tokens are refilled
 */
class TokenBucketRateLimiter(
    private val storage: RateLimitStorage,
    private val capacity: Long,
    private val refillRate: Long,
    private val refillPeriod: Duration,
) : RateLimiter {
    private val logger = Logger.getLogger(TokenBucketRateLimiter::class.java)

    init {
        require(capacity > 0) { "Capacity must be positive" }
        require(refillRate > 0) { "Refill rate must be positive" }
        require(!refillPeriod.isNegative && !refillPeriod.isZero) { "Refill period must be positive" }
    }

    override fun tryAcquire(key: String): Boolean = tryAcquire(key, 1)

    override fun tryAcquire(
        key: String,
        permits: Long,
    ): Boolean {
        require(permits > 0) { "Permits must be positive" }
        require(permits <= capacity) { "Cannot acquire more permits than capacity" }

        val now = System.currentTimeMillis()
        val bucketKey = "ratelimit:bucket:$key"
        val timestampKey = "ratelimit:bucket:$key:time"

        synchronized(this) {
            // Get current state
            val currentTokens = storage.get(bucketKey) ?: capacity
            val lastRefillTime = storage.get(timestampKey) ?: now

            // Calculate refill
            val elapsed = now - lastRefillTime
            val refillIntervalMs = refillPeriod.toMillis()
            val tokensToAdd =
                if (elapsed >= refillIntervalMs) {
                    val intervals = elapsed / refillIntervalMs
                    intervals * refillRate
                } else {
                    0L
                }

            // New token count (capped at capacity)
            val newTokens = minOf(currentTokens + tokensToAdd, capacity)

            // Try to consume permits
            return if (newTokens >= permits) {
                val remainingTokens = newTokens - permits

                // Update storage
                val ttl = refillPeriod.multipliedBy(2) // Keep state for 2 refill periods
                storage.set(bucketKey, remainingTokens, ttl)
                storage.set(timestampKey, now, ttl)

                logger.debug("Rate limit allowed: key=$key, permits=$permits, remaining=$remainingTokens")
                true
            } else {
                logger.debug(
                    "Rate limit exceeded: key=$key, permits=$permits, available=$newTokens, capacity=$capacity",
                )
                false
            }
        }
    }

    override fun availablePermits(key: String): Long? {
        val bucketKey = "ratelimit:bucket:$key"
        val timestampKey = "ratelimit:bucket:$key:time"

        val currentTokens = storage.get(bucketKey) ?: return capacity
        val lastRefillTime = storage.get(timestampKey) ?: return capacity

        val now = System.currentTimeMillis()
        val elapsed = now - lastRefillTime
        val refillIntervalMs = refillPeriod.toMillis()

        val tokensToAdd =
            if (elapsed >= refillIntervalMs) {
                val intervals = elapsed / refillIntervalMs
                intervals * refillRate
            } else {
                0L
            }

        return minOf(currentTokens + tokensToAdd, capacity)
    }

    override fun reset(key: String) {
        val bucketKey = "ratelimit:bucket:$key"
        val timestampKey = "ratelimit:bucket:$key:time"

        storage.delete(bucketKey)
        storage.delete(timestampKey)

        logger.info("Rate limit reset: key=$key")
    }

    override fun algorithmName(): String = "TokenBucket"

    override fun toString(): String =
        "TokenBucketRateLimiter(capacity=$capacity, refillRate=$refillRate, refillPeriod=$refillPeriod)"
}
