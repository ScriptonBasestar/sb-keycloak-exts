package org.scriptonbasestar.kcexts.events.kafka.metrics

import org.jboss.logging.Logger
import java.util.concurrent.atomic.AtomicLong

/**
 * Basic Kafka Event Listener Metrics collector (without Micrometer/Prometheus dependencies)
 * Provides essential metrics for monitoring event processing
 */
class KafkaEventMetrics {
    companion object {
        private val logger = Logger.getLogger(KafkaEventMetrics::class.java)
    }

    // Basic counters
    private val eventsSentTotal = AtomicLong(0)
    private val eventsFailedTotal = AtomicLong(0)
    private val activeSessions = AtomicLong(0)
    private val eventSizeAccumulator = AtomicLong(0)
    private val eventCountAccumulator = AtomicLong(0)
    private val connectionStatus = AtomicLong(1) // 1 = connected, 0 = disconnected

    // Simple timer for tracking processing times
    class TimerSample(private val startTime: Long = System.currentTimeMillis()) {
        fun stop(): Long = System.currentTimeMillis() - startTime
    }

    /**
     * Record successful event sent
     */
    fun recordEventSent(
        eventType: String,
        realm: String,
        topic: String,
        sizeBytes: Int,
    ) {
        eventsSentTotal.incrementAndGet()
        eventSizeAccumulator.addAndGet(sizeBytes.toLong())
        eventCountAccumulator.incrementAndGet()
        
        logger.trace("Event sent recorded: type=$eventType, realm=$realm, topic=$topic, size=$sizeBytes")
    }

    /**
     * Record failed event
     */
    fun recordEventFailed(
        eventType: String,
        realm: String,
        topic: String,
        errorType: String,
    ) {
        eventsFailedTotal.incrementAndGet()
        logger.debug("Event failure recorded: type=$eventType, realm=$realm, topic=$topic, error=$errorType")
    }

    /**
     * Start timing an event
     */
    fun startTimer(): TimerSample {
        return TimerSample()
    }

    /**
     * Stop timing and record
     */
    fun stopTimer(
        sample: TimerSample,
        eventType: String,
    ) {
        val duration = sample.stop()
        logger.trace("Event processing completed: type=$eventType, duration=${duration}ms")
    }

    /**
     * Update connection status
     */
    fun updateConnectionStatus(connected: Boolean) {
        connectionStatus.set(if (connected) 1 else 0)
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
     * Get metrics summary for logging
     */
    fun getMetricsSummary(): MetricsSummary {
        val totalEventsSent = eventsSentTotal.get()
        val totalEventsFailed = eventsFailedTotal.get()
        val avgEventSize = if (eventCountAccumulator.get() > 0) {
            eventSizeAccumulator.get() / eventCountAccumulator.get()
        } else {
            0L
        }

        return MetricsSummary(
            totalEventsSent = totalEventsSent,
            totalEventsFailed = totalEventsFailed,
            successRate = if (totalEventsSent + totalEventsFailed > 0) {
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
        logger.info("Basic metrics reset")
    }

    /**
     * Log current metrics summary
     */
    fun logMetricsSummary() {
        val summary = getMetricsSummary()
        logger.info("Kafka Event Metrics Summary - Sent: ${summary.totalEventsSent}, " +
                   "Failed: ${summary.totalEventsFailed}, Success Rate: ${"%.2f".format(summary.successRate)}%, " +
                   "Active Sessions: ${summary.activeSessions}, Avg Size: ${summary.averageEventSizeBytes} bytes, " +
                   "Connected: ${summary.connectionStatus}")
    }
}

/**
 * Metrics summary data class
 */
data class MetricsSummary(
    val totalEventsSent: Long,
    val totalEventsFailed: Long,
    val successRate: Double,
    val activeSessions: Long,
    val producerQueueSize: Long,
    val connectionStatus: Boolean,
    val averageEventSizeBytes: Long,
)