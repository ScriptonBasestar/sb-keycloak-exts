package org.scriptonbasestar.kcexts.ratelimit

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.ratelimit.config.RateLimitAlgorithm
import org.scriptonbasestar.kcexts.ratelimit.config.RateLimitConfig
import org.scriptonbasestar.kcexts.ratelimit.config.StorageType
import org.scriptonbasestar.kcexts.ratelimit.limiter.FixedWindowRateLimiter
import org.scriptonbasestar.kcexts.ratelimit.limiter.RateLimiter
import org.scriptonbasestar.kcexts.ratelimit.limiter.SlidingWindowRateLimiter
import org.scriptonbasestar.kcexts.ratelimit.limiter.TokenBucketRateLimiter
import org.scriptonbasestar.kcexts.ratelimit.storage.InMemoryStorage
import org.scriptonbasestar.kcexts.ratelimit.storage.RateLimitStorage

/**
 * Factory for Rate Limiting Event Listener
 *
 * Creates and manages RateLimitingEventListenerProvider instances.
 * Initializes storage backends and rate limiters based on configuration.
 */
class RateLimitingEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(RateLimitingEventListenerProviderFactory::class.java)

    // Shared storage backend (singleton per Keycloak instance)
    private var storage: RateLimitStorage? = null

    // Rate limiters per scope
    private val rateLimiters = mutableMapOf<String, RateLimiter>()

    override fun create(session: KeycloakSession): EventListenerProvider {
        val realm = session.context.realm ?: throw IllegalStateException("Realm not available in session context")

        // Load configuration from realm
        val config = RateLimitConfig.fromRealm(realm)

        // Initialize storage if not already done
        if (storage == null) {
            storage = createStorage(config)
            logger.info("Rate limiting storage initialized: ${config.storageType}")
        }

        // Initialize rate limiters if not already done
        if (rateLimiters.isEmpty()) {
            initializeRateLimiters(config, storage!!)
            logger.info("Rate limiters initialized: ${rateLimiters.size} limiters")
        }

        return RateLimitingEventListenerProvider(
            session = session,
            config = config,
            rateLimiters = rateLimiters,
        )
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing Rate Limiting Event Listener Factory")
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        logger.info("Rate Limiting Event Listener Factory post-init complete")
    }

    override fun close() {
        logger.info("Closing Rate Limiting Event Listener Factory")

        // Close storage
        storage?.close()
        storage = null

        // Clear rate limiters
        rateLimiters.clear()
    }

    override fun getId(): String = "rate-limiting"

    /**
     * Create storage backend based on configuration
     */
    private fun createStorage(config: RateLimitConfig): RateLimitStorage =
        when (config.storageType) {
            StorageType.MEMORY -> {
                logger.info("Using in-memory storage for rate limiting")
                InMemoryStorage()
            }

            StorageType.REDIS -> {
                if (config.redisConfig == null) {
                    logger.warn("Redis configuration missing, falling back to in-memory")
                    InMemoryStorage()
                } else {
                    logger.info(
                        "Using Redis storage for rate limiting: ${config.redisConfig.host}:${config.redisConfig.port}",
                    )
                    org.scriptonbasestar.kcexts.ratelimit.storage
                        .RedisStorage(config.redisConfig)
                }
            }
        }

    /**
     * Initialize rate limiters for each configured limit
     */
    private fun initializeRateLimiters(
        config: RateLimitConfig,
        storage: RateLimitStorage,
    ) {
        config.defaultLimits.forEach { (scope, limitConfig) ->
            val rateLimiter = createRateLimiter(config.algorithm, limitConfig, storage)
            rateLimiters[scope] = rateLimiter

            logger.info(
                "Rate limiter created: scope=$scope, algorithm=${config.algorithm}, " +
                    "limit=${limitConfig.maxRequests}/${limitConfig.window}",
            )
        }
    }

    /**
     * Create rate limiter based on algorithm
     */
    private fun createRateLimiter(
        algorithm: RateLimitAlgorithm,
        limitConfig: org.scriptonbasestar.kcexts.ratelimit.config.LimitConfig,
        storage: RateLimitStorage,
    ): RateLimiter =
        when (algorithm) {
            RateLimitAlgorithm.TOKEN_BUCKET -> {
                TokenBucketRateLimiter(
                    storage = storage,
                    capacity = limitConfig.maxRequests,
                    refillRate = limitConfig.maxRequests,
                    refillPeriod = limitConfig.window,
                )
            }

            RateLimitAlgorithm.SLIDING_WINDOW -> {
                SlidingWindowRateLimiter(
                    storage = storage,
                    windowSize = limitConfig.window,
                    maxRequests = limitConfig.maxRequests,
                )
            }

            RateLimitAlgorithm.FIXED_WINDOW -> {
                FixedWindowRateLimiter(
                    storage = storage,
                    windowSize = limitConfig.window,
                    maxRequests = limitConfig.maxRequests,
                )
            }
        }
}
