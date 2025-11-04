package org.scriptonbasestar.kcexts.events.nats.metrics

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.metrics.EventMetrics
import org.scriptonbasestar.kcexts.events.common.metrics.MetricsSummary
import org.scriptonbasestar.kcexts.events.common.metrics.TimerSample
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * NATS Event Listener Metrics collector implementing common EventMetrics interface
 */
class NatsEventMetrics : EventMetrics {
    companion object {
        private val logger = Logger.getLogger(NatsEventMetrics::class.java)
    }

    private val eventsSent = ConcurrentHashMap<String, AtomicLong>()
    private val eventsFailed = ConcurrentHashMap<String, AtomicLong>()
    private val eventDurations = ConcurrentHashMap<String, AtomicLong>()
    private val eventDurationCounts = ConcurrentHashMap<String, AtomicLong>()

    override fun recordEventSent(
        eventType: String,
        realm: String,
        destination: String,
        sizeBytes: Int,
    ) {
        val key = "$eventType:$realm:$destination"
        eventsSent.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        logger.trace("Event sent: type=$eventType, realm=$realm, destination=$destination, size=$sizeBytes")
    }

    override fun recordEventFailed(
        eventType: String,
        realm: String,
        destination: String,
        errorType: String,
    ) {
        val key = "$eventType:$realm:$destination:$errorType"
        eventsFailed.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        logger.warn("Event failed: type=$eventType, realm=$realm, destination=$destination, error=$errorType")
    }

    override fun startTimer(): TimerSample = TimerSample(System.nanoTime())

    override fun stopTimer(
        sample: TimerSample,
        eventType: String,
    ) {
        val duration = System.nanoTime() - sample.startTime
        eventDurations.computeIfAbsent(eventType) { AtomicLong(0) }.addAndGet(duration)
        eventDurationCounts.computeIfAbsent(eventType) { AtomicLong(0) }.incrementAndGet()
        logger.trace("Event completed: type=$eventType, duration=${duration / 1_000_000}ms")
    }

    override fun getMetricsSummary(): MetricsSummary {
        val totalSent = eventsSent.values.sumOf { it.get() }
        val totalFailed = eventsFailed.values.sumOf { it.get() }

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
            errorsByType = eventsFailed.mapValues { it.value.get() },
            eventsByType = eventsSent.mapValues { it.value.get() },
        )
    }

    /**
     * Reset all metrics (for testing)
     */
    fun reset() {
        eventsSent.clear()
        eventsFailed.clear()
        eventDurations.clear()
        eventDurationCounts.clear()
        logger.info("NATS metrics reset")
    }

    /**
     * Log current metrics summary
     */
    fun logMetrics() {
        val summary = getMetricsSummary()
        logger.info(
            "NATS Event Metrics - Sent: ${summary.totalSent}, " +
                "Failed: ${summary.totalFailed}, " +
                "Avg Latency: ${"%.2f".format(summary.avgLatencyMs)}ms",
        )
    }
}
