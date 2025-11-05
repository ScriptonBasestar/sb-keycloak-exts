package org.scriptonbasestar.kcexts.events.azure.sender

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusMessage
import com.azure.messaging.servicebus.ServiceBusSenderClient
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.azure.config.AzureEventListenerConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Azure Service Bus message sender for Keycloak events
 */
class AzureServiceBusSender(
    private val config: AzureEventListenerConfig,
) {
    private val logger = Logger.getLogger(AzureServiceBusSender::class.java)
    private val closed = AtomicBoolean(false)

    private val queueSenders = ConcurrentHashMap<String, ServiceBusSenderClient>()
    private val topicSenders = ConcurrentHashMap<String, ServiceBusSenderClient>()

    /**
     * Send message to queue
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
     * Send message to topic
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

    /**
     * Check if sender is healthy
     */
    fun isHealthy(): Boolean {
        return try {
            !closed.get()
        } catch (e: Exception) {
            logger.warn("Health check failed", e)
            false
        }
    }

    /**
     * Close all senders
     */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                queueSenders.values.forEach { it.close() }
                topicSenders.values.forEach { it.close() }
                queueSenders.clear()
                topicSenders.clear()
                logger.info("Azure Service Bus senders closed successfully")
            } catch (e: Exception) {
                logger.error("Error closing Azure Service Bus senders", e)
            }
        }
    }
}
