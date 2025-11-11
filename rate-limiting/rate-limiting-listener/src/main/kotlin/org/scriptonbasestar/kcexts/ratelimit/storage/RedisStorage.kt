package org.scriptonbasestar.kcexts.ratelimit.storage

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.ratelimit.config.RedisConfig
import java.time.Duration

/**
 * Redis storage backend for rate limiting
 *
 * Suitable for:
 * - Multi-instance Keycloak deployments
 * - High availability requirements
 * - Distributed rate limiting
 *
 * Features:
 * - Atomic operations via Lua scripts
 * - Connection pooling
 * - Automatic reconnection
 * - TTL support for all keys
 */
class RedisStorage(
    private val config: RedisConfig,
) : RateLimitStorage {
    private val logger = Logger.getLogger(RedisStorage::class.java)

    private val redisClient: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val commands: RedisCommands<String, String>

    // Lua script SHA digests (loaded on initialization)
    private var incrementScriptSha: String? = null

    init {
        try {
            // Build Redis URI
            val redisUriBuilder =
                RedisURI
                    .builder()
                    .withHost(config.host)
                    .withPort(config.port)
                    .withDatabase(config.database)
                    .withTimeout(config.timeout)

            config.password?.let { redisUriBuilder.withPassword(it.toCharArray()) }

            val redisUri = redisUriBuilder.build()

            // Create client and connection
            redisClient = RedisClient.create(redisUri)
            connection = redisClient.connect()
            commands = connection.sync()

            // Load Lua scripts
            loadLuaScripts()

            logger.info(
                "Redis storage initialized: ${config.host}:${config.port}, database=${config.database}",
            )
        } catch (e: Exception) {
            logger.error("Failed to initialize Redis storage", e)
            throw e
        }
    }

    override fun get(key: String): Long? =
        try {
            commands.get(key)?.toLongOrNull()
        } catch (e: Exception) {
            logger.error("Failed to get key: $key", e)
            null
        }

    override fun set(
        key: String,
        value: Long,
        ttl: Duration,
    ) {
        try {
            val ttlSeconds = maxOf(1, ttl.seconds) // Ensure at least 1 second
            commands.setex(key, ttlSeconds, value.toString())
        } catch (e: Exception) {
            logger.error("Failed to set key: $key", e)
            throw e
        }
    }

    override fun increment(
        key: String,
        delta: Long,
    ): Long =
        try {
            // Use simple INCRBY - TTL should be managed separately via set() or expire()
            commands.incrby(key, delta)
        } catch (e: Exception) {
            logger.error("Failed to increment key: $key", e)
            throw e
        }

    override fun decrement(
        key: String,
        delta: Long,
    ): Long = increment(key, -delta)

    override fun delete(key: String): Boolean =
        try {
            commands.del(key) > 0
        } catch (e: Exception) {
            logger.error("Failed to delete key: $key", e)
            false
        }

    override fun exists(key: String): Boolean =
        try {
            commands.exists(key) > 0
        } catch (e: Exception) {
            logger.error("Failed to check key existence: $key", e)
            false
        }

    override fun expire(
        key: String,
        ttl: Duration,
    ): Boolean =
        try {
            commands.expire(key, ttl.seconds)
        } catch (e: Exception) {
            logger.error("Failed to set expiration for key: $key", e)
            false
        }

    override fun ttl(key: String): Duration? =
        try {
            val seconds = commands.ttl(key)
            if (seconds > 0) {
                Duration.ofSeconds(seconds)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to get TTL for key: $key", e)
            null
        }

    override fun close() {
        try {
            connection.close()
            redisClient.shutdown()
            logger.info("Redis storage closed")
        } catch (e: Exception) {
            logger.error("Error closing Redis storage", e)
        }
    }

    /**
     * Load Lua scripts into Redis for atomic operations
     */
    private fun loadLuaScripts() {
        try {
            // Atomic increment with TTL script
            val incrementScript =
                """
                local key = KEYS[1]
                local delta = tonumber(ARGV[1])
                local ttl = tonumber(ARGV[2])

                local current = redis.call('GET', key)
                local newValue

                if current then
                    newValue = tonumber(current) + delta
                else
                    newValue = delta
                end

                redis.call('SET', key, newValue, 'EX', ttl)
                return newValue
                """.trimIndent()

            incrementScriptSha = commands.scriptLoad(incrementScript)
            logger.debug("Loaded increment Lua script: $incrementScriptSha")
        } catch (e: Exception) {
            logger.warn("Failed to load Lua scripts, using fallback commands", e)
        }
    }

    /**
     * Execute Lua script for Token Bucket algorithm
     *
     * Returns 1 if allowed, 0 if denied
     */
    fun tryAcquireTokenBucket(
        key: String,
        capacity: Long,
        refillRate: Long,
        refillPeriodSeconds: Long,
    ): Boolean {
        try {
            val script =
                """
                local bucketKey = KEYS[1]
                local timestampKey = KEYS[2]
                local capacity = tonumber(ARGV[1])
                local refillRate = tonumber(ARGV[2])
                local refillPeriod = tonumber(ARGV[3])
                local now = tonumber(ARGV[4])

                local tokens = tonumber(redis.call('GET', bucketKey)) or capacity
                local lastRefill = tonumber(redis.call('GET', timestampKey)) or now

                -- Calculate refill
                local elapsed = now - lastRefill
                local tokensToAdd = math.floor((elapsed / refillPeriod) * refillRate)
                tokens = math.min(capacity, tokens + tokensToAdd)

                -- Try to consume 1 token
                if tokens >= 1 then
                    tokens = tokens - 1
                    local ttl = refillPeriod * 2
                    redis.call('SETEX', bucketKey, ttl, tokens)
                    redis.call('SETEX', timestampKey, ttl, now)
                    return 1
                else
                    return 0
                end
                """.trimIndent()

            val now = System.currentTimeMillis()
            val timestampKey = "$key:time"
            val refillPeriodMs = refillPeriodSeconds * 1000 // Convert to milliseconds

            val result =
                commands.eval<Long>(
                    script,
                    ScriptOutputType.INTEGER,
                    arrayOf(key, timestampKey),
                    capacity.toString(),
                    refillRate.toString(),
                    refillPeriodMs.toString(),
                    now.toString(),
                )

            return result == 1L
        } catch (e: Exception) {
            logger.error("Failed to execute Token Bucket Lua script", e)
            throw e
        }
    }

    /**
     * Execute Lua script for Sliding Window algorithm
     *
     * Returns 1 if allowed, 0 if denied
     */
    fun tryAcquireSlidingWindow(
        key: String,
        windowSizeMs: Long,
        maxRequests: Long,
    ): Boolean {
        try {
            val script =
                """
                local key = KEYS[1]
                local windowSize = tonumber(ARGV[1])
                local maxRequests = tonumber(ARGV[2])
                local now = tonumber(ARGV[3])
                local requestId = ARGV[4]

                -- Remove old entries
                local cutoff = now - windowSize
                redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)

                -- Count current requests
                local count = redis.call('ZCARD', key)

                if count < maxRequests then
                    redis.call('ZADD', key, now, requestId)
                    redis.call('EXPIRE', key, math.ceil(windowSize / 1000))
                    return 1
                else
                    return 0
                end
                """.trimIndent()

            val now = System.currentTimeMillis()
            val requestId = "$now-${(Math.random() * 1000000).toLong()}"

            val result =
                commands.eval<Long>(
                    script,
                    ScriptOutputType.INTEGER,
                    arrayOf(key),
                    windowSizeMs.toString(),
                    maxRequests.toString(),
                    now.toString(),
                    requestId,
                )

            return result == 1L
        } catch (e: Exception) {
            logger.error("Failed to execute Sliding Window Lua script", e)
            throw e
        }
    }

    /**
     * Get available permits for Token Bucket
     */
    fun getTokenBucketPermits(
        key: String,
        capacity: Long,
        refillRate: Long,
        refillPeriodSeconds: Long,
    ): Long {
        try {
            val bucketKey = key
            val timestampKey = "$key:time"

            val currentTokens = get(bucketKey) ?: return capacity
            val lastRefillTime = get(timestampKey) ?: return capacity

            val now = System.currentTimeMillis()
            val elapsed = now - lastRefillTime
            val refillPeriodMs = refillPeriodSeconds * 1000

            val tokensToAdd =
                if (elapsed >= refillPeriodMs) {
                    val intervals = elapsed / refillPeriodMs
                    intervals * refillRate
                } else {
                    0L
                }

            return minOf(currentTokens + tokensToAdd, capacity)
        } catch (e: Exception) {
            logger.error("Failed to get Token Bucket permits", e)
            return 0
        }
    }

    /**
     * Get available permits for Sliding Window
     */
    fun getSlidingWindowPermits(
        key: String,
        windowSizeMs: Long,
        maxRequests: Long,
    ): Long =
        try {
            val now = System.currentTimeMillis()
            val cutoff = now - windowSizeMs

            // Remove old entries
            commands.zremrangebyscore(key, Double.NEGATIVE_INFINITY, cutoff.toDouble())

            // Count current requests
            val count = commands.zcard(key)

            maxOf(0, maxRequests - count)
        } catch (e: Exception) {
            logger.error("Failed to get Sliding Window permits", e)
            0
        }

    /**
     * Flush all data from the current database
     * WARNING: This is for testing only!
     */
    fun flushDatabase() {
        try {
            commands.flushdb()
            logger.debug("Flushed Redis database")
        } catch (e: Exception) {
            logger.error("Failed to flush database", e)
            throw e
        }
    }
}
