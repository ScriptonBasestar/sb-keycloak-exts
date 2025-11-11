package org.scriptonbasestar.kcexts.ratelimit.limiter

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.ratelimit.storage.RateLimitStorage
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Sliding Window Rate Limiter
 *
 * Algorithm:
 * - Track requests in a time window
 * - Window slides continuously
 * - Count requests in current window
 * - Allow if count < limit
 *
 * Advantages:
 * - Precise time-based control
 * - No boundary issues (unlike fixed window)
 * - Smooth rate enforcement
 *
 * Note: This implementation uses a simplified approach with storage counters
 * For production use with Redis, consider using sorted sets with timestamps
 *
 * @param storage Storage backend
 * @param windowSize Time window size
 * @param maxRequests Maximum requests allowed in window
 */
class SlidingWindowRateLimiter(
    private val storage: RateLimitStorage,
    private val windowSize: Duration,
    private val maxRequests: Long,
) : RateLimiter {
    private val logger = Logger.getLogger(SlidingWindowRateLimiter::class.java)

    // In-memory tracking of request timestamps per key (for non-Redis storage)
    private val requestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    init {
        require(maxRequests > 0) { "Max requests must be positive" }
        require(!windowSize.isNegative && !windowSize.isZero) { "Window size must be positive" }
    }

    override fun tryAcquire(key: String): Boolean = tryAcquire(key, 1)

    override fun tryAcquire(
        key: String,
        permits: Long,
    ): Boolean {
        require(permits > 0) { "Permits must be positive" }
        require(permits <= maxRequests) { "Cannot acquire more permits than max requests" }

        val now = System.currentTimeMillis()
        val windowKey = "ratelimit:window:$key"

        synchronized(this) {
            // Get or create timestamp list
            val timestamps = requestTimestamps.getOrPut(key) { mutableListOf() }

            // Remove old requests outside the window
            val cutoffTime = now - windowSize.toMillis()
            timestamps.removeIf { it < cutoffTime }

            // Count current requests in window
            val currentCount = timestamps.size.toLong()

            // Check if we can add new permits
            return if (currentCount + permits <= maxRequests) {
                // Add new request timestamps
                repeat(permits.toInt()) {
                    timestamps.add(now)
                }

                // Store counter in storage for metrics
                storage.set(windowKey, currentCount + permits, windowSize)

                logger.debug(
                    "Rate limit allowed: key=$key, permits=$permits, " +
                        "current=$currentCount, max=$maxRequests",
                )
                true
            } else {
                logger.debug(
                    "Rate limit exceeded: key=$key, permits=$permits, " +
                        "current=$currentCount, max=$maxRequests",
                )
                false
            }
        }
    }

    override fun availablePermits(key: String): Long? {
        val now = System.currentTimeMillis()
        val timestamps = requestTimestamps[key] ?: return maxRequests

        synchronized(this) {
            // Remove old requests
            val cutoffTime = now - windowSize.toMillis()
            timestamps.removeIf { it < cutoffTime }

            val currentCount = timestamps.size.toLong()
            return maxOf(0, maxRequests - currentCount)
        }
    }

    override fun reset(key: String) {
        val windowKey = "ratelimit:window:$key"

        synchronized(this) {
            requestTimestamps.remove(key)
            storage.delete(windowKey)
        }

        logger.info("Rate limit reset: key=$key")
    }

    override fun algorithmName(): String = "SlidingWindow"

    override fun toString(): String = "SlidingWindowRateLimiter(windowSize=$windowSize, maxRequests=$maxRequests)"
}
