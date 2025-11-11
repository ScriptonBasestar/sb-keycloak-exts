package org.scriptonbasestar.kcexts.metering.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.scriptonbasestar.kcexts.events.common.model.KeycloakEvent
import org.scriptonbasestar.kcexts.metering.config.KafkaConfig
import org.scriptonbasestar.kcexts.metering.processor.EventProcessor
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kafka consumer for Keycloak events
 *
 * Consumes events from Kafka topics and passes them to EventProcessor
 */
class KafkaEventConsumer(
    private val config: KafkaConfig,
    private val processor: EventProcessor,
) {
    private val logger = LoggerFactory.getLogger(KafkaEventConsumer::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val running = AtomicBoolean(false)
    private lateinit var consumer: KafkaConsumer<String, String>
    private lateinit var consumerThread: Thread

    fun start() {
        if (running.compareAndSet(false, true)) {
            consumer = createKafkaConsumer()
            consumer.subscribe(listOf(config.eventTopic, config.adminEventTopic))

            consumerThread =
                Thread({
                    logger.info("Kafka consumer started, listening to topics: ${config.eventTopic}, ${config.adminEventTopic}")
                    consumeEvents()
                }, "kafka-consumer-thread")

            consumerThread.start()
        }
    }

    fun stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping Kafka consumer...")
            consumer.wakeup()
            consumerThread.join(5000)
            consumer.close()
            logger.info("Kafka consumer stopped")
        }
    }

    private fun consumeEvents() {
        try {
            while (running.get()) {
                val records = consumer.poll(Duration.ofMillis(1000))

                if (records.isEmpty) {
                    continue
                }

                logger.debug("Polled ${records.count()} records from Kafka")

                for (record in records) {
                    try {
                        // Determine event type from topic
                        val isAdminEvent = record.topic() == config.adminEventTopic

                        if (isAdminEvent) {
                            // Admin event processing
                            processAdminEvent(record.value())
                        } else {
                            // User event processing
                            val event = objectMapper.readValue(record.value(), KeycloakEvent::class.java)
                            processor.processUserEvent(event)
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to process event from topic ${record.topic()}, offset ${record.offset()}", e)
                        // Continue processing other events
                    }
                }

                // Manual commit after processing batch
                consumer.commitSync()
            }
        } catch (e: Exception) {
            if (running.get()) {
                logger.error("Error in Kafka consumer loop", e)
            }
        }
    }

    private fun processAdminEvent(json: String) {
        // TODO: Parse and process admin events
        // For MVP, we'll focus on user events first
        logger.trace("Admin event received (not processed in MVP): ${json.take(100)}...")
    }

    private fun createKafkaConsumer(): KafkaConsumer<String, String> {
        val props =
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
                put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId)
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.autoOffsetReset)
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, config.enableAutoCommit.toString())
                put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.maxPollRecords.toString())

                // Performance tuning
                put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1024")
                put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "500")
            }

        return KafkaConsumer(props)
    }
}
