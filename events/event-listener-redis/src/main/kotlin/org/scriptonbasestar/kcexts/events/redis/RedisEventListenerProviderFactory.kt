package org.scriptonbasestar.kcexts.events.redis

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
import org.scriptonbasestar.kcexts.events.redis.config.RedisEventListenerConfig
import org.scriptonbasestar.kcexts.events.redis.metrics.RedisEventMetrics
import org.scriptonbasestar.kcexts.events.redis.producer.RedisStreamProducer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis Streams Event Listener Provider Factory
 */
class RedisEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(RedisEventListenerProviderFactory::class.java)
    private val streamProducers = ConcurrentHashMap<String, RedisStreamProducer>()

    private var initConfigScope: Config.Scope? = null
    private var metricsExporter: PrometheusMetricsExporter? = null
    private lateinit var metrics: RedisEventMetrics
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var deadLetterQueue: DeadLetterQueue
    private lateinit var batchProcessor: BatchProcessor<RedisEventMessage>

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val config = RedisEventListenerConfig(session, initConfigScope)
            val streamProducer = getOrCreateStreamProducer(config)
            RedisEventListenerProvider(
                session,
                config,
                streamProducer,
                metrics,
                circuitBreaker,
                retryPolicy,
                deadLetterQueue,
                batchProcessor,
            )
        } catch (e: Exception) {
            logger.error("Failed to create RedisEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateStreamProducer(config: RedisEventListenerConfig): RedisStreamProducer {
        val key = config.redisUri
        return streamProducers.computeIfAbsent(key) {
            logger.info("Creating new RedisStreamProducer for URI: $key")
            RedisStreamProducer(config)
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing RedisEventListenerProviderFactory")
        initConfigScope = config

        val redisUri = config.get("redisUri")
        val userEventsStream = config.get("userEventsStream")
        val adminEventsStream = config.get("adminEventsStream")

        logger.info(
            "Configuration loaded - redisUri: $redisUri, userEventsStream: $userEventsStream, adminEventsStream: $adminEventsStream",
        )

        // Initialize Prometheus metrics exporter if enabled
        val enablePrometheus = config.getBoolean("enablePrometheus", false)
        val prometheusPort = config.getInt("prometheusPort", 9096)
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
        metrics = RedisEventMetrics(metricsExporter)
        logger.info("Redis metrics collection enabled")

        // Initialize Circuit Breaker
        val enableCircuitBreaker = config.getBoolean("enableCircuitBreaker", true)
        val circuitBreakerFailureThreshold = config.getInt("circuitBreakerFailureThreshold", 5)
        val circuitBreakerOpenTimeout = config.getLong("circuitBreakerOpenTimeoutSeconds", 60)

        circuitBreaker =
            CircuitBreaker(
                name = "redis-event-sender",
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
        val dlqPath = config.get("dlqPath", "./dlq/redis")

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
                        streamProducers.values.firstOrNull()?.sendEvent(message.streamKey, message.fields)
                    }
                },
                onError = { batch, exception ->
                    logger.error("Batch processing failed for ${batch.size} messages", exception)
                    batch.forEach { message ->
                        deadLetterQueue.add(
                            eventType = message.meta.eventType,
                            eventData = message.fields.toString(),
                            realm = message.meta.realm,
                            destination = message.streamKey,
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
        logger.info("Post-initialization of RedisEventListenerProviderFactory completed")
    }

    override fun close() {
        logger.info("Closing RedisEventListenerProviderFactory")

        // Stop batch processor
        if (batchProcessor.isRunning()) {
            batchProcessor.stop()
            logger.info("Batch processor stopped")
        }

        streamProducers.values.forEach { producer ->
            try {
                producer.close()
            } catch (e: Exception) {
                logger.error("Error closing RedisStreamProducer", e)
            }
        }
        streamProducers.clear()

        // Stop Prometheus exporter
        metricsExporter?.stop()
        logger.info("Metrics cleanup completed")

        logger.info("RedisEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "redis-event-listener"
}
