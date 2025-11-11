package org.scriptonbasestar.kcexts.ratelimit.exception

import java.time.Duration

/**
 * Exception thrown when rate limit is exceeded
 *
 * @param message Error message
 * @param retryAfter Duration to wait before retrying
 * @param currentTokens Current number of tokens/requests available
 * @param limitConfig Limit configuration that was exceeded
 */
class RateLimitExceededException(
    message: String,
    val retryAfter: Duration? = null,
    val currentTokens: Long = 0,
    val limitConfig: String? = null,
) : RuntimeException(message) {
    override fun toString(): String {
        val parts = mutableListOf("RateLimitExceededException: $message")
        retryAfter?.let { parts.add("retryAfter=${it.seconds}s") }
        limitConfig?.let { parts.add("limit=$it") }
        parts.add("currentTokens=$currentTokens")
        return parts.joinToString(", ")
    }
}
