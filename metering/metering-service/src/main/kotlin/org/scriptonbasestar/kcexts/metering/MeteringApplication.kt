package org.scriptonbasestar.kcexts.metering

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.scriptonbasestar.kcexts.metering.config.MeteringConfig
import org.scriptonbasestar.kcexts.metering.consumer.KafkaEventConsumer
import org.scriptonbasestar.kcexts.metering.metrics.MetricsExporter
import org.scriptonbasestar.kcexts.metering.processor.EventProcessor
import org.scriptonbasestar.kcexts.metering.storage.InfluxDBStorage
import org.scriptonbasestar.kcexts.metering.storage.StorageBackend
import kotlin.system.exitProcess

/**
 * Keycloak Metering Service
 *
 * Standalone service that consumes Keycloak events from Kafka and stores usage metrics
 * in a time-series database for analytics and visualization.
 *
 * Architecture:
 * 1. KafkaEventConsumer - Reads events from Kafka topics
 * 2. EventProcessor - Aggregates and transforms events into metrics
 * 3. StorageBackend - Persists metrics to InfluxDB/TimescaleDB
 * 4. MetricsExporter - Exports Prometheus metrics for monitoring
 */
fun main() {
    val logger = LoggerFactory.getLogger("MeteringApplication")

    logger.info("Starting Keycloak Metering Service...")

    try {
        // Load configuration
        val config = ConfigFactory.load()
        val meteringConfig = MeteringConfig.fromConfig(config)

        // Initialize storage backend
        val storage: StorageBackend = when (meteringConfig.storageType) {
            "influxdb" -> InfluxDBStorage(meteringConfig.influxDB)
            "timescaledb" -> {
                // TODO: Implement TimescaleDB storage
                throw UnsupportedOperationException("TimescaleDB support coming in Phase 2")
            }
            else -> throw IllegalArgumentException("Unknown storage type: ${meteringConfig.storageType}")
        }

        // Initialize metrics exporter
        val metricsExporter = MetricsExporter(meteringConfig.prometheusPort)
        metricsExporter.start()

        // Initialize event processor
        val eventProcessor = EventProcessor(storage, metricsExporter)

        // Initialize Kafka consumer
        val kafkaConsumer = KafkaEventConsumer(
            config = meteringConfig.kafka,
            processor = eventProcessor,
        )

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.info("Shutting down Metering Service...")
                kafkaConsumer.stop()
                storage.close()
                metricsExporter.stop()
                logger.info("Metering Service stopped")
            },
        )

        // Start consuming events
        kafkaConsumer.start()

        logger.info("Keycloak Metering Service started successfully")
        logger.info("Prometheus metrics available at http://localhost:${meteringConfig.prometheusPort}/metrics")

        // Keep the application running
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start Metering Service", e)
        exitProcess(1)
    }
}
