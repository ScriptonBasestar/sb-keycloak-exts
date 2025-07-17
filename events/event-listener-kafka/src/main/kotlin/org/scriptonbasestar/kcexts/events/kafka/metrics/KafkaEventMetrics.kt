package org.scriptonbasestar.kcexts.events.kafka.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.jboss.logging.Logger
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Kafka Event Listener Metrics collector
 * Provides comprehensive metrics for monitoring event processing
 */
class KafkaEventMetrics : MeterBinder {
    
    companion object {
        private val logger = Logger.getLogger(KafkaEventMetrics::class.java)
        private const val METRIC_PREFIX = "keycloak_kafka"
        
        // Metric names
        const val EVENTS_SENT_TOTAL = "${METRIC_PREFIX}_events_sent_total"
        const val EVENTS_FAILED_TOTAL = "${METRIC_PREFIX}_events_failed_total"
        const val EVENT_PROCESSING_DURATION = "${METRIC_PREFIX}_event_processing_duration"
        const val KAFKA_CONNECTION_STATUS = "${METRIC_PREFIX}_connection_status"
        const val KAFKA_PRODUCER_QUEUE_SIZE = "${METRIC_PREFIX}_producer_queue_size"
        const val ACTIVE_SESSIONS = "${METRIC_PREFIX}_active_sessions"
        const val EVENT_SIZE_BYTES = "${METRIC_PREFIX}_event_size_bytes"
        const val BATCH_SIZE = "${METRIC_PREFIX}_batch_size"
        const val KAFKA_LAG = "${METRIC_PREFIX}_lag"
        
        // Tags
        const val TAG_EVENT_TYPE = "event_type"
        const val TAG_REALM = "realm"
        const val TAG_CLIENT_ID = "client_id"
        const val TAG_TOPIC = "topic"
        const val TAG_STATUS = "status"
        const val TAG_ERROR_TYPE = "error_type"
    }
    
    // Metrics storage
    private val eventCounters = ConcurrentHashMap<String, Counter>()
    private val errorCounters = ConcurrentHashMap<String, Counter>()
    private val timers = ConcurrentHashMap<String, Timer>()
    private val gauges = ConcurrentHashMap<String, AtomicLong>()
    
    // State tracking
    private val activeSessions = AtomicLong(0)
    private val producerQueueSize = AtomicLong(0)
    private val connectionStatus = AtomicLong(1) // 1 = connected, 0 = disconnected
    private val eventSizeAccumulator = AtomicLong(0)
    private val eventCountAccumulator = AtomicLong(0)
    
    private lateinit var registry: MeterRegistry
    
    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry
        
        // Register connection status gauge
        Gauge.builder(KAFKA_CONNECTION_STATUS) { connectionStatus.get().toDouble() }
            .description("Kafka connection status (1 = connected, 0 = disconnected)")
            .register(registry)
        
        // Register producer queue size gauge
        Gauge.builder(KAFKA_PRODUCER_QUEUE_SIZE) { producerQueueSize.get().toDouble() }
            .description("Number of events waiting in producer queue")
            .register(registry)
        
        // Register active sessions gauge
        Gauge.builder(ACTIVE_SESSIONS) { activeSessions.get().toDouble() }
            .description("Number of active user sessions")
            .register(registry)
        
        // Register average event size gauge
        Gauge.builder(EVENT_SIZE_BYTES) { 
            val count = eventCountAccumulator.get()
            val size = eventSizeAccumulator.get()
            if (count > 0) size.toDouble() / count else 0.0
        }
            .description("Average event size in bytes")
            .register(registry)
        
        logger.info("Kafka Event Metrics initialized")
    }
    
    /**
     * Create Prometheus registry for metrics export
     */
    fun createPrometheusRegistry(): PrometheusMeterRegistry {
        return PrometheusMeterRegistry(PrometheusConfig.DEFAULT).also {
            bindTo(it)
        }
    }
    
    /**
     * Record successful event sent
     */
    fun recordEventSent(eventType: String, realm: String, topic: String, sizeBytes: Int) {
        val counter = eventCounters.computeIfAbsent("$eventType:$realm:$topic") {
            Counter.builder(EVENTS_SENT_TOTAL)
                .description("Total number of events sent to Kafka")
                .tag(TAG_EVENT_TYPE, eventType)
                .tag(TAG_REALM, realm)
                .tag(TAG_TOPIC, topic)
                .register(registry)
        }
        counter.increment()
        
        // Update size metrics
        eventSizeAccumulator.addAndGet(sizeBytes.toLong())
        eventCountAccumulator.incrementAndGet()
    }
    
    /**
     * Record failed event
     */
    fun recordEventFailed(eventType: String, realm: String, topic: String, errorType: String) {
        val counter = errorCounters.computeIfAbsent("$eventType:$realm:$topic:$errorType") {
            Counter.builder(EVENTS_FAILED_TOTAL)
                .description("Total number of failed events")
                .tag(TAG_EVENT_TYPE, eventType)
                .tag(TAG_REALM, realm)
                .tag(TAG_TOPIC, topic)
                .tag(TAG_ERROR_TYPE, errorType)
                .register(registry)
        }
        counter.increment()
    }
    
    /**
     * Record event processing time
     */
    fun recordEventProcessingTime(eventType: String, duration: Duration): Timer.Sample {
        val timer = timers.computeIfAbsent(eventType) {
            Timer.builder(EVENT_PROCESSING_DURATION)
                .description("Time taken to process events")
                .tag(TAG_EVENT_TYPE, eventType)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry)
        }
        return Timer.start(registry).also {
            // Record immediately for completed events
            it.stop(timer)
        }
    }
    
    /**
     * Start timing an event
     */
    fun startTimer(): Timer.Sample {
        return Timer.start(registry)
    }
    
    /**
     * Stop timing and record
     */
    fun stopTimer(sample: Timer.Sample, eventType: String) {
        val timer = timers.computeIfAbsent(eventType) {
            Timer.builder(EVENT_PROCESSING_DURATION)
                .description("Time taken to process events")
                .tag(TAG_EVENT_TYPE, eventType)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry)
        }
        sample.stop(timer)
    }
    
    /**
     * Update connection status
     */
    fun updateConnectionStatus(connected: Boolean) {
        connectionStatus.set(if (connected) 1 else 0)
        logger.debug("Kafka connection status updated: $connected")
    }
    
    /**
     * Update producer queue size
     */
    fun updateProducerQueueSize(size: Long) {
        producerQueueSize.set(size)
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
     * Record batch metrics
     */
    fun recordBatch(topic: String, batchSize: Int, duration: Duration) {
        // Record batch size distribution
        registry.summary("${METRIC_PREFIX}_batch_size")
            .tag(TAG_TOPIC, topic)
            .record(batchSize.toDouble())
        
        // Record batch processing time
        registry.timer("${METRIC_PREFIX}_batch_processing_duration")
            .tag(TAG_TOPIC, topic)
            .record(duration)
    }
    
    /**
     * Record Kafka lag metrics
     */
    fun recordKafkaLag(topic: String, partition: Int, lag: Long) {
        gauges.computeIfAbsent("$topic:$partition") { AtomicLong(0) }.set(lag)
        
        Gauge.builder(KAFKA_LAG) { gauges["$topic:$partition"]?.get()?.toDouble() ?: 0.0 }
            .description("Kafka consumer lag")
            .tag(TAG_TOPIC, topic)
            .tag("partition", partition.toString())
            .register(registry)
    }
    
    /**
     * Get metrics summary for logging
     */
    fun getMetricsSummary(): MetricsSummary {
        val totalEventsSent = eventCounters.values.sumOf { it.count() }.toLong()
        val totalEventsFailed = errorCounters.values.sumOf { it.count() }.toLong()
        val avgEventSize = if (eventCountAccumulator.get() > 0) {
            eventSizeAccumulator.get() / eventCountAccumulator.get()
        } else 0L
        
        return MetricsSummary(
            totalEventsSent = totalEventsSent,
            totalEventsFailed = totalEventsFailed,
            successRate = if (totalEventsSent + totalEventsFailed > 0) {
                (totalEventsSent.toDouble() / (totalEventsSent + totalEventsFailed)) * 100
            } else 100.0,
            activeSessions = activeSessions.get(),
            producerQueueSize = producerQueueSize.get(),
            connectionStatus = connectionStatus.get() == 1L,
            averageEventSizeBytes = avgEventSize
        )
    }
    
    /**
     * Reset all metrics (for testing)
     */
    fun reset() {
        eventCounters.clear()
        errorCounters.clear()
        timers.clear()
        gauges.clear()
        activeSessions.set(0)
        producerQueueSize.set(0)
        connectionStatus.set(1)
        eventSizeAccumulator.set(0)
        eventCountAccumulator.set(0)
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
    val averageEventSizeBytes: Long
)