package org.scriptonbasestar.kcexts.events.kafka

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.kafka.metrics.KafkaEventMetrics
import java.util.concurrent.ConcurrentHashMap

class KafkaEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(KafkaEventListenerProviderFactory::class.java)
    private val producerManagers = ConcurrentHashMap<String, KafkaProducerManager>()

    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: KafkaEventMetrics

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val config = KafkaEventListenerConfig(session)
            val producerManager = getOrCreateProducerManager(config)
            KafkaEventListenerProvider(session, config, producerManager, metrics)
        } catch (e: Exception) {
            logger.error("Failed to create KafkaEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateProducerManager(config: KafkaEventListenerConfig): KafkaProducerManager {
        val key = "${config.bootstrapServers}:${config.clientId}"
        return producerManagers.computeIfAbsent(key) {
            logger.info("Creating new KafkaProducerManager for key: $key")
            KafkaProducerManager(config)
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing KafkaEventListenerProviderFactory")

        val bootstrapServers = config.get("bootstrapServers")
        val eventTopic = config.get("eventTopic")
        val adminEventTopic = config.get("adminEventTopic")
        val clientId = config.get("clientId")

        logger.info(
            "Configuration loaded - bootstrapServers: $bootstrapServers, eventTopic: $eventTopic, adminEventTopic: $adminEventTopic, clientId: $clientId",
        )

        // Initialize Prometheus metrics exporter if enabled
        val enablePrometheus = config.getBoolean("enablePrometheus", false)
        val prometheusPort = config.getInt("prometheusPort", 9090)
        val enableJvmMetrics = config.getBoolean("enableJvmMetrics", true)

        if (enablePrometheus) {
            try {
                metricsExporter = PrometheusMetricsExporter(prometheusPort, enableJvmMetrics)
                metricsExporter?.start()
                logger.info("Prometheus metrics exporter started on port $prometheusPort")
            } catch (e: Exception) {
                logger.error("Failed to start Prometheus metrics exporter", e)
                metricsExporter = null
            }
        } else {
            logger.info("Prometheus metrics exporter is disabled")
        }

        // Initialize metrics with optional Prometheus exporter
        metrics = KafkaEventMetrics(metricsExporter)
        logger.info("Kafka metrics collection enabled")
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        logger.info("Post-initialization of KafkaEventListenerProviderFactory completed")
    }

    override fun close() {
        logger.info("Closing KafkaEventListenerProviderFactory")

        producerManagers.values.forEach { manager ->
            try {
                manager.close()
            } catch (e: Exception) {
                logger.error("Error closing KafkaProducerManager", e)
            }
        }
        producerManagers.clear()

        // Stop Prometheus exporter
        metricsExporter?.stop()
        logger.info("Metrics cleanup completed")

        logger.info("KafkaEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "kafka-event-listener"
}
