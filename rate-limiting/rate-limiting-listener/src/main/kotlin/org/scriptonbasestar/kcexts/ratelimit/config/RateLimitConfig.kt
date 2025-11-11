package org.scriptonbasestar.kcexts.ratelimit.config

import org.keycloak.models.RealmModel
import java.time.Duration

/**
 * Rate limiting configuration
 *
 * Loaded from:
 * 1. Realm Attributes (highest priority)
 * 2. System Properties
 * 3. Environment Variables
 * 4. Hardcoded Defaults (lowest priority)
 */
data class RateLimitConfig(
    val enabled: Boolean,
    val storageType: StorageType,
    val strategy: RateLimitStrategy,
    val algorithm: RateLimitAlgorithm,
    val defaultLimits: Map<String, LimitConfig>,
    val redisConfig: RedisConfig?,
) {
    companion object {
        private const val PREFIX = "ratelimit"

        fun fromRealm(realm: RealmModel): RateLimitConfig {
            val enabled = realm.getAttribute("$PREFIX.enabled")?.toBoolean() ?: true
            val storageType =
                realm.getAttribute("$PREFIX.storage.type")?.let {
                    StorageType.valueOf(it.uppercase())
                } ?: StorageType.MEMORY

            val strategy =
                realm.getAttribute("$PREFIX.strategy")?.let {
                    RateLimitStrategy.valueOf(it.uppercase())
                } ?: RateLimitStrategy.PER_USER

            val algorithm =
                realm.getAttribute("$PREFIX.algorithm")?.let {
                    RateLimitAlgorithm.valueOf(it.uppercase())
                } ?: RateLimitAlgorithm.TOKEN_BUCKET

            // Load default limits
            val defaultLimits = loadDefaultLimits(realm)

            // Load Redis config if storage type is REDIS
            val redisConfig =
                if (storageType == StorageType.REDIS) {
                    loadRedisConfig(realm)
                } else {
                    null
                }

            return RateLimitConfig(
                enabled = enabled,
                storageType = storageType,
                strategy = strategy,
                algorithm = algorithm,
                defaultLimits = defaultLimits,
                redisConfig = redisConfig,
            )
        }

        private fun loadDefaultLimits(realm: RealmModel): Map<String, LimitConfig> {
            val limits = mutableMapOf<String, LimitConfig>()

            // Per-user limits
            realm.getAttribute("$PREFIX.user.login.max")?.toLongOrNull()?.let { max ->
                val window = realm.getAttribute("$PREFIX.user.login.window")?.toLongOrNull() ?: 60
                limits["user:login"] =
                    LimitConfig(
                        maxRequests = max,
                        window = Duration.ofSeconds(window),
                        scope = "user",
                        eventType = "LOGIN",
                    )
            } ?: run {
                // Default: 10 login attempts per minute
                limits["user:login"] =
                    LimitConfig(
                        maxRequests = 10,
                        window = Duration.ofMinutes(1),
                        scope = "user",
                        eventType = "LOGIN",
                    )
            }

            // Per-IP limits for failed logins
            realm.getAttribute("$PREFIX.ip.login-error.max")?.toLongOrNull()?.let { max ->
                val window = realm.getAttribute("$PREFIX.ip.login-error.window")?.toLongOrNull() ?: 300
                limits["ip:login-error"] =
                    LimitConfig(
                        maxRequests = max,
                        window = Duration.ofSeconds(window),
                        scope = "ip",
                        eventType = "LOGIN_ERROR",
                    )
            } ?: run {
                // Default: 5 failed login attempts per 5 minutes
                limits["ip:login-error"] =
                    LimitConfig(
                        maxRequests = 5,
                        window = Duration.ofMinutes(5),
                        scope = "ip",
                        eventType = "LOGIN_ERROR",
                    )
            }

            // Per-client limits
            realm.getAttribute("$PREFIX.client.requests.max")?.toLongOrNull()?.let { max ->
                val window = realm.getAttribute("$PREFIX.client.requests.window")?.toLongOrNull() ?: 60
                limits["client:requests"] =
                    LimitConfig(
                        maxRequests = max,
                        window = Duration.ofSeconds(window),
                        scope = "client",
                        eventType = "*",
                    )
            } ?: run {
                // Default: 1000 requests per minute per client
                limits["client:requests"] =
                    LimitConfig(
                        maxRequests = 1000,
                        window = Duration.ofMinutes(1),
                        scope = "client",
                        eventType = "*",
                    )
            }

            return limits
        }

        private fun loadRedisConfig(realm: RealmModel): RedisConfig =
            RedisConfig(
                host = realm.getAttribute("$PREFIX.redis.host") ?: "localhost",
                port = realm.getAttribute("$PREFIX.redis.port")?.toIntOrNull() ?: 6379,
                password = realm.getAttribute("$PREFIX.redis.password"),
                database = realm.getAttribute("$PREFIX.redis.database")?.toIntOrNull() ?: 0,
                timeout =
                    realm.getAttribute("$PREFIX.redis.timeout")?.toLongOrNull()?.let { Duration.ofSeconds(it) }
                        ?: Duration.ofSeconds(5),
            )
    }
}

/**
 * Storage type for rate limiting
 */
enum class StorageType {
    MEMORY, // In-memory (single instance)
    REDIS, // Redis (distributed)
}

/**
 * Rate limiting strategy
 */
enum class RateLimitStrategy {
    PER_USER, // Limit per user ID
    PER_CLIENT, // Limit per client ID
    PER_REALM, // Limit per realm
    PER_IP, // Limit per IP address
    COMBINED, // Combine multiple strategies
}

/**
 * Rate limiting algorithm
 */
enum class RateLimitAlgorithm {
    TOKEN_BUCKET, // Token bucket algorithm
    SLIDING_WINDOW, // Sliding window algorithm
    FIXED_WINDOW, // Fixed window algorithm
}

/**
 * Limit configuration for a specific scope and event type
 */
data class LimitConfig(
    val maxRequests: Long,
    val window: Duration,
    val scope: String, // "user", "client", "realm", "ip"
    val eventType: String, // Event type or "*" for all events
)

/**
 * Redis configuration
 */
data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String?,
    val database: Int,
    val timeout: Duration,
)
