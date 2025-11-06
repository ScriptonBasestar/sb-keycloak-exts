package org.scriptonbasestar.kcexts.events.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.connection.EventConnectionManager

/**
 * RabbitMQ implementation of EventConnectionManager.
 *
 * Manages RabbitMQ connection lifecycle and message publishing.
 */
class RabbitMQConnectionManager(
    private val config: RabbitMQEventListenerConfig,
) : EventConnectionManager {
    private val logger = Logger.getLogger(RabbitMQConnectionManager::class.java)

    @Volatile
    private var connection: Connection? = null

    @Volatile
    private var channel: Channel? = null

    @Synchronized
    fun getChannel(): Channel {
        if (channel == null || !channel!!.isOpen) {
            initializeConnection()
        }
        return channel!!
    }

    @Synchronized
    private fun initializeConnection() {
        try {
            // Close existing connection if any
            close()

            // Create connection factory
            val factory =
                ConnectionFactory().apply {
                    host = config.host
                    port = config.port
                    username = config.username
                    password = config.password
                    virtualHost = config.virtualHost
                    connectionTimeout = config.connectionTimeout
                    requestedHeartbeat = config.requestedHeartbeat
                    networkRecoveryInterval = config.networkRecoveryInterval
                    isAutomaticRecoveryEnabled = config.automaticRecoveryEnabled

                    if (config.useSsl) {
                        useSslProtocol()
                    }
                }

            // Create connection
            connection = factory.newConnection("keycloak-rabbitmq-event-listener")
            logger.info("RabbitMQ connection established: ${config.host}:${config.port}")

            // Create channel
            channel = connection!!.createChannel()

            // Enable publisher confirms if configured
            if (config.publisherConfirms) {
                channel!!.confirmSelect()
                logger.info("Publisher confirms enabled")
            }

            // Declare exchange
            channel!!.exchangeDeclare(
                config.exchangeName,
                config.exchangeType,
                config.exchangeDurable,
            )
            logger.info("Exchange declared: ${config.exchangeName} (${config.exchangeType})")
        } catch (e: Exception) {
            logger.error("Failed to initialize RabbitMQ connection", e)
            throw e
        }
    }

    /**
     * Send message to RabbitMQ exchange with routing key.
     *
     * @param destination Routing key for message routing
     * @param message Message content (JSON string)
     * @return true if successfully sent, false on error
     */
    override fun send(
        destination: String,
        message: String,
    ): Boolean =
        try {
            publishMessage(destination, message)
            true
        } catch (e: Exception) {
            logger.error("Failed to send message to RabbitMQ with routing key '$destination'", e)
            false
        }

    /**
     * Publish message to RabbitMQ (legacy method for backward compatibility).
     * Prefer using send() for standard interface.
     */
    fun publishMessage(
        routingKey: String,
        message: String,
    ) {
        try {
            val channel = getChannel()

            // Publish message
            channel.basicPublish(
                config.exchangeName,
                routingKey,
                null,
                message.toByteArray(Charsets.UTF_8),
            )

            // Wait for publisher confirms if enabled
            if (config.publisherConfirms) {
                val confirmed = channel.waitForConfirms(config.publisherConfirmTimeout)
                if (!confirmed) {
                    logger.warn("Publisher confirm timeout for routing key: $routingKey")
                }
            }

            logger.debug("Message published to RabbitMQ: routingKey=$routingKey")
        } catch (e: Exception) {
            logger.error("Failed to publish message to RabbitMQ", e)
            throw e
        }
    }

    @Synchronized
    override fun close() {
        try {
            channel?.let {
                if (it.isOpen) {
                    it.close()
                    logger.debug("RabbitMQ channel closed")
                }
            }
            channel = null

            connection?.let {
                if (it.isOpen) {
                    it.close()
                    logger.info("RabbitMQ connection closed")
                }
            }
            connection = null
        } catch (e: Exception) {
            logger.warn("Error while closing RabbitMQ connection", e)
        }
    }

    override fun isConnected(): Boolean = connection?.isOpen == true && channel?.isOpen == true
}
