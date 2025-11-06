package org.scriptonbasestar.kcexts.events.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.XAddArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.connection.ConnectionException
import org.scriptonbasestar.kcexts.events.common.connection.EventConnectionManager
import org.scriptonbasestar.kcexts.events.redis.config.RedisEventListenerConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Redis Streams implementation of EventConnectionManager.
 *
 * Manages Redis connection lifecycle and message sending to streams.
 */
class RedisConnectionManager(
    private val config: RedisEventListenerConfig,
) : EventConnectionManager {
    private val logger = Logger.getLogger(RedisConnectionManager::class.java)
    private val redisClient: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val commands: RedisCommands<String, String>
    private val closed = AtomicBoolean(false)

    init {
        try {
            redisClient = RedisClient.create(config.redisUri)
            connection = redisClient.connect()
            commands = connection.sync()
            logger.info("Redis connection manager initialized with URI: ${config.redisUri}")
        } catch (e: Exception) {
            logger.error("Failed to initialize Redis connection manager", e)
            throw e
        }
    }

    /**
     * Send message to specified Redis Stream.
     *
     * @param destination Redis stream key name
     * @param message JSON-serialized event message
     * @return true if successfully sent, false on error
     * @throws ConnectionException if connection is closed
     */
    override fun send(
        destination: String,
        message: String,
    ): Boolean {
        if (closed.get()) {
            throw ConnectionException("Redis connection is closed, cannot send message")
        }

        return try {
            val fields = mapOf("message" to message)
            val args =
                XAddArgs.Builder
                    .maxlen(config.streamMaxLength)
                    .approximateTrimming()

            val messageId = commands.xadd(destination, args, fields)
            logger.debug("Message sent successfully to stream '$destination', message ID: $messageId")
            true
        } catch (e: Exception) {
            logger.error("Failed to send message to Redis stream '$destination'", e)
            false
        }
    }

    /**
     * Legacy method for backward compatibility - send event to Redis Stream.
     * Prefer using send() directly.
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
    fun sendUserEvent(fields: Map<String, String>): String? = sendEvent(config.userEventsStream, fields)

    /**
     * Send admin event to configured admin events stream
     */
    fun sendAdminEvent(fields: Map<String, String>): String? = sendEvent(config.adminEventsStream, fields)

    /**
     * Get stream length
     */
    fun getStreamLength(streamKey: String): Long =
        try {
            commands.xlen(streamKey)
        } catch (e: Exception) {
            logger.warn("Failed to get stream length for '$streamKey'", e)
            0L
        }

    override fun isConnected(): Boolean =
        try {
            !closed.get() && connection.isOpen && commands.ping() == "PONG"
        } catch (e: Exception) {
            logger.warn("Connection check failed", e)
            false
        }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                connection.close()
                redisClient.shutdown()
                logger.info("Redis connection manager closed successfully")
            } catch (e: Exception) {
                logger.error("Error closing Redis connection manager", e)
            }
        }
    }
}
