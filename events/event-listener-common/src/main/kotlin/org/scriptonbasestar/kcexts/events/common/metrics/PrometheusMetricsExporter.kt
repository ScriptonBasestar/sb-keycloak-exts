package org.scriptonbasestar.kcexts.events.common.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import org.jboss.logging.Logger
import java.io.IOException

/**
 * Prometheus metrics exporter for Keycloak event listeners
 * Exposes metrics via HTTP endpoint for Prometheus scraping
 */
class PrometheusMetricsExporter(
    private val port: Int = 9090,
    private val enableJvmMetrics: Boolean = true,
) {
    companion object {
        private val logger = Logger.getLogger(PrometheusMetricsExporter::class.java)

        // Events sent counter
        val eventsSent =
            Counter
                .build()
                .name("keycloak_events_sent_total")
                .help("Total number of events successfully sent")
                .labelNames("event_type", "realm", "destination", "listener_type")
                .register()

        // Events failed counter
        val eventsFailed =
            Counter
                .build()
                .name("keycloak_events_failed_total")
                .help("Total number of events that failed to send")
                .labelNames("event_type", "realm", "destination", "error_type", "listener_type")
                .register()

        // Event processing latency histogram
        val eventLatency =
            Histogram
                .build()
                .name("keycloak_event_processing_duration_seconds")
                .help("Event processing duration in seconds")
                .labelNames("event_type", "listener_type")
                .buckets(0.001, 0.005, 0.010, 0.025, 0.050, 0.100, 0.250, 0.500, 1.0, 2.5, 5.0)
                .register()

        // Event size histogram
        val eventSize =
            Histogram
                .build()
                .name("keycloak_event_size_bytes")
                .help("Event message size in bytes")
                .labelNames("event_type", "listener_type")
                .buckets(100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0, 10000.0, 25000.0)
                .register()

        // Active connections gauge
        val activeConnections =
            Gauge
                .build()
                .name("keycloak_event_listener_connections")
                .help("Number of active connections to messaging system")
                .labelNames("listener_type", "status")
                .register()
    }

    private var httpServer: HTTPServer? = null

    /**
     * Start the Prometheus HTTP server
     */
    fun start() {
        try {
            if (enableJvmMetrics) {
                // Register JVM metrics (memory, threads, GC, etc.)
                DefaultExports.initialize()
                logger.info("JVM metrics registered for Prometheus")
            }

            httpServer = HTTPServer(port)
            logger.info("Prometheus metrics exporter started on port $port")
        } catch (e: IOException) {
            logger.error("Failed to start Prometheus metrics exporter on port $port", e)
            throw e
        }
    }

    /**
     * Stop the Prometheus HTTP server
     */
    fun stop() {
        try {
            httpServer?.close()
            logger.info("Prometheus metrics exporter stopped")
        } catch (e: Exception) {
            logger.error("Error stopping Prometheus metrics exporter", e)
        } finally {
            httpServer = null
        }
    }

    /**
     * Check if the exporter is running
     */
    fun isRunning(): Boolean = httpServer != null

    /**
     * Record an event sent
     */
    fun recordEventSent(
        eventType: String,
        realm: String,
        destination: String,
        sizeBytes: Int,
        listenerType: String,
    ) {
        eventsSent.labels(eventType, realm, destination, listenerType).inc()
        eventSize.labels(eventType, listenerType).observe(sizeBytes.toDouble())
    }

    /**
     * Record an event failed
     */
    fun recordEventFailed(
        eventType: String,
        realm: String,
        destination: String,
        errorType: String,
        listenerType: String,
    ) {
        eventsFailed.labels(eventType, realm, destination, errorType, listenerType).inc()
    }

    /**
     * Record event processing duration
     */
    fun recordEventDuration(
        eventType: String,
        listenerType: String,
        durationSeconds: Double,
    ) {
        eventLatency.labels(eventType, listenerType).observe(durationSeconds)
    }

    /**
     * Update connection status
     */
    fun updateConnectionStatus(
        listenerType: String,
        connected: Boolean,
    ) {
        val status = if (connected) "connected" else "disconnected"
        activeConnections.labels(listenerType, status).set(if (connected) 1.0 else 0.0)
    }
}
