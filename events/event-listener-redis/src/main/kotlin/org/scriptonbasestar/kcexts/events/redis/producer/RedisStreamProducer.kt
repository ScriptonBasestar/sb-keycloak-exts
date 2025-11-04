package org.scriptonbasestar.kcexts.events.redis.producer

import io.lettuce.core.RedisClient
import io.lettuce.core.XAddArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.redis.config.RedisEventListenerConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Redis Streams producer for publishing Keycloak events
 */
class RedisStreamProducer(
    private val config: RedisEventListenerConfig,
) {
    private val logger = Logger.getLogger(RedisStreamProducer::class.java)
    private val redisClient: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val commands: RedisCommands<String, String>
    private val closed = AtomicBoolean(false)

    init {
        try {
            redisClient = RedisClient.create(config.redisUri)
            connection = redisClient.connect()
            commands = connection.sync()
            logger.info("Redis Streams producer initialized with URI: ${config.redisUri}")
        } catch (e: Exception) {
            logger.error("Failed to initialize Redis Streams producer", e)
            throw e
        }
    }

    /**
     * Send event to Redis Stream
     */
    fun sendEvent(
        streamKey: String,
        fields: Map<String, String>,
    ): String? {
        if (closed.get()) {
            logger.warn("Producer is closed, cannot send event")
            return null
        }

        return try {
            val args =
                XAddArgs.Builder
                    .maxlen(config.streamMaxLength)
                    .approximateTrimming()

            val messageId = commands.xadd(streamKey, args, fields)
            logger.debug("Event sent successfully to stream '$streamKey', message ID: $messageId")
            messageId
        } catch (e: Exception) {
            logger.error("Error sending event to Redis stream '$streamKey'", e)
            throw e
        }
    }

    /**
     * Send user event to configured user events stream
     */
    fun sendUserEvent(fields: Map<String, String>): String? {
        return sendEvent(config.userEventsStream, fields)
    }

    /**
     * Send admin event to configured admin events stream
     */
    fun sendAdminEvent(fields: Map<String, String>): String? {
        return sendEvent(config.adminEventsStream, fields)
    }

    /**
     * Check if connection is healthy
     */
    fun isHealthy(): Boolean {
        return try {
            !closed.get() && connection.isOpen && commands.ping() == "PONG"
        } catch (e: Exception) {
            logger.warn("Health check failed", e)
            false
        }
    }

    /**
     * Get stream length
     */
    fun getStreamLength(streamKey: String): Long {
        return try {
            commands.xlen(streamKey)
        } catch (e: Exception) {
            logger.warn("Failed to get stream length for '$streamKey'", e)
            0L
        }
    }

    /**
     * Close Redis connection
     */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                connection.close()
                redisClient.shutdown()
                logger.info("Redis Streams producer closed successfully")
            } catch (e: Exception) {
                logger.error("Error closing Redis Streams producer", e)
            }
        }
    }
}
