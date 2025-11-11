package org.scriptonbasestar.kcexts.ratelimit.limiter

/**
 * Rate limiter interface
 *
 * Implementations should be thread-safe and support distributed rate limiting
 */
interface RateLimiter {
    /**
     * Try to acquire permission for a request
     *
     * @param key Unique key for the rate limit (e.g., "user:123", "client:app-1")
     * @return true if request is allowed, false if rate limit exceeded
     */
    fun tryAcquire(key: String): Boolean

    /**
     * Try to acquire N permits
     *
     * @param key Unique key for the rate limit
     * @param permits Number of permits to acquire (default 1)
     * @return true if request is allowed, false if rate limit exceeded
     */
    fun tryAcquire(
        key: String,
        permits: Long = 1,
    ): Boolean

    /**
     * Get current available permits for a key
     *
     * @param key Unique key for the rate limit
     * @return Number of available permits, or null if key doesn't exist
     */
    fun availablePermits(key: String): Long?

    /**
     * Reset rate limit for a key
     *
     * @param key Unique key to reset
     */
    fun reset(key: String)

    /**
     * Get algorithm name
     */
    fun algorithmName(): String
}

/**
 * Rate limit result with detailed information
 */
data class RateLimitResult(
    val allowed: Boolean,
    val remainingTokens: Long,
    val retryAfterMs: Long?,
    val limit: Long,
)
