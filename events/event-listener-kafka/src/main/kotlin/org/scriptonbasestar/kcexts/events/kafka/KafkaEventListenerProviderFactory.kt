package org.scriptonbasestar.kcexts.events.kafka

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.events.kafka.metrics.KafkaEventMetrics
// import org.scriptonbasestar.kcexts.events.kafka.metrics.PrometheusMetricsExporter
import java.util.concurrent.ConcurrentHashMap

class KafkaEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(KafkaEventListenerProviderFactory::class.java)
    private val producerManagers = ConcurrentHashMap<String, KafkaProducerManager>()

    // private var metricsExporter: PrometheusMetricsExporter? = null
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

        // Initialize basic metrics (Prometheus disabled temporarily)
        metrics = KafkaEventMetrics()
        logger.info("Basic metrics collection enabled (Prometheus exporter disabled)")
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

        // Cleanup basic metrics
        logger.info("Basic metrics cleanup completed")

        logger.info("KafkaEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "kafka-event-listener"
}
