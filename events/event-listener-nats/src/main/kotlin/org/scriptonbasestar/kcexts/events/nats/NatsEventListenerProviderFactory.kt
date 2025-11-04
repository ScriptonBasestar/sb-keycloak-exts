package org.scriptonbasestar.kcexts.events.nats

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
import org.scriptonbasestar.kcexts.events.nats.metrics.NatsEventMetrics
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class NatsEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(NatsEventListenerProviderFactory::class.java)
    private val connectionManagers = ConcurrentHashMap<String, NatsConnectionManager>()

    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: NatsEventMetrics
    private lateinit var defaultConfig: NatsEventListenerConfig
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var deadLetterQueue: DeadLetterQueue
    private lateinit var batchProcessor: BatchProcessor<NatsEventMessage>

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val connectionManager = getOrCreateConnectionManager(defaultConfig)
            NatsEventListenerProvider(
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
            logger.error("Failed to create NatsEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateConnectionManager(config: NatsEventListenerConfig): NatsConnectionManager {
        val key = config.serverUrl
        return connectionManagers.computeIfAbsent(key) {
            logger.info("Creating new NatsConnectionManager for server: $key")
            NatsConnectionManager(config)
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing NatsEventListenerProviderFactory")

        // Load configuration
        val configMap =
            mapOf(
                "serverUrl" to config.get("serverUrl"),
                "username" to config.get("username"),
                "password" to config.get("password"),
                "token" to config.get("token"),
                "useTls" to config.get("useTls"),
                "userEventSubject" to config.get("userEventSubject"),
                "adminEventSubject" to config.get("adminEventSubject"),
                "enableUserEvents" to config.get("enableUserEvents"),
                "enableAdminEvents" to config.get("enableAdminEvents"),
                "includedEventTypes" to config.get("includedEventTypes"),
                "connectionTimeout" to config.get("connectionTimeout"),
                "maxReconnects" to config.get("maxReconnects"),
                "reconnectWait" to config.get("reconnectWait"),
                "noEcho" to config.get("noEcho"),
                "maxPingsOut" to config.get("maxPingsOut"),
            )

        defaultConfig = NatsEventListenerConfig.fromConfig(configMap)

        logger.info(
            "Configuration loaded - server: ${defaultConfig.serverUrl}, " +
                "userSubject: ${defaultConfig.userEventSubject}, " +
                "adminSubject: ${defaultConfig.adminEventSubject}",
        )

        // Initialize Prometheus metrics exporter if enabled
        val enablePrometheus = config.getBoolean("enablePrometheus", false)
        val prometheusPort = config.getInt("prometheusPort", 9092)
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
        metrics = NatsEventMetrics(metricsExporter)
        logger.info("NATS metrics collection enabled")

        // Initialize Circuit Breaker
        val enableCircuitBreaker = config.getBoolean("enableCircuitBreaker", true)
        val circuitBreakerFailureThreshold = config.getInt("circuitBreakerFailureThreshold", 5)
        val circuitBreakerOpenTimeout = config.getLong("circuitBreakerOpenTimeoutSeconds", 60)

        circuitBreaker =
            CircuitBreaker(
                name = "nats-event-sender",
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
        val dlqPath = config.get("dlqPath", "./dlq/nats")

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
                        connectionManagers.values.firstOrNull()?.publish(message.subject, message.message)
                    }
                },
                onError = { batch, exception ->
                    logger.error("Batch processing failed for ${batch.size} messages", exception)
                    batch.forEach { message ->
                        deadLetterQueue.add(
                            eventType = message.eventType,
                            eventData = message.message,
                            realm = message.realm,
                            destination = message.subject,
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
        logger.info("Post-initialization of NatsEventListenerProviderFactory completed")
    }

    override fun close() {
        logger.info("Closing NatsEventListenerProviderFactory")

        // Stop batch processor
        if (batchProcessor.isRunning()) {
            batchProcessor.stop()
            logger.info("Batch processor stopped")
        }

        connectionManagers.values.forEach { manager ->
            try {
                manager.close()
            } catch (e: Exception) {
                logger.error("Error closing NatsConnectionManager", e)
            }
        }
        connectionManagers.clear()

        // Stop Prometheus exporter
        metricsExporter?.stop()
        logger.info("Metrics cleanup completed")

        logger.info("NatsEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "nats-event-listener"
}
