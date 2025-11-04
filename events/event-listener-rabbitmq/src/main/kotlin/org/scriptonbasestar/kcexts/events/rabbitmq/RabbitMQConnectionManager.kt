package org.scriptonbasestar.kcexts.events.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import org.jboss.logging.Logger

class RabbitMQConnectionManager(
    private val config: RabbitMQEventListenerConfig,
) {
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
    fun close() {
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

    fun isConnected(): Boolean = connection?.isOpen == true && channel?.isOpen == true
}
