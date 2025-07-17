package org.scriptonbasestar.kcexts.events.kafka

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.events.kafka.metrics.KafkaEventMetrics
import org.scriptonbasestar.kcexts.events.kafka.metrics.PrometheusMetricsExporter
import java.util.concurrent.ConcurrentHashMap

class KafkaEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(KafkaEventListenerProviderFactory::class.java)
    private val producerManagers = ConcurrentHashMap<String, KafkaProducerManager>()
    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: KafkaEventMetrics

    override fun create(session: KeycloakSession): EventListenerProvider {
        return try {
            val config = KafkaEventListenerConfig(session)
            val producerManager = getOrCreateProducerManager(config)
            KafkaEventListenerProvider(session, config, producerManager, metrics)
        } catch (e: Exception) {
            logger.error("Failed to create KafkaEventListenerProvider", e)
            throw e
        }
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

        // Initialize metrics if enabled
        val metricsEnabled = config.getBoolean("metrics.enabled", true)
        if (metricsEnabled) {
            val metricsPort = config.getInt("metrics.port", 9090)
            val metricsHost = config.get("metrics.host", "0.0.0.0")

            metricsExporter = PrometheusMetricsExporter(metricsPort, metricsHost)
            val prometheusRegistry = metricsExporter!!.start()
            metrics = metricsExporter!!.getKafkaMetrics()

            logger.info("Prometheus metrics exporter started on $metricsHost:$metricsPort")
        } else {
            // Create metrics instance without exporter
            metrics = KafkaEventMetrics()
            logger.info("Metrics collection disabled")
        }
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

        // Stop metrics exporter if running
        metricsExporter?.let {
            try {
                it.stop()
                logger.info("Metrics exporter stopped")
            } catch (e: Exception) {
                logger.error("Error stopping metrics exporter", e)
            }
        }

        logger.info("KafkaEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "kafka-event-listener"
}
