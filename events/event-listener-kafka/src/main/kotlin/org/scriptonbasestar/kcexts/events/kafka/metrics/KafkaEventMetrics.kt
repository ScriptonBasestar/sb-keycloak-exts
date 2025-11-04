package org.scriptonbasestar.kcexts.events.kafka.metrics

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.metrics.EventMetrics
import org.scriptonbasestar.kcexts.events.common.metrics.MetricsSummary
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.common.metrics.TimerSample
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Kafka Event Listener Metrics collector implementing common EventMetrics interface
 * Provides essential metrics for monitoring event processing with Kafka-specific features
 */
class KafkaEventMetrics(
    private val prometheusExporter: PrometheusMetricsExporter? = null,
) : EventMetrics {
    companion object {
        private val logger = Logger.getLogger(KafkaEventMetrics::class.java)
        private const val LISTENER_TYPE = "kafka"
    }

    // Basic counters
    private val eventsSentTotal = AtomicLong(0)
    private val eventsFailedTotal = AtomicLong(0)
    private val activeSessions = AtomicLong(0)
    private val eventSizeAccumulator = AtomicLong(0)
    private val eventCountAccumulator = AtomicLong(0)
    private val connectionStatus = AtomicLong(1) // 1 = connected, 0 = disconnected

    // For common interface compatibility
    private val eventDurations = ConcurrentHashMap<String, AtomicLong>()
    private val eventDurationCounts = ConcurrentHashMap<String, AtomicLong>()
    private val errorsByType = ConcurrentHashMap<String, AtomicLong>()
    private val eventsByType = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Record successful event sent (implements EventMetrics interface)
     */
    override fun recordEventSent(
        eventType: String,
        realm: String,
        destination: String,
        sizeBytes: Int,
    ) {
        eventsSentTotal.incrementAndGet()
        eventSizeAccumulator.addAndGet(sizeBytes.toLong())
        eventCountAccumulator.incrementAndGet()

        // Track by type for common interface
        val key = "$eventType:$realm:$destination"
        eventsByType.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()

        // Export to Prometheus
        prometheusExporter?.recordEventSent(eventType, realm, destination, sizeBytes, LISTENER_TYPE)

        logger.trace("Event sent recorded: type=$eventType, realm=$realm, destination=$destination, size=$sizeBytes")
    }

    /**
     * Record failed event (implements EventMetrics interface)
     */
    override fun recordEventFailed(
        eventType: String,
        realm: String,
        destination: String,
        errorType: String,
    ) {
        eventsFailedTotal.incrementAndGet()

        // Track errors by type for common interface
        val key = "$eventType:$realm:$destination:$errorType"
        errorsByType.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()

        // Export to Prometheus
        prometheusExporter?.recordEventFailed(eventType, realm, destination, errorType, LISTENER_TYPE)

        logger.debug("Event failure recorded: type=$eventType, realm=$realm, destination=$destination, error=$errorType")
    }

    /**
     * Start timing an event (implements EventMetrics interface)
     */
    override fun startTimer(): TimerSample = TimerSample(System.nanoTime())

    /**
     * Stop timing and record (implements EventMetrics interface)
     */
    override fun stopTimer(
        sample: TimerSample,
        eventType: String,
    ) {
        val duration = System.nanoTime() - sample.startTime
        eventDurations.computeIfAbsent(eventType) { AtomicLong(0) }.addAndGet(duration)
        eventDurationCounts.computeIfAbsent(eventType) { AtomicLong(0) }.incrementAndGet()

        // Export to Prometheus (convert nanoseconds to seconds)
        prometheusExporter?.recordEventDuration(eventType, LISTENER_TYPE, duration / 1_000_000_000.0)

        logger.trace("Event processing completed: type=$eventType, duration=${duration / 1_000_000}ms")
    }

    /**
     * Update connection status
     */
    fun updateConnectionStatus(connected: Boolean) {
        connectionStatus.set(if (connected) 1 else 0)
        prometheusExporter?.updateConnectionStatus(LISTENER_TYPE, connected)
        logger.debug("Kafka connection status updated: $connected")
    }

    /**
     * Increment active sessions
     */
    fun incrementActiveSessions() {
        activeSessions.incrementAndGet()
    }

    /**
     * Decrement active sessions
     */
    fun decrementActiveSessions() {
        activeSessions.decrementAndGet()
    }

    /**
     * Get metrics summary (implements EventMetrics interface)
     */
    override fun getMetricsSummary(): MetricsSummary {
        val totalSent = eventsSentTotal.get()
        val totalFailed = eventsFailedTotal.get()

        // Calculate average latency in milliseconds
        val totalDuration = eventDurations.values.sumOf { it.get() }
        val totalDurationCount = eventDurationCounts.values.sumOf { it.get() }
        val avgLatencyMs =
            if (totalDurationCount > 0) {
                (totalDuration.toDouble() / totalDurationCount) / 1_000_000.0
            } else {
                0.0
            }

        return MetricsSummary(
            totalSent = totalSent,
            totalFailed = totalFailed,
            avgLatencyMs = avgLatencyMs,
            errorsByType = errorsByType.mapValues { it.value.get() },
            eventsByType = eventsByType.mapValues { it.value.get() },
        )
    }

    /**
     * Get extended Kafka-specific metrics summary
     */
    fun getKafkaMetricsSummary(): KafkaMetricsSummary {
        val totalEventsSent = eventsSentTotal.get()
        val totalEventsFailed = eventsFailedTotal.get()
        val avgEventSize =
            if (eventCountAccumulator.get() > 0) {
                eventSizeAccumulator.get() / eventCountAccumulator.get()
            } else {
                0L
            }

        return KafkaMetricsSummary(
            totalEventsSent = totalEventsSent,
            totalEventsFailed = totalEventsFailed,
            successRate =
                if (totalEventsSent + totalEventsFailed > 0) {
                    (totalEventsSent.toDouble() / (totalEventsSent + totalEventsFailed)) * 100
                } else {
                    100.0
                },
            activeSessions = activeSessions.get(),
            producerQueueSize = 0L, // Not tracked in basic version
            connectionStatus = connectionStatus.get() == 1L,
            averageEventSizeBytes = avgEventSize,
        )
    }

    /**
     * Reset all metrics (for testing)
     */
    fun reset() {
        eventsSentTotal.set(0)
        eventsFailedTotal.set(0)
        activeSessions.set(0)
        eventSizeAccumulator.set(0)
        eventCountAccumulator.set(0)
        connectionStatus.set(1)
        eventDurations.clear()
        eventDurationCounts.clear()
        errorsByType.clear()
        eventsByType.clear()
        logger.info("Kafka metrics reset")
    }

    /**
     * Log current metrics summary
     */
    fun logMetricsSummary() {
        val summary = getKafkaMetricsSummary()
        logger.info(
            "Kafka Event Metrics Summary - Sent: ${summary.totalEventsSent}, " +
                "Failed: ${summary.totalEventsFailed}, Success Rate: ${"%.2f".format(summary.successRate)}%, " +
                "Active Sessions: ${summary.activeSessions}, Avg Size: ${summary.averageEventSizeBytes} bytes, " +
                "Connected: ${summary.connectionStatus}",
        )
    }
}

/**
 * Kafka-specific extended metrics summary data class
 */
data class KafkaMetricsSummary(
    val totalEventsSent: Long,
    val totalEventsFailed: Long,
    val successRate: Double,
    val activeSessions: Long,
    val producerQueueSize: Long,
    val connectionStatus: Boolean,
    val averageEventSizeBytes: Long,
)
