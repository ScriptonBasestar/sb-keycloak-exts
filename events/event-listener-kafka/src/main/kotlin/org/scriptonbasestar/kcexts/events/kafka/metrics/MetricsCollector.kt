package org.scriptonbasestar.kcexts.events.kafka.metrics

import org.jboss.logging.Logger
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import com.sun.management.OperatingSystemMXBean

/**
 * Comprehensive metrics collector for Keycloak Kafka Event Listener
 * Collects performance, system, and business metrics
 */
class MetricsCollector {
    companion object {
        private val logger = Logger.getLogger(MetricsCollector::class.java)
        private val instance = AtomicReference<MetricsCollector>()
        
        fun getInstance(): MetricsCollector {
            return instance.get() ?: synchronized(this) {
                instance.get() ?: MetricsCollector().also { instance.set(it) }
            }
        }
    }

    private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
    private val operatingSystemMXBean: OperatingSystemMXBean = 
        ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    // Performance counters
    private val eventCount = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private val totalProcessingTime = AtomicLong(0)
    private val latencyHistogram = ConcurrentHashMap<Long, AtomicLong>()
    
    // Connection and circuit breaker states
    private val connectionFailures = AtomicLong(0)
    private val lastConnectionTime = AtomicLong(System.currentTimeMillis())
    @Volatile private var circuitBreakerOpen = false
    @Volatile private var backpressureActive = false
    
    // Kafka specific metrics
    private val kafkaLag = AtomicLong(0)
    private val kafkaConnected = AtomicReference(true)

    // Thread counter for factories
    val threadCounter = AtomicLong(0)

    /**
     * Record an event processing completion
     */
    fun recordEventProcessed(processingTimeMs: Long) {
        eventCount.incrementAndGet()
        totalProcessingTime.addAndGet(processingTimeMs)
        
        // Update latency histogram (rounded to nearest 10ms bucket)
        val bucket = (processingTimeMs / 10) * 10
        latencyHistogram.computeIfAbsent(bucket) { AtomicLong(0) }.incrementAndGet()
        
        logger.trace("Event processed in ${processingTimeMs}ms")
    }

    /**
     * Record an error occurrence
     */
    fun recordError(errorType: String) {
        errorCount.incrementAndGet()
        logger.debug("Error recorded: $errorType")
    }

    /**
     * Record a connection failure
     */
    fun recordConnectionFailure() {
        connectionFailures.incrementAndGet()
        lastConnectionTime.set(System.currentTimeMillis())
        kafkaConnected.set(false)
        logger.warn("Connection failure recorded")
    }

    /**
     * Record successful connection
     */
    fun recordConnectionSuccess() {
        lastConnectionTime.set(System.currentTimeMillis())
        kafkaConnected.set(true)
        logger.debug("Connection success recorded")
    }

    /**
     * Get current error rate (errors per second over last minute)
     */
    fun getErrorRate(): Double {
        val currentTime = System.currentTimeMillis()
        val oneMinuteAgo = currentTime - 60_000
        
        // Simplified calculation - in production would use time-windowed counters
        val totalEvents = eventCount.get()
        val totalErrors = errorCount.get()
        
        return if (totalEvents > 0) {
            (totalErrors.toDouble() / totalEvents.toDouble()) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Get latency percentile
     */
    fun getLatencyPercentile(percentile: Double): Long {
        val totalEvents = latencyHistogram.values.sumOf { it.get() }
        if (totalEvents == 0L) return 0L
        
        val targetCount = (totalEvents * percentile / 100.0).toLong()
        var runningCount = 0L
        
        for ((latency, count) in latencyHistogram.toSortedMap()) {
            runningCount += count.get()
            if (runningCount >= targetCount) {
                return latency
            }
        }
        
        return latencyHistogram.keys.maxOrNull() ?: 0L
    }

    /**
     * Get memory usage percentage
     */
    fun getMemoryUsagePercent(): Double {
        val heapMemory = memoryMXBean.heapMemoryUsage
        return (heapMemory.used.toDouble() / heapMemory.max.toDouble()) * 100.0
    }

    /**
     * Get CPU usage percentage
     */
    fun getCpuUsagePercent(): Double {
        return operatingSystemMXBean.processCpuLoad * 100.0
    }

    /**
     * Get connection failure rate
     */
    fun getConnectionFailureRate(): Double {
        val totalAttempts = eventCount.get()
        val failures = connectionFailures.get()
        
        return if (totalAttempts > 0) {
            (failures.toDouble() / totalAttempts.toDouble()) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Check if circuit breaker is open
     */
    fun isCircuitBreakerOpen(): Boolean = circuitBreakerOpen

    /**
     * Set circuit breaker state
     */
    fun setCircuitBreakerOpen(open: Boolean) {
        circuitBreakerOpen = open
        logger.info("Circuit breaker ${if (open) "opened" else "closed"}")
    }

    /**
     * Check if backpressure is active
     */
    fun isBackpressureActive(): Boolean = backpressureActive

    /**
     * Set backpressure state
     */
    fun setBackpressureActive(active: Boolean) {
        backpressureActive = active
        logger.info("Backpressure ${if (active) "activated" else "deactivated"}")
    }

    /**
     * Get Kafka lag
     */
    fun getKafkaLag(): Long = kafkaLag.get()

    /**
     * Set Kafka lag
     */
    fun setKafkaLag(lag: Long) {
        kafkaLag.set(lag)
    }

    /**
     * Get disk usage percentage
     */
    fun getDiskUsagePercent(): Double {
        return try {
            val file = java.io.File("/")
            val totalSpace = file.totalSpace
            val freeSpace = file.freeSpace
            val usedSpace = totalSpace - freeSpace
            
            (usedSpace.toDouble() / totalSpace.toDouble()) * 100.0
        } catch (e: Exception) {
            logger.warn("Failed to get disk usage", e)
            0.0
        }
    }

    /**
     * Get comprehensive metrics summary
     */
    fun getMetricsSummary(): Map<String, Any> {
        return mapOf(
            "events_processed" to eventCount.get(),
            "errors_total" to errorCount.get(),
            "error_rate_percent" to getErrorRate(),
            "avg_processing_time_ms" to if (eventCount.get() > 0) {
                totalProcessingTime.get() / eventCount.get()
            } else 0L,
            "latency_p50_ms" to getLatencyPercentile(50.0),
            "latency_p95_ms" to getLatencyPercentile(95.0),
            "latency_p99_ms" to getLatencyPercentile(99.0),
            "memory_usage_percent" to getMemoryUsagePercent(),
            "cpu_usage_percent" to getCpuUsagePercent(),
            "disk_usage_percent" to getDiskUsagePercent(),
            "connection_failure_rate" to getConnectionFailureRate(),
            "circuit_breaker_open" to isCircuitBreakerOpen(),
            "backpressure_active" to isBackpressureActive(),
            "kafka_lag" to getKafkaLag(),
            "kafka_connected" to kafkaConnected.get(),
            "last_connection_time" to Instant.ofEpochMilli(lastConnectionTime.get()).toString()
        )
    }

    /**
     * Reset all metrics (for testing)
     */
    fun reset() {
        eventCount.set(0)
        errorCount.set(0)
        totalProcessingTime.set(0)
        latencyHistogram.clear()
        connectionFailures.set(0)
        circuitBreakerOpen = false
        backpressureActive = false
        kafkaLag.set(0)
        kafkaConnected.set(true)
        logger.info("Metrics collector reset")
    }
}