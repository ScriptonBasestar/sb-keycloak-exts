package org.scriptonbasestar.kcexts.events.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class KafkaProducerManager(private val config: KafkaEventListenerConfig) {
    private val logger = Logger.getLogger(KafkaProducerManager::class.java)
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
        logger.info("Kafka producer initialized with bootstrap servers: ${config.bootstrapServers}")
    }

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

    fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                producer.flush()
                producer.close()
                logger.info("Kafka producer closed successfully")
            } catch (e: Exception) {
                logger.error("Error closing Kafka producer", e)
            }
        }
    }
}
