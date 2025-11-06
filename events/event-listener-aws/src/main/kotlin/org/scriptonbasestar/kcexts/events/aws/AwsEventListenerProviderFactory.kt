package org.scriptonbasestar.kcexts.events.aws

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.events.aws.config.AwsEventListenerConfig
import org.scriptonbasestar.kcexts.events.aws.metrics.AwsEventMetrics
import org.scriptonbasestar.kcexts.events.common.batch.BatchProcessor
import org.scriptonbasestar.kcexts.events.common.dlq.DeadLetterQueue
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreaker
import org.scriptonbasestar.kcexts.events.common.resilience.RetryPolicy
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * AWS SQS/SNS Event Listener Provider Factory
 */
class AwsEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(AwsEventListenerProviderFactory::class.java)
    private val connectionManagers = ConcurrentHashMap<String, AwsConnectionManager>()

    private var initConfigScope: Config.Scope? = null
    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: AwsEventMetrics
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var deadLetterQueue: DeadLetterQueue
    private lateinit var batchProcessor: BatchProcessor<AwsEventMessage>

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val config = AwsEventListenerConfig(session, initConfigScope)
            val connectionManager = getOrCreateConnectionManager(config)
            AwsEventListenerProvider(
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
            logger.error("Failed to create AwsEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateConnectionManager(config: AwsEventListenerConfig): AwsConnectionManager {
        val key = "${config.awsRegion}:${config.useSqs}:${config.useSns}"
        return connectionManagers.computeIfAbsent(key) {
            logger.info("Creating new AwsConnectionManager for region: ${config.awsRegion}")
            AwsConnectionManager(config)
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing AwsEventListenerProviderFactory")
        initConfigScope = config

        // Initialize Prometheus metrics exporter
        val enablePrometheus = config.getBoolean("enablePrometheus", false)
        val prometheusPort = config.getInt("prometheusPort", 9093)
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
        }

        metrics = AwsEventMetrics(metricsExporter)

        // Initialize Circuit Breaker
        circuitBreaker =
            CircuitBreaker(
                name = "aws-event-sender",
                failureThreshold = config.getInt("circuitBreakerFailureThreshold", 5),
                successThreshold = 2,
                openTimeout = Duration.ofSeconds(config.getLong("circuitBreakerOpenTimeoutSeconds", 60)),
            )

        // Initialize Retry Policy
        retryPolicy =
            RetryPolicy(
                maxAttempts = config.getInt("maxRetryAttempts", 3),
                initialDelay = Duration.ofMillis(config.getLong("retryInitialDelayMs", 100)),
                maxDelay = Duration.ofMillis(config.getLong("retryMaxDelayMs", 10000)),
                backoffStrategy = RetryPolicy.BackoffStrategy.EXPONENTIAL,
                multiplier = 2.0,
            )

        // Initialize Dead Letter Queue
        deadLetterQueue =
            DeadLetterQueue(
                maxSize = config.getInt("dlqMaxSize", 10000),
                persistToFile = config.getBoolean("dlqPersistToFile", false),
                persistencePath = config.get("dlqPath", "./dlq/aws"),
            )

        // Initialize Batch Processor
        val enableBatching = config.getBoolean("enableBatching", false)
        batchProcessor =
            BatchProcessor(
                batchSize = config.getInt("batchSize", 100),
                flushInterval = Duration.ofMillis(config.getLong("batchFlushIntervalMs", 5000)),
                processBatch = { batch ->
                    batch.forEach { message ->
                        connectionManagers.values.firstOrNull()?.let { connectionManager ->
                            if (message.queueUrl != null) {
                                connectionManager.sendToSqs(message.queueUrl, message.messageBody, message.messageAttributes)
                            } else if (message.topicArn != null) {
                                connectionManager.sendToSns(message.topicArn, message.messageBody, message.messageAttributes)
                            }
                        }
                    }
                },
                onError = { batch, exception ->
                    logger.error("Batch processing failed for ${batch.size} messages", exception)
                },
            )

        if (enableBatching) {
            batchProcessor.start()
            logger.info("Batch Processor started")
        }

        logger.info("AwsEventListenerProviderFactory initialized successfully")
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        logger.info("Post-initialization completed")
    }

    override fun close() {
        logger.info("Closing AwsEventListenerProviderFactory")

        if (batchProcessor.isRunning()) {
            batchProcessor.stop()
        }

        connectionManagers.values.forEach { manager ->
            try {
                manager.close()
            } catch (e: Exception) {
                logger.error("Error closing AwsConnectionManager", e)
            }
        }
        connectionManagers.clear()

        metricsExporter?.stop()
        logger.info("AwsEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "aws-event-listener"
}
