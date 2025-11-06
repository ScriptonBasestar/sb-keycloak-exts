package org.scriptonbasestar.kcexts.events.kafka

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
import org.scriptonbasestar.kcexts.events.kafka.metrics.KafkaEventMetrics
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class KafkaEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(KafkaEventListenerProviderFactory::class.java)
    private val connectionManagers = ConcurrentHashMap<String, KafkaConnectionManager>()

    private var initConfigScope: Config.Scope? = null
    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: KafkaEventMetrics
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var deadLetterQueue: DeadLetterQueue
    private lateinit var batchProcessor: BatchProcessor<KafkaEventMessage>

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val config = KafkaEventListenerConfig(session, initConfigScope)
            val connectionManager = getOrCreateConnectionManager(config)
            KafkaEventListenerProvider(
                session,
                config,
                connectionManager,
                metrics,
                circuitBreaker,
                retryPolicy,
                deadLetterQueue,
                batchProcessor,
            )
        } catch (e: Exception) {
            logger.error("Failed to create KafkaEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateConnectionManager(config: KafkaEventListenerConfig): KafkaConnectionManager {
        val key = "${config.bootstrapServers}:${config.clientId}"
        return connectionManagers.computeIfAbsent(key) {
            logger.info("Creating new KafkaConnectionManager for key: $key")
            KafkaConnectionManager(config)
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing KafkaEventListenerProviderFactory")
        initConfigScope = config

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

        // Initialize Circuit Breaker
        val enableCircuitBreaker = config.getBoolean("enableCircuitBreaker", true)
        val circuitBreakerFailureThreshold = config.getInt("circuitBreakerFailureThreshold", 5)
        val circuitBreakerOpenTimeout = config.getLong("circuitBreakerOpenTimeoutSeconds", 60)

        circuitBreaker =
            CircuitBreaker(
                name = "kafka-event-sender",
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
        val dlqPath = config.get("dlqPath", "./dlq/kafka")

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
                        connectionManagers.values.firstOrNull()?.sendEvent(message.topic, message.key, message.value)
                    }
                },
                onError = { batch, exception ->
                    logger.error("Batch processing failed for ${batch.size} messages", exception)
                    batch.forEach { message ->
                        deadLetterQueue.add(
                            eventType = message.meta.eventType,
                            eventData = message.value,
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
        logger.info("Post-initialization of KafkaEventListenerProviderFactory completed")
    }

    override fun close() {
        logger.info("Closing KafkaEventListenerProviderFactory")

        // Stop batch processor
        if (batchProcessor.isRunning()) {
            batchProcessor.stop()
            logger.info("Batch processor stopped")
        }

        connectionManagers.values.forEach { manager ->
            try {
                manager.close()
            } catch (e: Exception) {
                logger.error("Error closing KafkaConnectionManager", e)
            }
        }
        connectionManagers.clear()

        // Stop Prometheus exporter
        metricsExporter?.stop()
        logger.info("Metrics cleanup completed")

        logger.info("KafkaEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "kafka-event-listener"
}
