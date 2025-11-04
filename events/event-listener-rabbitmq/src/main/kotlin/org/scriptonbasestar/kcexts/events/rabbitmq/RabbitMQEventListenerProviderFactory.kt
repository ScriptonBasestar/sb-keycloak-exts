package org.scriptonbasestar.kcexts.events.rabbitmq

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
import org.scriptonbasestar.kcexts.events.rabbitmq.metrics.RabbitMQEventMetrics
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class RabbitMQEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(RabbitMQEventListenerProviderFactory::class.java)
    private val connectionManagers = ConcurrentHashMap<String, RabbitMQConnectionManager>()

    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: RabbitMQEventMetrics
    private lateinit var defaultConfig: RabbitMQEventListenerConfig
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var deadLetterQueue: DeadLetterQueue
    private lateinit var batchProcessor: BatchProcessor<RabbitMQEventMessage>

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val connectionManager = getOrCreateConnectionManager(defaultConfig)
            RabbitMQEventListenerProvider(
                session,
                defaultConfig,
                connectionManager,
                metrics,
                circuitBreaker,
                retryPolicy,
                deadLetterQueue,
                batchProcessor,
            )
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

        // Initialize Circuit Breaker
        val enableCircuitBreaker = config.getBoolean("enableCircuitBreaker", true)
        val circuitBreakerFailureThreshold = config.getInt("circuitBreakerFailureThreshold", 5)
        val circuitBreakerOpenTimeout = config.getLong("circuitBreakerOpenTimeoutSeconds", 60)

        circuitBreaker =
            CircuitBreaker(
                name = "rabbitmq-event-sender",
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
        val dlqPath = config.get("dlqPath", "./dlq/rabbitmq")

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
                        connectionManagers.values.firstOrNull()?.publishMessage(message.routingKey, message.message)
                    }
                },
                onError = { batch, exception ->
                    logger.error("Batch processing failed for ${batch.size} messages", exception)
                    batch.forEach { message ->
                        deadLetterQueue.add(
                            eventType = message.eventType,
                            eventData = message.message,
                            realm = message.realm,
                            destination = message.exchange,
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
        logger.info("Post-initialization of RabbitMQEventListenerProviderFactory completed")
    }

    override fun close() {
        logger.info("Closing RabbitMQEventListenerProviderFactory")

        // Stop batch processor
        if (batchProcessor.isRunning()) {
            batchProcessor.stop()
            logger.info("Batch processor stopped")
        }

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
