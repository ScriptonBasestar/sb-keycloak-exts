package org.scriptonbasestar.kcexts.events.rabbitmq

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.rabbitmq.metrics.RabbitMQEventMetrics
import java.util.concurrent.ConcurrentHashMap

class RabbitMQEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(RabbitMQEventListenerProviderFactory::class.java)
    private val connectionManagers = ConcurrentHashMap<String, RabbitMQConnectionManager>()

    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: RabbitMQEventMetrics
    private lateinit var defaultConfig: RabbitMQEventListenerConfig

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val connectionManager = getOrCreateConnectionManager(defaultConfig)
            RabbitMQEventListenerProvider(session, defaultConfig, connectionManager, metrics)
        } catch (e: Exception) {
            logger.error("Failed to create RabbitMQEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateConnectionManager(config: RabbitMQEventListenerConfig): RabbitMQConnectionManager {
        val key = "${config.host}:${config.port}:${config.virtualHost}"
        return connectionManagers.computeIfAbsent(key) {
            logger.info("Creating new RabbitMQConnectionManager for key: $key")
            RabbitMQConnectionManager(config)
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing RabbitMQEventListenerProviderFactory")

        // Load configuration
        val configMap =
            mapOf(
                "host" to config.get("host"),
                "port" to config.get("port"),
                "username" to config.get("username"),
                "password" to config.get("password"),
                "virtualHost" to config.get("virtualHost"),
                "useSsl" to config.get("useSsl"),
                "exchangeName" to config.get("exchangeName"),
                "exchangeType" to config.get("exchangeType"),
                "exchangeDurable" to config.get("exchangeDurable"),
                "queueDurable" to config.get("queueDurable"),
                "queueAutoDelete" to config.get("queueAutoDelete"),
                "userEventRoutingKey" to config.get("userEventRoutingKey"),
                "adminEventRoutingKey" to config.get("adminEventRoutingKey"),
                "enableUserEvents" to config.get("enableUserEvents"),
                "enableAdminEvents" to config.get("enableAdminEvents"),
                "includedEventTypes" to config.get("includedEventTypes"),
                "connectionTimeout" to config.get("connectionTimeout"),
                "requestedHeartbeat" to config.get("requestedHeartbeat"),
                "networkRecoveryInterval" to config.get("networkRecoveryInterval"),
                "automaticRecoveryEnabled" to config.get("automaticRecoveryEnabled"),
                "publisherConfirms" to config.get("publisherConfirms"),
                "publisherConfirmTimeout" to config.get("publisherConfirmTimeout"),
            )

        defaultConfig = RabbitMQEventListenerConfig.fromConfig(configMap)

        logger.info(
            "Configuration loaded - host: ${defaultConfig.host}, " +
                "port: ${defaultConfig.port}, " +
                "exchange: ${defaultConfig.exchangeName}, " +
                "virtualHost: ${defaultConfig.virtualHost}",
        )

        // Initialize Prometheus metrics exporter if enabled
        val enablePrometheus = config.getBoolean("enablePrometheus", false)
        val prometheusPort = config.getInt("prometheusPort", 9091)
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
        metrics = RabbitMQEventMetrics(metricsExporter)
        logger.info("RabbitMQ metrics collection enabled")
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        logger.info("Post-initialization of RabbitMQEventListenerProviderFactory completed")
    }

    override fun close() {
        logger.info("Closing RabbitMQEventListenerProviderFactory")

        connectionManagers.values.forEach { manager ->
            try {
                manager.close()
            } catch (e: Exception) {
                logger.error("Error closing RabbitMQConnectionManager", e)
            }
        }
        connectionManagers.clear()

        // Stop Prometheus exporter
        metricsExporter?.stop()
        logger.info("Metrics cleanup completed")

        logger.info("RabbitMQEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "rabbitmq-event-listener"
}
