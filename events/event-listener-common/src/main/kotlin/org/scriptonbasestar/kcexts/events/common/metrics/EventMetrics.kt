package org.scriptonbasestar.kcexts.events.common.metrics

/**
 * Common metrics interface for event listeners
 */
interface EventMetrics {
    /**
     * Record a successfully sent event
     */
    fun recordEventSent(
        eventType: String,
        realm: String,
        destination: String,
        sizeBytes: Int,
    )

    /**
     * Record a failed event
     */
    fun recordEventFailed(
        eventType: String,
        realm: String,
        destination: String,
        errorType: String,
    )

    /**
     * Start a timer for latency measurement
     */
    fun startTimer(): TimerSample

    /**
     * Stop a timer and record the latency
     */
    fun stopTimer(
        sample: TimerSample,
        eventType: String,
    )

    /**
     * Get a summary of collected metrics
     */
    fun getMetricsSummary(): MetricsSummary
}

/**
 * Timer sample for latency measurement
 */
data class TimerSample(
    val startTime: Long = System.nanoTime(),
)

/**
 * Summary of event metrics
 */
data class MetricsSummary(
    val totalSent: Long,
    val totalFailed: Long,
    val avgLatencyMs: Double,
    val errorsByType: Map<String, Long>,
    val eventsByType: Map<String, Long>,
)
