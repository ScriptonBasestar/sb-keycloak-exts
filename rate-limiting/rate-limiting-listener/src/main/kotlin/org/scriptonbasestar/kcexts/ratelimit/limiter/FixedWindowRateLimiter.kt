/*
 * Copyright 2025 ScriptonBasestar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scriptonbasestar.kcexts.ratelimit.limiter

import org.scriptonbasestar.kcexts.ratelimit.storage.RateLimitStorage
import java.time.Duration

/**
 * Fixed Window Rate Limiter
 *
 * Divides time into discrete fixed windows (e.g., 1-minute buckets).
 * Counts requests in current window and resets at window boundary.
 *
 * Advantages:
 * - Very low memory usage (single counter per key)
 * - Simple implementation
 * - High performance
 *
 * Disadvantages:
 * - Boundary issue: 2x limit possible at window edge
 * - Less fair than sliding window
 *
 * Example:
 * - Window size: 60 seconds
 * - Max requests: 10
 * - Window 1 (0-60s): 10 requests at 59s ✅
 * - Window 2 (60-120s): 10 requests at 60s ✅
 * - Total: 20 requests in 1 second! ⚠️
 */
class FixedWindowRateLimiter(
    private val storage: RateLimitStorage,
    private val windowSize: Duration,
    private val maxRequests: Long,
) : RateLimiter {
    companion object {
        private const val COUNTER_PREFIX = "ratelimit:fixed"
    }

    init {
        require(windowSize.seconds > 0) { "Window size must be positive" }
        require(maxRequests > 0) { "Max requests must be positive" }
    }

    override fun tryAcquire(key: String): Boolean = tryAcquire(key, 1)

    override fun tryAcquire(
        key: String,
        permits: Long,
    ): Boolean {
        require(permits > 0) { "Permits must be positive" }
        require(permits <= maxRequests) { "Cannot acquire more permits than max requests" }

        val currentWindow = getCurrentWindow()
        val counterKey = buildCounterKey(key, currentWindow)

        // Get current count or initialize to 0
        val currentCount =
            storage.get(counterKey) ?: run {
                // Initialize counter with TTL
                storage.set(counterKey, 0L, windowSize)
                0L
            }

        // Check if adding permits would exceed limit
        if (currentCount + permits > maxRequests) {
            return false
        }

        // Increment counter
        val newCount = storage.increment(counterKey, permits)

        // Ensure TTL is set (in case increment was first operation)
        if (storage.ttl(counterKey) == null) {
            storage.expire(counterKey, windowSize)
        }

        return newCount <= maxRequests
    }

    override fun availablePermits(key: String): Long? {
        val currentWindow = getCurrentWindow()
        val counterKey = buildCounterKey(key, currentWindow)

        val currentCount = storage.get(counterKey) ?: 0L

        return (maxRequests - currentCount).coerceAtLeast(0)
    }

    override fun reset(key: String) {
        // Delete all windows for this key
        // Note: This requires wildcard deletion which may not be efficient
        // For precise deletion, we'd need to track all window keys separately
        val currentWindow = getCurrentWindow()
        val counterKey = buildCounterKey(key, currentWindow)
        storage.delete(counterKey)
    }

    override fun algorithmName(): String = "FixedWindow"

    /**
     * Get current time window index
     *
     * Divides time into discrete windows based on window size.
     * Example: If window size is 60s, windows are 0-59s, 60-119s, 120-179s, etc.
     *
     * @return Window index (e.g., 0, 1, 2, ...)
     */
    private fun getCurrentWindow(): Long {
        val now = System.currentTimeMillis() / 1000 // Current time in seconds
        val windowSizeSeconds = windowSize.seconds

        return now / windowSizeSeconds
    }

    /**
     * Build counter key for specific window
     *
     * Format: ratelimit:fixed:{key}:window:{windowIndex}
     *
     * @param key Original rate limit key
     * @param window Window index
     * @return Redis/storage key
     */
    private fun buildCounterKey(
        key: String,
        window: Long,
    ): String = "$COUNTER_PREFIX:$key:window:$window"
}
