package org.scriptonbasestar.kcexts.events.nats

import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.connection.ConnectionException
import org.scriptonbasestar.kcexts.events.common.connection.EventConnectionManager
import java.time.Duration

/**
 * NATS implementation of EventConnectionManager.
 *
 * Manages NATS connection lifecycle and message publishing.
 */
class NatsConnectionManager(
    private val config: NatsEventListenerConfig,
) : EventConnectionManager {
    private val logger = Logger.getLogger(NatsConnectionManager::class.java)

    @Volatile
    private var connection: Connection? = null

    init {
        connect()
    }

    private fun connect() {
        try {
            val optionsBuilder =
                Options
                    .Builder()
                    .server(config.serverUrl)
                    .connectionTimeout(Duration.ofMillis(config.connectionTimeout.toLong()))
                    .maxReconnects(config.maxReconnects)
                    .reconnectWait(Duration.ofMillis(config.reconnectWait))
                    .maxPingsOut(config.maxPingsOut)
                    .noEcho()

            // Authentication
            config.token?.let { optionsBuilder.token(it.toCharArray()) }
            if (config.username != null && config.password != null) {
                optionsBuilder.userInfo(config.username, config.password)
            }

            // TLS
            if (config.useTls) {
                optionsBuilder.secure()
            }

            connection = Nats.connect(optionsBuilder.build())
            logger.info("Connected to NATS server: ${config.serverUrl}")
        } catch (e: Exception) {
            logger.error("Failed to connect to NATS server", e)
            throw e
        }
    }

    /**
     * Send message to specified NATS subject.
     *
     * @param destination NATS subject name
     * @param message Message content (JSON string)
     * @return true if successfully sent, false on error
     * @throws ConnectionException if connection is not active
     */
    override fun send(
        destination: String,
        message: String,
    ): Boolean =
        try {
            publish(destination, message)
            true
        } catch (e: Exception) {
            logger.error("Failed to send message to NATS subject '$destination'", e)
            false
        }

    /**
     * Publish a message to a subject (legacy method for backward compatibility).
     * Prefer using send() for standard interface.
     */
    fun publish(
        subject: String,
        message: String,
    ) {
        try {
            val conn = connection ?: throw IllegalStateException("Not connected to NATS")

            if (!conn.status.equals(Connection.Status.CONNECTED)) {
                logger.warn("NATS connection not active, attempting reconnect")
                connect()
            }

            connection?.publish(subject, message.toByteArray(Charsets.UTF_8))
            logger.debug("Published message to subject: $subject")
        } catch (e: Exception) {
            logger.error("Failed to publish message to NATS", e)
            throw e
        }
    }

    /**
     * Close the NATS connection
     */
    override fun close() {
        try {
            connection?.close()
            logger.info("NATS connection closed")
        } catch (e: Exception) {
            logger.error("Error closing NATS connection", e)
        } finally {
            connection = null
        }
    }

    /**
     * Check if connected
     */
    override fun isConnected(): Boolean = connection?.status == Connection.Status.CONNECTED
}
