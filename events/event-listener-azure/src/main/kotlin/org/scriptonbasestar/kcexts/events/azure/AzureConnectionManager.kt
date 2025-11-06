package org.scriptonbasestar.kcexts.events.azure

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusMessage
import com.azure.messaging.servicebus.ServiceBusSenderClient
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.azure.config.AzureEventListenerConfig
import org.scriptonbasestar.kcexts.events.common.connection.ConnectionException
import org.scriptonbasestar.kcexts.events.common.connection.EventConnectionManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Azure Service Bus implementation of EventConnectionManager.
 *
 * Manages Azure Service Bus sender lifecycle and message sending to queues/topics.
 */
class AzureConnectionManager(
    private val config: AzureEventListenerConfig,
) : EventConnectionManager {
    private val logger = Logger.getLogger(AzureConnectionManager::class.java)
    private val closed = AtomicBoolean(false)

    private val queueSenders = ConcurrentHashMap<String, ServiceBusSenderClient>()
    private val topicSenders = ConcurrentHashMap<String, ServiceBusSenderClient>()

    /**
     * Send message to specified Azure Service Bus destination.
     *
     * @param destination Queue or topic name (format: "queue:name" or "topic:name")
     * @param message JSON-serialized event message
     * @return true if successfully sent, false on error
     * @throws ConnectionException if connection is closed
     */
    override fun send(
        destination: String,
        message: String,
    ): Boolean {
        if (closed.get()) {
            throw ConnectionException("Azure Service Bus connection is closed, cannot send message")
        }

        return try {
            // Parse destination format: "queue:name" or "topic:name"
            when {
                destination.startsWith("queue:") -> {
                    val queueName = destination.removePrefix("queue:")
                    sendToQueue(queueName, message, emptyMap())
                }
                destination.startsWith("topic:") -> {
                    val topicName = destination.removePrefix("topic:")
                    sendToTopic(topicName, message, emptyMap())
                }
                else -> {
                    // Default: treat as queue name
                    sendToQueue(destination, message, emptyMap())
                }
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to send message to Azure Service Bus destination '$destination'", e)
            false
        }
    }

    /**
     * Legacy method for backward compatibility - send message to queue.
     * Prefer using send() directly.
     */
    fun sendToQueue(
        queueName: String,
        messageBody: String,
        properties: Map<String, String>,
    ) {
        if (closed.get()) {
            logger.warn("Sender is closed, cannot send message")
            return
        }

        try {
            val sender = queueSenders.computeIfAbsent(queueName) { createQueueSender(it) }
            val message =
                ServiceBusMessage(messageBody).apply {
                    properties.forEach { (key, value) ->
                        applicationProperties[key] = value
                    }
                }

            sender.sendMessage(message)
            logger.debug("Message sent to queue: $queueName")
        } catch (e: Exception) {
            logger.error("Error sending message to queue: $queueName", e)
            throw e
        }
    }

    /**
     * Legacy method for backward compatibility - send message to topic.
     * Prefer using send() directly.
     */
    fun sendToTopic(
        topicName: String,
        messageBody: String,
        properties: Map<String, String>,
    ) {
        if (closed.get()) {
            logger.warn("Sender is closed, cannot send message")
            return
        }

        try {
            val sender = topicSenders.computeIfAbsent(topicName) { createTopicSender(it) }
            val message =
                ServiceBusMessage(messageBody).apply {
                    properties.forEach { (key, value) ->
                        applicationProperties[key] = value
                    }
                }

            sender.sendMessage(message)
            logger.debug("Message sent to topic: $topicName")
        } catch (e: Exception) {
            logger.error("Error sending message to topic: $topicName", e)
            throw e
        }
    }

    /**
     * Send user event to configured destination
     */
    fun sendUserEvent(
        messageBody: String,
        properties: Map<String, String>,
    ) {
        when {
            config.useQueue -> sendToQueue(config.userEventsQueueName, messageBody, properties)
            config.useTopic -> sendToTopic(config.userEventsTopicName, messageBody, properties)
            else -> logger.warn("No user events destination configured")
        }
    }

    /**
     * Send admin event to configured destination
     */
    fun sendAdminEvent(
        messageBody: String,
        properties: Map<String, String>,
    ) {
        when {
            config.useQueue -> sendToQueue(config.adminEventsQueueName, messageBody, properties)
            config.useTopic -> sendToTopic(config.adminEventsTopicName, messageBody, properties)
            else -> logger.warn("No admin events destination configured")
        }
    }

    private fun createQueueSender(queueName: String): ServiceBusSenderClient {
        logger.info("Creating queue sender for: $queueName")
        return try {
            baseClientBuilder()
                .sender()
                .queueName(queueName)
                .buildClient()
        } catch (e: Exception) {
            logger.error("Failed to create queue sender for $queueName", e)
            throw e
        }
    }

    private fun createTopicSender(topicName: String): ServiceBusSenderClient {
        logger.info("Creating topic sender for: $topicName")
        return try {
            baseClientBuilder()
                .sender()
                .topicName(topicName)
                .buildClient()
        } catch (e: Exception) {
            logger.error("Failed to create topic sender for $topicName", e)
            throw e
        }
    }

    private fun baseClientBuilder(): ServiceBusClientBuilder {
        val builder = ServiceBusClientBuilder()

        return when {
            config.useManagedIdentity -> {
                if (config.fullyQualifiedNamespace.isBlank()) {
                    throw IllegalStateException(
                        "azure.servicebus.namespace must be configured when using managed identity",
                    )
                }

                val credentialBuilder = DefaultAzureCredentialBuilder()
                config.managedIdentityClientId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { credentialBuilder.managedIdentityClientId(it) }

                builder.credential(config.fullyQualifiedNamespace, credentialBuilder.build())
            }

            config.connectionString.isNotBlank() -> builder.connectionString(config.connectionString)

            else -> throw IllegalStateException(
                "Azure Service Bus connection credentials are not configured",
            )
        }
    }

    override fun isConnected(): Boolean =
        try {
            !closed.get()
        } catch (e: Exception) {
            logger.warn("Connection check failed", e)
            false
        }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                queueSenders.values.forEach { it.close() }
                topicSenders.values.forEach { it.close() }
                queueSenders.clear()
                topicSenders.clear()
                logger.info("Azure Service Bus connection manager closed successfully")
            } catch (e: Exception) {
                logger.error("Error closing Azure Service Bus senders", e)
            }
        }
    }
}
