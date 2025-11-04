package org.scriptonbasestar.kcexts.events.aws.metrics

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.metrics.EventMetrics
import org.scriptonbasestar.kcexts.events.common.metrics.MetricsSummary
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.common.metrics.TimerSample
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * AWS SQS/SNS Event Listener Metrics collector
 */
class AwsEventMetrics(
    private val prometheusExporter: PrometheusMetricsExporter? = null,
) : EventMetrics {
    companion object {
        private val logger = Logger.getLogger(AwsEventMetrics::class.java)
        private const val LISTENER_TYPE = "aws"
    }

    // Basic counters
    private val eventsSentTotal = AtomicLong(0)
    private val eventsFailedTotal = AtomicLong(0)
    private val sqsMessagesSent = AtomicLong(0)
    private val snsMessagesSent = AtomicLong(0)
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

        // Track by type
        val key = "$eventType:$realm:$destination"
        eventsByType.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()

        // Track by service (SQS or SNS)
        when {
            destination.contains("sqs") -> sqsMessagesSent.incrementAndGet()
            destination.contains("sns") -> snsMessagesSent.incrementAndGet()
        }

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

        // Track errors by type
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
        logger.debug("AWS connection status updated: $connected")
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
     * Get extended AWS-specific metrics summary
     */
    fun getAwsMetricsSummary(): AwsMetricsSummary {
        val totalEventsSent = eventsSentTotal.get()
        val totalEventsFailed = eventsFailedTotal.get()

        return AwsMetricsSummary(
            totalEventsSent = totalEventsSent,
            totalEventsFailed = totalEventsFailed,
            successRate =
                if (totalEventsSent + totalEventsFailed > 0) {
                    (totalEventsSent.toDouble() / (totalEventsSent + totalEventsFailed)) * 100
                } else {
                    100.0
                },
            sqsMessagesSent = sqsMessagesSent.get(),
            snsMessagesSent = snsMessagesSent.get(),
            connectionStatus = connectionStatus.get() == 1L,
        )
    }

    /**
     * Reset all metrics (for testing)
     */
    fun reset() {
        eventsSentTotal.set(0)
        eventsFailedTotal.set(0)
        sqsMessagesSent.set(0)
        snsMessagesSent.set(0)
        connectionStatus.set(1)
        eventDurations.clear()
        eventDurationCounts.clear()
        errorsByType.clear()
        eventsByType.clear()
        logger.info("AWS metrics reset")
    }

    /**
     * Log current metrics summary
     */
    fun logMetricsSummary() {
        val summary = getAwsMetricsSummary()
        logger.info(
            "AWS Event Metrics Summary - Sent: ${summary.totalEventsSent}, " +
                "Failed: ${summary.totalEventsFailed}, Success Rate: ${"%.2f".format(summary.successRate)}%, " +
                "SQS: ${summary.sqsMessagesSent}, SNS: ${summary.snsMessagesSent}, " +
                "Connected: ${summary.connectionStatus}",
        )
    }
}

/**
 * AWS-specific extended metrics summary data class
 */
data class AwsMetricsSummary(
    val totalEventsSent: Long,
    val totalEventsFailed: Long,
    val successRate: Double,
    val sqsMessagesSent: Long,
    val snsMessagesSent: Long,
    val connectionStatus: Boolean,
)
