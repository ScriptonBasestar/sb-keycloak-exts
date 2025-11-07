package org.scriptonbasestar.kcexts.events.mqtt

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.events.common.batch.BatchProcessor
import org.scriptonbasestar.kcexts.events.common.dlq.DeadLetterQueue
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreaker
import org.scriptonbasestar.kcexts.events.common.resilience.RetryPolicy
import org.scriptonbasestar.kcexts.events.mqtt.metrics.MqttEventMetrics
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating MqttEventListenerProvider instances.
 *
 * Manages connection lifecycle and shared resources.
 */
class MqttEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(MqttEventListenerProviderFactory::class.java)
    private val connectionManagers = ConcurrentHashMap<String, MqttConnectionManager>()

    private var initConfigScope: Config.Scope? = null
    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: MqttEventMetrics
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var deadLetterQueue: DeadLetterQueue
    private lateinit var batchProcessor: BatchProcessor<MqttEventMessage>

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val config = MqttEventListenerConfig.fromRuntime(session, initConfigScope)
            val connectionManager = getOrCreateConnectionManager(config)

            MqttEventListenerProvider(
                session = session,
                config = config,
                connectionManager = connectionManager,
                metrics = metrics,
                circuitBreaker = circuitBreaker,
                retryPolicy = retryPolicy,
                deadLetterQueue = deadLetterQueue,
                batchProcessor = batchProcessor,
            )
        } catch (e: Exception) {
            logger.error("Failed to create MqttEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateConnectionManager(config: MqttEventListenerConfig): MqttConnectionManager {
        val key = "${config.brokerUrl}:${config.clientId}"
        return connectionManagers.computeIfAbsent(key) {
            logger.info("Creating new MqttConnectionManager for key: $key")
            try {
                val manager = MqttConnectionManager(config)
                metrics.setConnectionStatus(manager.isConnected())
                manager
            } catch (e: Exception) {
                logger.error("Failed to create MQTT connection manager", e)
                metrics.setConnectionStatus(false)
                throw e
            }
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing MqttEventListenerProviderFactory")
        initConfigScope = config

        // Log configuration
        val brokerUrl = config.get("broker.url") ?: "tcp://localhost:1883"
        val clientId = config.get("client.id") ?: "keycloak-mqtt"
        val qos = config.getInt("qos", 1)

        logger.info(
            "MQTT Configuration loaded - brokerUrl: $brokerUrl, clientId: $clientId, qos: $qos",
        )

        // Initialize Prometheus metrics exporter if enabled
        val enablePrometheus = config.getBoolean("enable.prometheus", false)
        val prometheusPort = config.getInt("prometheus.port", 9090)
        val enableJvmMetrics = config.getBoolean("enable.jvm.metrics", true)

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

        // Initialize metrics
        metrics = MqttEventMetrics(metricsExporter)
        logger.info("MQTT metrics collection enabled")

        // Initialize Circuit Breaker
        val enableCircuitBreaker = config.getBoolean("enableCircuitBreaker", true)
        val circuitBreakerFailureThreshold = config.getInt("circuitBreakerFailureThreshold", 5)
        val circuitBreakerOpenTimeout = config.getLong("circuitBreakerOpenTimeoutSeconds", 60)

        circuitBreaker =
            CircuitBreaker(
                name = "mqtt-event-sender",
                failureThreshold = circuitBreakerFailureThreshold,
                successThreshold = 2,
                openTimeout = Duration.ofSeconds(circuitBreakerOpenTimeout),
            )
        logger.info(
            "Circuit Breaker initialized: enabled=$enableCircuitBreaker, " +
                "failureThreshold=$circuitBreakerFailureThreshold, openTimeout=${circuitBreakerOpenTimeout}s",
        )

        // Initialize Retry Policy
        val enableRetry = config.getBoolean("enableRetry", true)
        val maxRetryAttempts = config.getInt("maxRetryAttempts", 3)
        val retryInitialDelay = config.getLong("retryInitialDelayMs", 100)
        val retryMaxDelay = config.getLong("retryMaxDelayMs", 10000)

        retryPolicy =
            RetryPolicy(
                maxAttempts = maxRetryAttempts,
                initialDelay = Duration.ofMillis(retryInitialDelay),
                maxDelay = Duration.ofMillis(retryMaxDelay),
                backoffStrategy = RetryPolicy.BackoffStrategy.EXPONENTIAL,
                multiplier = 2.0,
            )
        logger.info(
            "Retry Policy initialized: enabled=$enableRetry, maxAttempts=$maxRetryAttempts, " +
                "initialDelay=${retryInitialDelay}ms, maxDelay=${retryMaxDelay}ms",
        )

        // Initialize Dead Letter Queue
        val enableDLQ = config.getBoolean("enableDeadLetterQueue", true)
        val dlqMaxSize = config.getInt("dlqMaxSize", 10000)
        val dlqPersistToFile = config.getBoolean("dlqPersistToFile", false)
        val dlqPath = config.get("dlqPath", "./dlq/mqtt")

        deadLetterQueue =
            DeadLetterQueue(
                maxSize = dlqMaxSize,
                persistToFile = dlqPersistToFile,
                persistencePath = dlqPath,
            )
        logger.info(
            "Dead Letter Queue initialized: enabled=$enableDLQ, maxSize=$dlqMaxSize, " +
                "persistToFile=$dlqPersistToFile, path=$dlqPath",
        )

        // Initialize Batch Processor
        val enableBatching = config.getBoolean("enableBatching", false)
        val batchSize = config.getInt("batchSize", 100)
        val batchFlushInterval = config.getLong("batchFlushIntervalMs", 5000)

        batchProcessor =
            BatchProcessor(
                batchSize = batchSize,
                flushInterval = Duration.ofMillis(batchFlushInterval),
                processBatch = { batch ->
                    batch.forEach { message ->
                        connectionManagers.values.firstOrNull()?.publish(
                            message.topic,
                            message.message,
                            message.qos,
                            message.retained,
                        )
                    }
                },
                onError = { batch, exception ->
                    logger.error("Batch processing failed for ${batch.size} messages", exception)
                    batch.forEach { message ->
                        deadLetterQueue.add(
                            eventType = message.meta.eventType,
                            eventData = message.message,
                            realm = message.meta.realm,
                            destination = message.topic,
                            failureReason = exception.message ?: "Unknown error",
                            attemptCount = maxRetryAttempts,
                        )
                    }
                },
            )

        if (enableBatching) {
            batchProcessor.start()
            logger.info(
                "Batch Processor started: batchSize=$batchSize, flushInterval=${batchFlushInterval}ms",
            )
        } else {
            logger.info("Batch processing is disabled")
        }
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        logger.info("Post-initialization of MqttEventListenerProviderFactory")

        // Log metrics summary
        val summary = metrics.getMetricsSummary()
        logger.info(
            "Metrics initialized - totalSent: ${summary.totalSent}, " +
                "totalFailed: ${summary.totalFailed}, avgLatencyMs: ${summary.avgLatencyMs}",
        )
    }

    override fun close() {
        logger.info("Closing MqttEventListenerProviderFactory")

        // Close all connection managers
        connectionManagers.values.forEach { manager ->
            try {
                manager.close()
            } catch (e: Exception) {
                logger.error("Error closing MQTT connection manager", e)
            }
        }
        connectionManagers.clear()

        // Stop Prometheus metrics exporter
        metricsExporter?.let {
            try {
                it.stop()
                logger.info("Prometheus metrics exporter stopped")
            } catch (e: Exception) {
                logger.error("Error stopping Prometheus metrics exporter", e)
            }
        }

        // Log final metrics
        val finalSummary = metrics.getMqttMetricsSummary()
        logger.info("Final metrics summary: $finalSummary")

        logger.info("MqttEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "mqtt"
}
