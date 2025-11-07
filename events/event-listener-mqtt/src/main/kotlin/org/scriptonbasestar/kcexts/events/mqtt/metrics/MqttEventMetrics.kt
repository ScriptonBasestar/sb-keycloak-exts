package org.scriptonbasestar.kcexts.events.mqtt.metrics

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.metrics.EventMetrics
import org.scriptonbasestar.kcexts.events.common.metrics.MetricsSummary
import org.scriptonbasestar.kcexts.events.common.metrics.PrometheusMetricsExporter
import org.scriptonbasestar.kcexts.events.common.metrics.TimerSample
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * MQTT Event Listener Metrics collector implementing common EventMetrics interface.
 * Provides essential metrics for monitoring event processing with MQTT-specific features.
 */
class MqttEventMetrics(
    private val prometheusExporter: PrometheusMetricsExporter? = null,
) : EventMetrics {
    companion object {
        private val logger = Logger.getLogger(MqttEventMetrics::class.java)
        private const val LISTENER_TYPE = "mqtt"
    }

    // Basic counters
    private val eventsSentTotal = AtomicLong(0)
    private val eventsFailedTotal = AtomicLong(0)
    private val activeSessions = AtomicLong(0)
    private val eventSizeAccumulator = AtomicLong(0)
    private val eventCountAccumulator = AtomicLong(0)
    private val connectionStatus = AtomicLong(1) // 1 = connected, 0 = disconnected

    // MQTT-specific metrics
    private val messagesByQos = ConcurrentHashMap<Int, AtomicLong>()
    private val retainedMessages = AtomicLong(0)
    private val reconnectCount = AtomicLong(0)

    // For common interface compatibility
    private val eventDurations = ConcurrentHashMap<String, AtomicLong>()
    private val eventDurationCounts = ConcurrentHashMap<String, AtomicLong>()
    private val errorsByType = ConcurrentHashMap<String, AtomicLong>()
    private val eventsByType = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Record successful event sent (implements EventMetrics interface).
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
     * Record failed event (implements EventMetrics interface).
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

        logger.debug(
            "Event failure recorded: type=$eventType, realm=$realm, destination=$destination, error=$errorType",
        )
    }

    /**
     * Start timing an event (implements EventMetrics interface).
     */
    override fun startTimer(): TimerSample = TimerSample(System.nanoTime())

    /**
     * Stop timing and record (implements EventMetrics interface).
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

        logger.trace("Event duration recorded: type=$eventType, duration=${duration / 1_000_000}ms")
    }

    /**
     * Get metrics summary (implements EventMetrics interface).
     */
    override fun getMetricsSummary(): MetricsSummary {
        val avgLatency =
            if (eventDurationCounts.values.sumOf { it.get() } > 0) {
                val totalDuration = eventDurations.values.sumOf { it.get() }
                val totalCount = eventDurationCounts.values.sumOf { it.get() }
                (totalDuration / totalCount / 1_000_000).toDouble()
            } else {
                0.0
            }

        return MetricsSummary(
            totalSent = eventsSentTotal.get(),
            totalFailed = eventsFailedTotal.get(),
            avgLatencyMs = avgLatency,
            errorsByType = errorsByType.mapValues { it.value.get() },
            eventsByType = eventsByType.mapValues { it.value.get() },
        )
    }

    /**
     * Record MQTT-specific message by QoS level.
     */
    fun recordMessageByQos(qos: Int) {
        messagesByQos.computeIfAbsent(qos) { AtomicLong(0) }.incrementAndGet()
    }

    /**
     * Record retained message.
     */
    fun recordRetainedMessage() {
        retainedMessages.incrementAndGet()
    }

    /**
     * Record connection status change.
     */
    fun setConnectionStatus(connected: Boolean) {
        connectionStatus.set(if (connected) 1 else 0)
    }

    /**
     * Record reconnection attempt.
     */
    fun recordReconnect() {
        reconnectCount.incrementAndGet()
    }

    /**
     * Increment active session count.
     */
    fun incrementActiveSessions() {
        activeSessions.incrementAndGet()
    }

    /**
     * Decrement active session count.
     */
    fun decrementActiveSessions() {
        activeSessions.decrementAndGet()
    }

    /**
     * Get MQTT-specific metrics summary.
     */
    fun getMqttMetricsSummary(): Map<String, Any> =
        mapOf(
            "eventsSent" to eventsSentTotal.get(),
            "eventsFailed" to eventsFailedTotal.get(),
            "activeSessions" to activeSessions.get(),
            "avgEventSizeBytes" to
                (if (eventCountAccumulator.get() > 0) eventSizeAccumulator.get() / eventCountAccumulator.get() else 0),
            "connectionStatus" to (if (connectionStatus.get() == 1L) "connected" else "disconnected"),
            "messagesByQos" to messagesByQos.mapValues { it.value.get() },
            "retainedMessages" to retainedMessages.get(),
            "reconnectCount" to reconnectCount.get(),
            "eventsByType" to eventsByType.mapValues { it.value.get() },
            "errorsByType" to errorsByType.mapValues { it.value.get() },
        )

    /**
     * Reset all metrics (useful for testing).
     */
    fun reset() {
        eventsSentTotal.set(0)
        eventsFailedTotal.set(0)
        activeSessions.set(0)
        eventSizeAccumulator.set(0)
        eventCountAccumulator.set(0)
        connectionStatus.set(1)
        messagesByQos.clear()
        retainedMessages.set(0)
        reconnectCount.set(0)
        eventDurations.clear()
        eventDurationCounts.clear()
        errorsByType.clear()
        eventsByType.clear()
    }
}
