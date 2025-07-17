package org.scriptonbasestar.kcexts.events.kafka.performance

import org.apache.kafka.clients.producer.ProducerConfig
import org.jboss.logging.Logger
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Kafka Performance Tuner
 * Provides optimized configuration settings for different scenarios
 */
class KafkaPerformanceTuner {
    companion object {
        private val logger = Logger.getLogger(KafkaPerformanceTuner::class.java)

        // Performance profiles
        const val PROFILE_THROUGHPUT = "throughput"
        const val PROFILE_LATENCY = "latency"
        const val PROFILE_BALANCED = "balanced"
        const val PROFILE_RELIABILITY = "reliability"
    }

    private val lastTuneTime = AtomicLong(0)
    private val tuneInterval = 60000L // 1 minute

    /**
     * Get optimized producer configuration based on performance profile
     */
    fun getOptimizedProducerConfig(
        profile: String,
        baseConfig: Properties,
        metrics: PerformanceMetrics? = null,
    ): Properties {
        val optimizedConfig = Properties()
        optimizedConfig.putAll(baseConfig)

        when (profile.lowercase()) {
            PROFILE_THROUGHPUT -> applyThroughputOptimizations(optimizedConfig)
            PROFILE_LATENCY -> applyLatencyOptimizations(optimizedConfig)
            PROFILE_BALANCED -> applyBalancedOptimizations(optimizedConfig)
            PROFILE_RELIABILITY -> applyReliabilityOptimizations(optimizedConfig)
            else -> {
                logger.warn("Unknown performance profile: $profile, using balanced configuration")
                applyBalancedOptimizations(optimizedConfig)
            }
        }

        // Apply dynamic tuning based on current metrics
        if (metrics != null) {
            applyDynamicTuning(optimizedConfig, metrics)
        }

        logger.info("Applied $profile performance profile to Kafka producer configuration")
        return optimizedConfig
    }

    /**
     * Throughput-optimized configuration
     * Maximizes events per second, may increase latency
     */
    private fun applyThroughputOptimizations(config: Properties) {
        // Batch settings for maximum throughput
        config[ProducerConfig.BATCH_SIZE_CONFIG] = 65536 // 64KB
        config[ProducerConfig.LINGER_MS_CONFIG] = 20 // Wait up to 20ms to batch
        config[ProducerConfig.BUFFER_MEMORY_CONFIG] = 67108864L // 64MB

        // Compression for better throughput
        config[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"

        // Multiple in-flight requests
        config[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5

        // Reliability trade-offs for throughput
        config[ProducerConfig.ACKS_CONFIG] = "1" // Leader acknowledgment only
        config[ProducerConfig.RETRIES_CONFIG] = 3
        config[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = false

        // Network optimizations
        config[ProducerConfig.SEND_BUFFER_BYTES_CONFIG] = 262144 // 256KB
        config[ProducerConfig.RECEIVE_BUFFER_BYTES_CONFIG] = 131072 // 128KB

        logger.info("Applied throughput optimizations: large batches, compression, multiple in-flight requests")
    }

    /**
     * Latency-optimized configuration
     * Minimizes event processing time, may reduce throughput
     */
    private fun applyLatencyOptimizations(config: Properties) {
        // Minimal batching for low latency
        config[ProducerConfig.BATCH_SIZE_CONFIG] = 1024 // 1KB
        config[ProducerConfig.LINGER_MS_CONFIG] = 0 // No waiting
        config[ProducerConfig.BUFFER_MEMORY_CONFIG] = 16777216L // 16MB

        // No compression to reduce CPU overhead
        config[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "none"

        // Single in-flight request to maintain order and reduce latency variance
        config[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 1

        // Fast failure detection
        config[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 10000 // 10 seconds
        config[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 30000 // 30 seconds

        // Reliability settings
        config[ProducerConfig.ACKS_CONFIG] = "1"
        config[ProducerConfig.RETRIES_CONFIG] = 3
        config[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true

        logger.info("Applied latency optimizations: minimal batching, no compression, single in-flight")
    }

    /**
     * Balanced configuration
     * Good balance between throughput and latency
     */
    private fun applyBalancedOptimizations(config: Properties) {
        // Moderate batch settings
        config[ProducerConfig.BATCH_SIZE_CONFIG] = 16384 // 16KB
        config[ProducerConfig.LINGER_MS_CONFIG] = 5 // Wait up to 5ms
        config[ProducerConfig.BUFFER_MEMORY_CONFIG] = 33554432L // 32MB

        // Efficient compression
        config[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "snappy"

        // Moderate parallelism
        config[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 3

        // Good reliability
        config[ProducerConfig.ACKS_CONFIG] = "1"
        config[ProducerConfig.RETRIES_CONFIG] = 5
        config[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true

        // Reasonable timeouts
        config[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 30000 // 30 seconds
        config[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 120000 // 2 minutes

        logger.info("Applied balanced optimizations: moderate batching, snappy compression")
    }

    /**
     * Reliability-optimized configuration
     * Maximizes data durability and consistency
     */
    private fun applyReliabilityOptimizations(config: Properties) {
        // Conservative batch settings
        config[ProducerConfig.BATCH_SIZE_CONFIG] = 8192 // 8KB
        config[ProducerConfig.LINGER_MS_CONFIG] = 10 // Wait up to 10ms
        config[ProducerConfig.BUFFER_MEMORY_CONFIG] = 33554432L // 32MB

        // No compression to avoid data corruption risks
        config[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "none"

        // Single in-flight request for strict ordering
        config[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 1

        // Maximum reliability
        config[ProducerConfig.ACKS_CONFIG] = "all" // All replicas
        config[ProducerConfig.RETRIES_CONFIG] = Int.MAX_VALUE
        config[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true

        // Conservative timeouts
        config[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 60000 // 1 minute
        config[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 300000 // 5 minutes

        // Additional reliability settings
        config[ProducerConfig.MAX_BLOCK_MS_CONFIG] = 60000L // 1 minute
        config[ProducerConfig.RETRY_BACKOFF_MS_CONFIG] = 1000L // 1 second

        logger.info("Applied reliability optimizations: all acks, idempotence, conservative timeouts")
    }

    /**
     * Apply dynamic tuning based on current performance metrics
     */
    private fun applyDynamicTuning(
        config: Properties,
        metrics: PerformanceMetrics,
    ) {
        val currentTime = System.currentTimeMillis()

        // Only tune if enough time has passed since last tuning
        if (currentTime - lastTuneTime.get() < tuneInterval) {
            return
        }

        logger.info("Applying dynamic performance tuning based on metrics")

        // Adjust batch size based on throughput
        val currentBatchSize = config.getProperty(ProducerConfig.BATCH_SIZE_CONFIG)?.toInt() ?: 16384
        val targetThroughput = 1000.0 // events per second

        if (metrics.eventsPerSecond < targetThroughput * 0.8) {
            // Low throughput - increase batch size
            val newBatchSize = minOf(currentBatchSize * 2, 65536)
            config[ProducerConfig.BATCH_SIZE_CONFIG] = newBatchSize
            logger.info("Increased batch size to $newBatchSize due to low throughput")
        } else if (metrics.eventsPerSecond > targetThroughput * 1.2) {
            // High throughput - might decrease batch size for lower latency
            if (metrics.averageLatencyMs > 100) {
                val newBatchSize = maxOf(currentBatchSize / 2, 1024)
                config[ProducerConfig.BATCH_SIZE_CONFIG] = newBatchSize
                logger.info("Decreased batch size to $newBatchSize due to high latency")
            }
        }

        // Adjust linger time based on latency requirements
        val currentLingerMs = config.getProperty(ProducerConfig.LINGER_MS_CONFIG)?.toInt() ?: 5

        if (metrics.averageLatencyMs > 200 && currentLingerMs > 0) {
            // High latency - reduce linger time
            val newLingerMs = maxOf(currentLingerMs - 1, 0)
            config[ProducerConfig.LINGER_MS_CONFIG] = newLingerMs
            logger.info("Reduced linger time to ${newLingerMs}ms due to high latency")
        } else if (metrics.averageLatencyMs < 50 && metrics.eventsPerSecond < targetThroughput) {
            // Low latency but also low throughput - increase linger time
            val newLingerMs = minOf(currentLingerMs + 2, 20)
            config[ProducerConfig.LINGER_MS_CONFIG] = newLingerMs
            logger.info("Increased linger time to ${newLingerMs}ms to improve throughput")
        }

        // Adjust buffer memory based on queue size
        if (metrics.queueSizeBytes > 16 * 1024 * 1024) { // 16MB
            val currentBuffer = config.getProperty(ProducerConfig.BUFFER_MEMORY_CONFIG)?.toLong() ?: 33554432L
            val newBuffer = minOf(currentBuffer * 2, 134217728L) // Max 128MB
            config[ProducerConfig.BUFFER_MEMORY_CONFIG] = newBuffer
            logger.info("Increased buffer memory to $newBuffer due to high queue size")
        }

        // Adjust compression based on CPU usage and throughput
        if (metrics.cpuUsagePercent > 80) {
            val currentCompression = config.getProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG)
            if (currentCompression in listOf("lz4", "gzip", "zstd")) {
                config[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "snappy"
                logger.info("Switched to snappy compression due to high CPU usage")
            }
        }

        lastTuneTime.set(currentTime)
        logger.info("Dynamic tuning completed")
    }

    /**
     * Get recommended configuration based on deployment scenario
     */
    fun getRecommendedConfig(scenario: DeploymentScenario): String {
        return when (scenario) {
            DeploymentScenario.HIGH_VOLUME_LOGIN -> PROFILE_THROUGHPUT
            DeploymentScenario.REAL_TIME_MONITORING -> PROFILE_LATENCY
            DeploymentScenario.AUDIT_LOGGING -> PROFILE_RELIABILITY
            DeploymentScenario.GENERAL_PURPOSE -> PROFILE_BALANCED
            DeploymentScenario.DEVELOPMENT -> PROFILE_LATENCY
            DeploymentScenario.TESTING -> PROFILE_BALANCED
        }
    }

    /**
     * Validate configuration for potential performance issues
     */
    fun validatePerformanceConfig(config: Properties): List<PerformanceIssue> {
        val issues = mutableListOf<PerformanceIssue>()

        // Check batch size
        val batchSize = config.getProperty(ProducerConfig.BATCH_SIZE_CONFIG)?.toInt() ?: 16384
        if (batchSize < 1024) {
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.MEDIUM,
                    message = "Batch size is very small ($batchSize bytes)",
                    recommendation = "Consider increasing batch size for better throughput",
                ),
            )
        } else if (batchSize > 100 * 1024) {
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.LOW,
                    message = "Batch size is very large ($batchSize bytes)",
                    recommendation = "Large batch sizes may increase latency",
                ),
            )
        }

        // Check linger time
        val lingerMs = config.getProperty(ProducerConfig.LINGER_MS_CONFIG)?.toInt() ?: 0
        if (lingerMs > 100) {
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.MEDIUM,
                    message = "Linger time is high (${lingerMs}ms)",
                    recommendation = "High linger time increases latency",
                ),
            )
        }

        // Check compression
        val compression = config.getProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG) ?: "none"
        if (compression == "none") {
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.LOW,
                    message = "No compression enabled",
                    recommendation = "Consider enabling snappy or lz4 compression for better throughput",
                ),
            )
        }

        // Check acknowledgment settings
        val acks = config.getProperty(ProducerConfig.ACKS_CONFIG) ?: "1"
        if (acks == "0") {
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.HIGH,
                    message = "Fire-and-forget mode enabled (acks=0)",
                    recommendation = "This may cause data loss, consider using acks=1 or acks=all",
                ),
            )
        }

        // Check buffer memory
        val bufferMemory = config.getProperty(ProducerConfig.BUFFER_MEMORY_CONFIG)?.toLong() ?: 33554432L
        if (bufferMemory < 16 * 1024 * 1024) { // 16MB
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.MEDIUM,
                    message = "Buffer memory is low (${bufferMemory / 1024 / 1024}MB)",
                    recommendation = "Consider increasing buffer memory for better performance",
                ),
            )
        }

        return issues
    }
}

/**
 * Performance metrics data class
 */
data class PerformanceMetrics(
    val eventsPerSecond: Double,
    val averageLatencyMs: Double,
    val p95LatencyMs: Double,
    val p99LatencyMs: Double,
    val queueSizeBytes: Long,
    val cpuUsagePercent: Double,
    val memoryUsagePercent: Double,
    val errorRate: Double,
)

/**
 * Deployment scenarios for configuration recommendations
 */
enum class DeploymentScenario {
    HIGH_VOLUME_LOGIN, // E-commerce, social media
    REAL_TIME_MONITORING, // Security systems, fraud detection
    AUDIT_LOGGING, // Compliance, financial systems
    GENERAL_PURPOSE, // Standard enterprise applications
    DEVELOPMENT, // Development environment
    TESTING, // Testing environment
}

/**
 * Performance issue data class
 */
data class PerformanceIssue(
    val severity: IssueSeverity,
    val message: String,
    val recommendation: String,
)

/**
 * Issue severity levels
 */
enum class IssueSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}
