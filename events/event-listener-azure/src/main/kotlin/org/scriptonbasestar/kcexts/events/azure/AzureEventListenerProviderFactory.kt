package org.scriptonbasestar.kcexts.events.azure

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.events.azure.config.AzureEventListenerConfig
import org.scriptonbasestar.kcexts.events.azure.metrics.AzureEventMetrics
import org.scriptonbasestar.kcexts.events.azure.sender.AzureServiceBusSender
import org.scriptonbasestar.kcexts.events.common.batch.BatchProcessor
import org.scriptonbasestar.kcexts.events.common.dlq.DeadLetterQueue
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreaker
import org.scriptonbasestar.kcexts.events.common.resilience.RetryPolicy
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Azure Service Bus Event Listener Provider Factory
 */
class AzureEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(AzureEventListenerProviderFactory::class.java)
    private val serviceBusSenders = ConcurrentHashMap<String, AzureServiceBusSender>()

    private var initConfigScope: Config.Scope? = null
    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: AzureEventMetrics
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var deadLetterQueue: DeadLetterQueue
    private lateinit var batchProcessor: BatchProcessor<AzureEventMessage>

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val config = AzureEventListenerConfig(session, initConfigScope)
            val senderKey = buildSenderKey(config)
            val sender = getOrCreateSender(senderKey, config)
            AzureEventListenerProvider(
                session = session,
                config = config,
                sender = sender,
                senderKey = senderKey,
                metrics = metrics,
                circuitBreaker = circuitBreaker,
                retryPolicy = retryPolicy,
                deadLetterQueue = deadLetterQueue,
                batchProcessor = batchProcessor,
            )
        } catch (e: Exception) {
            logger.error("Failed to create AzureEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateSender(
        senderKey: String,
        config: AzureEventListenerConfig,
    ): AzureServiceBusSender {
        return serviceBusSenders.computeIfAbsent(senderKey) {
            logger.info("Creating new AzureServiceBusSender for key: $senderKey")
            AzureServiceBusSender(config)
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing AzureEventListenerProviderFactory")
        initConfigScope = config

        // Initialize Prometheus metrics exporter
        val enablePrometheus = config.getBoolean("enablePrometheus", false)
        val prometheusPort = config.getInt("prometheusPort", 9094)
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

        metrics = AzureEventMetrics(metricsExporter)

        // Initialize Circuit Breaker
        circuitBreaker =
            CircuitBreaker(
                name = "azure-event-sender",
                failureThreshold = config.getInt("circuitBreakerFailureThreshold", 5),
                successThreshold = 2,
                openTimeout = Duration.ofSeconds(config.getLong("circuitBreakerOpenTimeoutSeconds", 60)),
            )

        // Initialize Retry Policy
        retryPolicy =
            RetryPolicy(
                maxAttempts = config.getInt("maxRetryAttempts", 3),
                initialDelay = Duration.ofMillis(config.getLong("retryInitialDelayMs", 100)),
                maxDelay = Duration.ofMillis(config.getLong("retryMaxDelayMs", 10_000)),
                backoffStrategy = RetryPolicy.BackoffStrategy.EXPONENTIAL,
                multiplier = 2.0,
            )

        // Initialize Dead Letter Queue
        deadLetterQueue =
            DeadLetterQueue(
                maxSize = config.getInt("dlqMaxSize", 10_000),
                persistToFile = config.getBoolean("dlqPersistToFile", false),
                persistencePath = config.get("dlqPath", "./dlq/azure"),
            )

        // Initialize Batch Processor
        val enableBatching = config.getBoolean("enableBatching", false)
        val batchSize = config.getInt("batchSize", 100)
        val batchFlushInterval = config.getLong("batchFlushIntervalMs", 5_000)

        batchProcessor =
            BatchProcessor(
                batchSize = batchSize,
                flushInterval = Duration.ofMillis(batchFlushInterval),
                processBatch = { batch ->
                    batch
                        .groupBy { it.senderKey }
                        .forEach { (senderKey, messages) ->
                            val sender = serviceBusSenders[senderKey]
                            if (sender == null) {
                                logger.error("No AzureServiceBusSender found for key: $senderKey")
                                metrics.updateConnectionStatus(false)
                                messages.forEach { message ->
                                    addToDeadLetterQueue(message, IllegalStateException("Missing sender for batch"))
                                }
                                return@forEach
                            }

                            messages.forEach { message ->
                                try {
                                    when {
                                        message.queueName != null ->
                                            sender.sendToQueue(
                                                message.queueName,
                                                message.messageBody,
                                                message.properties,
                                            )

                                        message.topicName != null ->
                                            sender.sendToTopic(
                                                message.topicName,
                                                message.messageBody,
                                                message.properties,
                                            )

                                        else -> logger.warn("Batch message missing destination: ${message.meta.eventType}")
                                    }
                                } catch (e: Exception) {
                                    logger.error("Failed to send batched message: ${message.meta.eventType}", e)
                                    addToDeadLetterQueue(message, e)
                                } finally {
                                    metrics.updateConnectionStatus(sender.isHealthy())
                                }
                            }
                        }
                },
                onError = { batch, exception ->
                    logger.error("Batch processing failed for ${batch.size} messages", exception)
                    batch.forEach { message ->
                        addToDeadLetterQueue(message, exception)
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

        logger.info("AzureEventListenerProviderFactory initialized successfully")
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        logger.info("AzureEventListenerProviderFactory post-initialization complete")
    }

    override fun close() {
        logger.info("Closing AzureEventListenerProviderFactory")

        if (batchProcessor.isRunning()) {
            batchProcessor.stop()
        }

        serviceBusSenders.values.forEach { sender ->
            try {
                sender.close()
            } catch (e: Exception) {
                logger.error("Error closing AzureServiceBusSender", e)
            }
        }
        serviceBusSenders.clear()

        metricsExporter?.stop()

        logger.info("AzureEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "azure-event-listener"

    private fun buildSenderKey(config: AzureEventListenerConfig): String =
        if (config.useManagedIdentity) {
            "managed:${config.fullyQualifiedNamespace}:${config.managedIdentityClientId ?: ""}"
        } else {
            "connection:${config.connectionString}"
        }

    private fun addToDeadLetterQueue(
        message: AzureEventMessage,
        exception: Exception,
    ) {
        val destination = message.queueName ?: message.topicName ?: "unknown"
        deadLetterQueue.add(
            eventType = message.meta.eventType,
            eventData = message.messageBody,
            realm = message.meta.realm,
            destination = destination,
            failureReason = exception.message ?: exception.javaClass.simpleName,
            attemptCount = retryPolicy.getConfig().maxAttempts,
            metadata =
                buildMap {
                    put("isAdminEvent", message.isAdminEvent.toString())
                    put("senderKey", message.senderKey)
                    message.properties.forEach { (key, value) ->
                        put(key, value)
                    }
                },
        )

        metrics.recordEventFailed(
            eventType = message.meta.eventType,
            realm = message.meta.realm,
            destination =
                if (message.queueName != null) {
                    "queue:${message.queueName}"
                } else {
                    "topic:${message.topicName ?: "unknown"}"
                },
            errorType = exception.javaClass.simpleName,
        )
    }
}
