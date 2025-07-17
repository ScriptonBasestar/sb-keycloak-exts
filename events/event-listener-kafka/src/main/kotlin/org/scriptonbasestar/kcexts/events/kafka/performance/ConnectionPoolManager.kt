package org.scriptonbasestar.kcexts.events.kafka.performance

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Connection Pool Manager for Kafka Producers
 * Manages multiple Kafka producer instances for better performance and reliability
 */
class ConnectionPoolManager(
    private val config: ConnectionPoolConfig,
) {
    companion object {
        private val logger = Logger.getLogger(ConnectionPoolManager::class.java)
        private const val DEFAULT_POOL_SIZE = 3
        private const val DEFAULT_MAX_POOL_SIZE = 10
        private const val DEFAULT_IDLE_TIMEOUT_MS = 300000L // 5 minutes
    }

    private val producers = ConcurrentLinkedQueue<PooledProducer>()
    private val activeConnections = AtomicInteger(0)
    private val totalConnections = AtomicInteger(0)
    private val connectionRequestCount = AtomicLong(0)
    private val connectionErrorCount = AtomicLong(0)

    private val executorService: ExecutorService =
        ThreadPoolExecutor(
            config.coreThreads,
            config.maxThreads,
            config.keepAliveTime,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(config.queueCapacity),
            ThreadFactory { r ->
                val thread = Thread(r, "kafka-event-pool-${threadCounter.incrementAndGet()}")
                thread.isDaemon = true
                thread.uncaughtExceptionHandler =
                    Thread.UncaughtExceptionHandler { t, e ->
                        logger.error("Uncaught exception in thread ${t.name}", e)
                    }
                thread
            },
            ThreadPoolExecutor.CallerRunsPolicy(), // Handle rejected tasks
        )

    private val maintenanceExecutor =
        Executors.newSingleThreadScheduledExecutor { r ->
            val thread = Thread(r, "kafka-pool-maintenance")
            thread.isDaemon = true
            thread
        }

    @Volatile
    private var closed = false

    init {
        initializePool()
        startMaintenanceTask()
    }

    /**
     * Initialize the connection pool with minimum connections
     */
    private fun initializePool() {
        logger.info("Initializing Kafka connection pool with ${config.initialSize} connections")

        repeat(config.initialSize) {
            try {
                val producer = createNewProducer()
                producers.offer(PooledProducer(producer, System.currentTimeMillis()))
                totalConnections.incrementAndGet()
                logger.debug("Created initial producer connection ${it + 1}/${config.initialSize}")
            } catch (e: Exception) {
                logger.error("Failed to create initial producer connection", e)
                connectionErrorCount.incrementAndGet()
            }
        }

        logger.info("Connection pool initialized with ${producers.size} connections")
    }

    /**
     * Get a producer from the pool
     */
    fun getProducer(): KafkaProducer<String, String> {
        if (closed) {
            throw IllegalStateException("Connection pool is closed")
        }

        connectionRequestCount.incrementAndGet()

        // Try to get an existing producer
        var pooledProducer = producers.poll()

        // If no producer available and we can create more
        if (pooledProducer == null && totalConnections.get() < config.maxPoolSize) {
            try {
                val producer = createNewProducer()
                totalConnections.incrementAndGet()
                pooledProducer = PooledProducer(producer, System.currentTimeMillis())
                logger.debug("Created new producer, total connections: ${totalConnections.get()}")
            } catch (e: Exception) {
                logger.error("Failed to create new producer", e)
                connectionErrorCount.incrementAndGet()
            }
        }

        // If still no producer, wait for one to become available
        if (pooledProducer == null) {
            try {
                val future = CompletableFuture<PooledProducer>()

                executorService.submit {
                    try {
                        // Wait up to 30 seconds for a producer to become available
                        var attempts = 0
                        while (attempts < 300 && !closed) { // 300 * 100ms = 30s
                            val producer = producers.poll()
                            if (producer != null) {
                                future.complete(producer)
                                return@submit
                            }
                            Thread.sleep(100)
                            attempts++
                        }
                        future.completeExceptionally(TimeoutException("No producer available within timeout"))
                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }

                pooledProducer = future.get(config.connectionTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                logger.error("Failed to acquire producer within timeout", e)
                connectionErrorCount.incrementAndGet()
                throw RuntimeException("Failed to acquire producer", e)
            }
        }

        activeConnections.incrementAndGet()
        pooledProducer.lastUsed = System.currentTimeMillis()
        return pooledProducer.producer
    }

    /**
     * Return a producer to the pool
     */
    fun returnProducer(producer: KafkaProducer<String, String>) {
        if (closed) {
            producer.close()
            return
        }

        activeConnections.decrementAndGet()
        producers.offer(PooledProducer(producer, System.currentTimeMillis()))
    }

    /**
     * Create a new Kafka producer with optimized settings
     */
    private fun createNewProducer(): KafkaProducer<String, String> {
        val producerConfig = Properties()
        producerConfig.putAll(config.baseKafkaConfig)

        // Connection pool optimizations
        producerConfig[ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG] = config.connectionIdleTimeoutMs
        producerConfig[ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG] = 50L
        producerConfig[ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 1000L
        producerConfig[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = config.requestTimeoutMs
        producerConfig[ProducerConfig.RETRY_BACKOFF_MS_CONFIG] = 100L

        // Thread safety settings
        producerConfig[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 1
        producerConfig[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true

        return KafkaProducer<String, String>(producerConfig)
    }

    /**
     * Start maintenance task for pool cleanup and health monitoring
     */
    private fun startMaintenanceTask() {
        maintenanceExecutor.scheduleWithFixedDelay({
            try {
                performMaintenance()
            } catch (e: Exception) {
                logger.error("Error during pool maintenance", e)
            }
        }, 60, 60, TimeUnit.SECONDS) // Run every minute
    }

    /**
     * Perform pool maintenance tasks
     */
    private fun performMaintenance() {
        if (closed) return

        val currentTime = System.currentTimeMillis()
        val idleTimeout = config.connectionIdleTimeoutMs

        // Remove idle connections
        val iterator = producers.iterator()
        var removedCount = 0

        while (iterator.hasNext()) {
            val pooledProducer = iterator.next()
            if (currentTime - pooledProducer.lastUsed > idleTimeout) {
                iterator.remove()
                try {
                    pooledProducer.producer.close(Duration.ofSeconds(5))
                    totalConnections.decrementAndGet()
                    removedCount++
                } catch (e: Exception) {
                    logger.warn("Error closing idle producer", e)
                }
            }
        }

        if (removedCount > 0) {
            logger.debug("Removed $removedCount idle connections, remaining: ${producers.size}")
        }

        // Ensure minimum pool size
        val currentSize = producers.size
        if (currentSize < config.initialSize) {
            val toCreate = config.initialSize - currentSize
            repeat(toCreate) {
                try {
                    val producer = createNewProducer()
                    producers.offer(PooledProducer(producer, currentTime))
                    totalConnections.incrementAndGet()
                } catch (e: Exception) {
                    logger.warn("Failed to create replacement producer during maintenance", e)
                }
            }
            logger.debug("Created $toCreate replacement connections during maintenance")
        }

        // Log pool statistics
        logPoolStatistics()
    }

    /**
     * Log current pool statistics
     */
    private fun logPoolStatistics() {
        val stats = getPoolStatistics()
        logger.debug(
            "Pool stats - Total: ${stats.totalConnections}, " +
                "Active: ${stats.activeConnections}, " +
                "Available: ${stats.availableConnections}, " +
                "Requests: ${stats.totalRequests}, " +
                "Errors: ${stats.errorCount}",
        )
    }

    /**
     * Get current pool statistics
     */
    fun getPoolStatistics(): PoolStatistics {
        return PoolStatistics(
            totalConnections = totalConnections.get(),
            activeConnections = activeConnections.get(),
            availableConnections = producers.size,
            totalRequests = connectionRequestCount.get(),
            errorCount = connectionErrorCount.get(),
            threadPoolSize = (executorService as ThreadPoolExecutor).poolSize,
            threadPoolActiveCount = (executorService as ThreadPoolExecutor).activeCount,
            threadPoolQueueSize = (executorService as ThreadPoolExecutor).queue.size,
        )
    }

    /**
     * Health check for the connection pool
     */
    fun healthCheck(): PoolHealthStatus {
        val stats = getPoolStatistics()
        val issues = mutableListOf<String>()

        // Check if we have any available connections
        if (stats.availableConnections == 0 && stats.activeConnections == stats.totalConnections) {
            issues.add("No available connections, pool may be exhausted")
        }

        // Check error rate
        val errorRate =
            if (stats.totalRequests > 0) {
                (stats.errorCount.toDouble() / stats.totalRequests.toDouble()) * 100
            } else {
                0.0
            }

        if (errorRate > 10.0) {
            issues.add("High error rate: ${errorRate.format(1)}%")
        }

        // Check thread pool utilization
        val threadUtilization =
            if (config.maxThreads > 0) {
                (stats.threadPoolActiveCount.toDouble() / config.maxThreads.toDouble()) * 100
            } else {
                0.0
            }

        if (threadUtilization > 90.0) {
            issues.add("High thread pool utilization: ${threadUtilization.format(1)}%")
        }

        val status =
            when {
                issues.isEmpty() -> HealthStatus.HEALTHY
                issues.size <= 2 -> HealthStatus.WARNING
                else -> HealthStatus.UNHEALTHY
            }

        return PoolHealthStatus(status, issues, stats)
    }

    /**
     * Close the connection pool and all producers
     */
    fun close() {
        if (closed) return

        logger.info("Closing Kafka connection pool...")
        closed = true

        // Stop maintenance task
        maintenanceExecutor.shutdown()

        // Close all producers
        var closedCount = 0
        while (true) {
            val pooledProducer = producers.poll() ?: break
            try {
                pooledProducer.producer.close(Duration.ofSeconds(5))
                closedCount++
            } catch (e: Exception) {
                logger.warn("Error closing producer during shutdown", e)
            }
        }

        // Shutdown executor service
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }

        logger.info("Connection pool closed, $closedCount producers closed")
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    companion object {
        private val threadCounter = AtomicInteger(0)
    }
}

/**
 * Pooled producer wrapper
 */
private data class PooledProducer(
    val producer: KafkaProducer<String, String>,
    var lastUsed: Long,
)

/**
 * Connection pool configuration
 */
data class ConnectionPoolConfig(
    val initialSize: Int = 3,
    val maxPoolSize: Int = 10,
    val connectionTimeoutMs: Long = 30000L,
    val connectionIdleTimeoutMs: Long = 300000L,
    val requestTimeoutMs: Long = 30000L,
    val coreThreads: Int = 2,
    val maxThreads: Int = 8,
    val keepAliveTime: Long = 60000L,
    val queueCapacity: Int = 100,
    val baseKafkaConfig: Properties,
)

/**
 * Pool statistics
 */
data class PoolStatistics(
    val totalConnections: Int,
    val activeConnections: Int,
    val availableConnections: Int,
    val totalRequests: Long,
    val errorCount: Long,
    val threadPoolSize: Int,
    val threadPoolActiveCount: Int,
    val threadPoolQueueSize: Int,
)

/**
 * Pool health status
 */
data class PoolHealthStatus(
    val status: HealthStatus,
    val issues: List<String>,
    val statistics: PoolStatistics,
)

/**
 * Health status enumeration
 */
enum class HealthStatus {
    HEALTHY,
    WARNING,
    UNHEALTHY,
}
