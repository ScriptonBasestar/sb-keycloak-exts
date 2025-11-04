package org.scriptonbasestar.kcexts.events.rabbitmq.metrics

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.metrics.EventMetrics
import org.scriptonbasestar.kcexts.events.common.metrics.MetricsSummary
import org.scriptonbasestar.kcexts.events.common.metrics.TimerSample
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class RabbitMQEventMetrics : EventMetrics {
    private val logger = Logger.getLogger(RabbitMQEventMetrics::class.java)

    private val eventsSent = ConcurrentHashMap<String, AtomicLong>()
    private val eventsFailed = ConcurrentHashMap<String, AtomicLong>()
    private val eventSizes = ConcurrentHashMap<String, AtomicLong>()
    private val eventDurations = ConcurrentHashMap<String, AtomicLong>()

    override fun recordEventSent(
        eventType: String,
        realm: String,
        destination: String,
        sizeBytes: Int,
    ) {
        val key = "$eventType:$realm:$destination"
        eventsSent.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        eventSizes.computeIfAbsent(key) { AtomicLong(0) }.addAndGet(sizeBytes.toLong())
    }

    override fun recordEventFailed(
        eventType: String,
        realm: String,
        destination: String,
        errorType: String,
    ) {
        val key = "$eventType:$realm:$destination:$errorType"
        eventsFailed.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }

    override fun startTimer(): TimerSample = TimerSample(System.nanoTime())

    override fun stopTimer(
        sample: TimerSample,
        eventType: String,
    ) {
        val duration = System.nanoTime() - sample.startTime
        eventDurations.computeIfAbsent(eventType) { AtomicLong(0) }.addAndGet(duration)
    }

    override fun getMetricsSummary(): MetricsSummary {
        val totalSent = eventsSent.values.sumOf { it.get() }
        val totalFailed = eventsFailed.values.sumOf { it.get() }
        val avgLatency =
            if (eventDurations.isNotEmpty()) {
                eventDurations.values.sumOf { it.get() } / eventDurations.size / 1_000_000.0
            } else {
                0.0
            }

        return MetricsSummary(
            totalSent = totalSent,
            totalFailed = totalFailed,
            avgLatencyMs = avgLatency,
            errorsByType = buildErrorsByType(),
            eventsByType = buildEventsByType(),
        )
    }

    private fun buildErrorsByType(): Map<String, Long> = eventsFailed.mapValues { it.value.get() }

    private fun buildEventsByType(): Map<String, Long> = eventsSent.mapValues { it.value.get() }

    fun logMetrics() {
        val summary = getMetricsSummary()
        logger.info(
            "RabbitMQ Event Metrics - " +
                "Sent: ${summary.totalSent}, " +
                "Failed: ${summary.totalFailed}, " +
                "AvgLatency: ${summary.avgLatencyMs}ms",
        )
    }

    fun reset() {
        eventsSent.clear()
        eventsFailed.clear()
        eventSizes.clear()
        eventDurations.clear()
        logger.debug("RabbitMQ metrics reset")
    }
}
