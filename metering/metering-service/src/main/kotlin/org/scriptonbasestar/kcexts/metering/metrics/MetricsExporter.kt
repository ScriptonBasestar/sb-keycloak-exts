package org.scriptonbasestar.kcexts.metering.metrics

import io.prometheus.client.Counter
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.exporter.HTTPServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * Prometheus metrics exporter using SimpleCient
 *
 * Exposes metrics at /metrics endpoint for Prometheus scraping
 * Uses Prometheus SimpleCient library (simpler than Micrometer, more stable API)
 */
class MetricsExporter(
    private val port: Int,
) {
    private val logger = LoggerFactory.getLogger(MetricsExporter::class.java)
    private lateinit var httpServer: HTTPServer

    // Metrics - Prometheus Counters
    private val eventCounter =
        Counter
            .build()
            .name("keycloak_metering_events_total")
            .help("Total number of Keycloak events processed")
            .register()

    private val errorCounter =
        Counter
            .build()
            .name("keycloak_metering_errors_total")
            .help("Total number of processing errors")
            .labelNames("event_type", "error_type")
            .register()

    private val userEventCounter =
        Counter
            .build()
            .name("keycloak_user_events_total")
            .help("Keycloak user events by type")
            .labelNames("event_type", "realm_id", "client_id", "success")
            .register()

    fun start() {
        try {
            // Export JVM metrics (heap, threads, GC, etc.)
            DefaultExports.initialize()

            // Start HTTP server (port, daemon)
            httpServer = HTTPServer(port, true)

            logger.info("Metrics exporter started on port $port")
            logger.info("Metrics available at http://localhost:$port/metrics")
        } catch (e: Exception) {
            logger.error("Failed to start metrics exporter", e)
            throw e
        }
    }

    fun stop() {
        try {
            httpServer.close()
            logger.info("Metrics exporter stopped")
        } catch (e: Exception) {
            logger.error("Error stopping metrics exporter", e)
        }
    }

    fun recordEvent(
        eventType: String,
        realmId: String,
        clientId: String,
        success: Boolean,
    ) {
        try {
            eventCounter.inc()
            userEventCounter
                .labels(eventType, realmId, clientId, success.toString())
                .inc()
        } catch (e: Exception) {
            logger.error("Failed to record event metric", e)
        }
    }

    fun recordError(
        eventType: String,
        errorType: String,
    ) {
        try {
            errorCounter.labels(eventType, errorType).inc()
        } catch (e: Exception) {
            logger.error("Failed to record error metric", e)
        }
    }
}
