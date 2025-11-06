package org.scriptonbasestar.kcexts.events.azure.metrics

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.metrics.EventMetrics
import org.scriptonbasestar.kcexts.events.common.metrics.MetricsSummary
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.common.metrics.TimerSample
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Azure Service Bus Event Listener Metrics collector
 */
class AzureEventMetrics(
    private val prometheusExporter: PrometheusMetricsExporter? = null,
) : EventMetrics {
    companion object {
        private val logger = Logger.getLogger(AzureEventMetrics::class.java)
        private const val LISTENER_TYPE = "azure"
        private const val QUEUE_PREFIX = "queue:"
        private const val TOPIC_PREFIX = "topic:"
    }

    // Basic counters
    private val eventsSentTotal = AtomicLong(0)
    private val eventsFailedTotal = AtomicLong(0)
    private val queueMessagesSent = AtomicLong(0)
    private val topicMessagesSent = AtomicLong(0)
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

        val key = "$eventType:$realm:$destination"
        eventsByType.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()

        when {
            destination.startsWith(QUEUE_PREFIX) -> queueMessagesSent.incrementAndGet()
            destination.startsWith(TOPIC_PREFIX) -> topicMessagesSent.incrementAndGet()
        }

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

        val key = "$eventType:$realm:$destination:$errorType"
        errorsByType.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()

        prometheusExporter?.recordEventFailed(eventType, realm, destination, errorType, LISTENER_TYPE)

        logger.debug(
            "Event failure recorded: type=$eventType, realm=$realm, destination=$destination, error=$errorType",
        )
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

        prometheusExporter?.recordEventDuration(eventType, LISTENER_TYPE, duration / 1_000_000_000.0)

        logger.trace("Event processing completed: type=$eventType, duration=${duration / 1_000_000}ms")
    }

    /**
     * Update connection status
     */
    fun updateConnectionStatus(connected: Boolean) {
        connectionStatus.set(if (connected) 1 else 0)
        prometheusExporter?.updateConnectionStatus(LISTENER_TYPE, connected)
        logger.debug("Azure Service Bus connection status updated: $connected")
    }

    /**
     * Get metrics summary (implements EventMetrics interface)
     */
    override fun getMetricsSummary(): MetricsSummary {
        val totalSent = eventsSentTotal.get()
        val totalFailed = eventsFailedTotal.get()

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
     * Get extended Azure-specific metrics summary
     */
    fun getAzureMetricsSummary(): AzureMetricsSummary {
        val totalEventsSent = eventsSentTotal.get()
        val totalEventsFailed = eventsFailedTotal.get()

        return AzureMetricsSummary(
            totalEventsSent = totalEventsSent,
            totalEventsFailed = totalEventsFailed,
            successRate =
                if (totalEventsSent + totalEventsFailed > 0) {
                    (totalEventsSent.toDouble() / (totalEventsSent + totalEventsFailed)) * 100
                } else {
                    100.0
                },
            queueMessagesSent = queueMessagesSent.get(),
            topicMessagesSent = topicMessagesSent.get(),
            connectionStatus = connectionStatus.get() == 1L,
        )
    }

    /**
     * Reset all metrics (for testing)
     */
    fun reset() {
        eventsSentTotal.set(0)
        eventsFailedTotal.set(0)
        queueMessagesSent.set(0)
        topicMessagesSent.set(0)
        connectionStatus.set(1)
        eventDurations.clear()
        eventDurationCounts.clear()
        errorsByType.clear()
        eventsByType.clear()
        logger.info("Azure metrics reset")
    }

    /**
     * Log current metrics summary
     */
    fun logMetricsSummary() {
        val summary = getAzureMetricsSummary()
        logger.info(
            "Azure Event Metrics Summary - Sent: ${summary.totalEventsSent}, " +
                "Failed: ${summary.totalEventsFailed}, Success Rate: ${"%.2f".format(summary.successRate)}%, " +
                "Queues: ${summary.queueMessagesSent}, Topics: ${summary.topicMessagesSent}, " +
                "Connected: ${summary.connectionStatus}",
        )
    }
}

/**
 * Azure-specific extended metrics summary data class
 */
data class AzureMetricsSummary(
    val totalEventsSent: Long,
    val totalEventsFailed: Long,
    val successRate: Double,
    val queueMessagesSent: Long,
    val topicMessagesSent: Long,
    val connectionStatus: Boolean,
)
