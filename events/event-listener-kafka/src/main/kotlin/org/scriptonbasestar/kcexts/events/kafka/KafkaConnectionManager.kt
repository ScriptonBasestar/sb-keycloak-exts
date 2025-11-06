package org.scriptonbasestar.kcexts.events.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.connection.ConnectionException
import org.scriptonbasestar.kcexts.events.common.connection.EventConnectionManager
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kafka implementation of EventConnectionManager.
 *
 * Manages Kafka producer lifecycle and message sending to topics.
 */
class KafkaConnectionManager(
    private val config: KafkaEventListenerConfig,
) : EventConnectionManager {
    private val logger = Logger.getLogger(KafkaConnectionManager::class.java)
    private val producer: KafkaProducer<String, String>
    private val closed = AtomicBoolean(false)

    init {
        val props =
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
                put(ProducerConfig.CLIENT_ID_CONFIG, config.clientId)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.ACKS_CONFIG, "1")
                put(ProducerConfig.RETRIES_CONFIG, 3)
                put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000)
                put(ProducerConfig.BATCH_SIZE_CONFIG, 16384)
                put(ProducerConfig.LINGER_MS_CONFIG, 5)
                put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432)
                put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000)
            }

        producer = KafkaProducer(props)
        logger.info("Kafka connection manager initialized with bootstrap servers: ${config.bootstrapServers}")
    }

    /**
     * Send message to specified Kafka topic.
     *
     * @param destination Kafka topic name
     * @param message JSON-serialized event message
     * @return true if successfully sent, false on error
     * @throws ConnectionException if connection is closed
     */
    override fun send(
        destination: String,
        message: String,
    ): Boolean {
        if (closed.get()) {
            throw ConnectionException("Kafka connection is closed, cannot send message")
        }

        return try {
            // Use destination as key for partitioning
            val record = ProducerRecord(destination, destination, message)
            val future = producer.send(record)

            // Wait for acknowledgment (blocking)
            val metadata = future.get()
            logger.debug(
                "Message sent successfully to topic '$destination', " +
                    "partition: ${metadata.partition()}, offset: ${metadata.offset()}",
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to send message to Kafka topic '$destination'", e)
            false
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Prefer using send() directly.
     */
    fun sendEvent(
        topic: String,
        key: String,
        value: String,
    ) {
        if (closed.get()) {
            logger.warn("Producer is closed, cannot send event")
            return
        }

        try {
            val record = ProducerRecord(topic, key, value)
            producer.send(record) { metadata, exception ->
                if (exception != null) {
                    logger.error("Failed to send event to Kafka topic '$topic'", exception)
                } else {
                    logger.debug(
                        "Event sent successfully to topic '$topic', partition: ${metadata.partition()}, offset: ${metadata.offset()}",
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error sending event to Kafka", e)
        }
    }

    fun sendUserEvent(
        key: String,
        value: String,
    ) {
        sendEvent(config.eventTopic, key, value)
    }

    fun sendAdminEvent(
        key: String,
        value: String,
    ) {
        sendEvent(config.adminEventTopic, key, value)
    }

    override fun isConnected(): Boolean = !closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                producer.flush()
                producer.close()
                logger.info("Kafka connection manager closed successfully")
            } catch (e: Exception) {
                logger.error("Error closing Kafka producer", e)
            }
        }
    }
}
