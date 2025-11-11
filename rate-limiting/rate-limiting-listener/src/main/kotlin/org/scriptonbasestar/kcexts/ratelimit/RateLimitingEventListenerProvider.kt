package org.scriptonbasestar.kcexts.ratelimit

import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.ratelimit.config.LimitConfig
import org.scriptonbasestar.kcexts.ratelimit.config.RateLimitConfig
import org.scriptonbasestar.kcexts.ratelimit.config.RateLimitStrategy
import org.scriptonbasestar.kcexts.ratelimit.exception.RateLimitExceededException
import org.scriptonbasestar.kcexts.ratelimit.limiter.RateLimiter

/**
 * Keycloak Event Listener for Rate Limiting
 *
 * Intercepts Keycloak events and applies rate limiting based on configuration.
 * Throws RateLimitExceededException when limit is exceeded, which Keycloak
 * will convert to HTTP 429 response.
 */
class RateLimitingEventListenerProvider(
    private val session: KeycloakSession,
    private val config: RateLimitConfig,
    private val rateLimiters: Map<String, RateLimiter>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(RateLimitingEventListenerProvider::class.java)

    override fun onEvent(event: Event) {
        if (!config.enabled) {
            return
        }

        try {
            // Check rate limits based on configuration
            checkRateLimit(event)
        } catch (e: RateLimitExceededException) {
            logger.warn(
                "Rate limit exceeded: type=${event.type}, user=${event.userId}, " +
                    "client=${event.clientId}, ip=${event.ipAddress}, realm=${event.realmId}",
            )
            throw e
        } catch (e: Exception) {
            logger.error("Error checking rate limit", e)
            // Don't block request on rate limiter error
        }
    }

    override fun onEvent(
        event: AdminEvent,
        includeRepresentation: Boolean,
    ) {
        // Admin events can also be rate limited if needed
        // For now, we focus on user events
    }

    override fun close() {
        // No resources to close (storage is managed by factory)
    }

    /**
     * Check rate limit for an event
     */
    private fun checkRateLimit(event: Event) {
        val eventType = event.type.toString()

        // Find applicable limit configs
        val applicableLimits = findApplicableLimits(eventType)

        if (applicableLimits.isEmpty()) {
            logger.trace("No rate limits configured for event type: $eventType")
            return
        }

        // Check each applicable limit
        applicableLimits.forEach { (scope, limitConfig) ->
            val key = buildRateLimitKey(event, scope, limitConfig)
            val rateLimiter = rateLimiters[scope] ?: return@forEach

            logger.trace("Checking rate limit: key=$key, limit=${limitConfig.maxRequests}/${limitConfig.window}")

            if (!rateLimiter.tryAcquire(key)) {
                val available = rateLimiter.availablePermits(key) ?: 0

                throw RateLimitExceededException(
                    message = "Rate limit exceeded for $scope",
                    retryAfter = limitConfig.window,
                    currentTokens = available,
                    limitConfig = "${limitConfig.maxRequests} requests per ${limitConfig.window.seconds}s",
                )
            }

            logger.debug(
                "Rate limit check passed: key=$key, remaining=${rateLimiter.availablePermits(key)}",
            )
        }
    }

    /**
     * Find applicable limit configurations for an event type
     */
    private fun findApplicableLimits(eventType: String): Map<String, LimitConfig> {
        val result = mutableMapOf<String, LimitConfig>()

        config.defaultLimits.forEach { (scope, limitConfig) ->
            // Check if this limit applies to this event type
            if (limitConfig.eventType == "*" || limitConfig.eventType == eventType) {
                result[scope] = limitConfig
            }
        }

        return result
    }

    /**
     * Build rate limit key based on strategy and scope
     */
    private fun buildRateLimitKey(
        event: Event,
        scope: String,
        limitConfig: LimitConfig,
    ): String =
        when (config.strategy) {
            RateLimitStrategy.PER_USER -> {
                val userId = event.userId ?: "anonymous"
                "ratelimit:user:$userId:${limitConfig.eventType}"
            }

            RateLimitStrategy.PER_CLIENT -> {
                val clientId = event.clientId ?: "unknown"
                "ratelimit:client:$clientId:${limitConfig.eventType}"
            }

            RateLimitStrategy.PER_REALM -> {
                val realmId = event.realmId ?: "unknown"
                "ratelimit:realm:$realmId:${limitConfig.eventType}"
            }

            RateLimitStrategy.PER_IP -> {
                val ipAddress = event.ipAddress ?: "unknown"
                "ratelimit:ip:$ipAddress:${limitConfig.eventType}"
            }

            RateLimitStrategy.COMBINED -> {
                // Combine user + IP for stronger security
                val userId = event.userId ?: "anonymous"
                val ipAddress = event.ipAddress ?: "unknown"
                "ratelimit:combined:$userId:$ipAddress:${limitConfig.eventType}"
            }
        }
}
