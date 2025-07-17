package org.scriptonbasestar.kcexts.events.kafka.metrics

import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import org.jboss.logging.Logger
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Prometheus metrics exporter for Kafka Event Listener
 * Exposes metrics via HTTP endpoint for Prometheus scraping
 */
class PrometheusMetricsExporter(
    private val port: Int = 9090,
    private val host: String = "0.0.0.0",
    private val path: String = "/metrics",
) {
    companion object {
        private val logger = Logger.getLogger(PrometheusMetricsExporter::class.java)
        private const val DEFAULT_PORT = 9090
    }

    private var httpServer: HTTPServer? = null
    private var prometheusRegistry: PrometheusMeterRegistry? = null
    private val isRunning = AtomicBoolean(false)
    private val kafkaMetrics = KafkaEventMetrics()

    /**
     * Start the metrics HTTP server
     */
    fun start(): PrometheusMeterRegistry {
        if (isRunning.get()) {
            logger.warn("Prometheus metrics exporter is already running")
            return prometheusRegistry!!
        }

        try {
            // Initialize Prometheus registry
            prometheusRegistry = kafkaMetrics.createPrometheusRegistry()

            // Register JVM metrics
            DefaultExports.initialize()

            // Start HTTP server
            httpServer =
                HTTPServer.Builder()
                    .withPort(port)
                    .withHostname(host)
                    .build()

            isRunning.set(true)

            logger.info("Prometheus metrics exporter started on $host:$port$path")
            logger.info("Metrics available at: http://$host:$port$path")

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    stop()
                },
            )

            return prometheusRegistry!!
        } catch (e: Exception) {
            logger.error("Failed to start Prometheus metrics exporter", e)
            throw RuntimeException("Failed to start metrics exporter", e)
        }
    }

    /**
     * Stop the metrics HTTP server
     */
    fun stop() {
        if (!isRunning.get()) {
            logger.debug("Prometheus metrics exporter is not running")
            return
        }

        try {
            httpServer?.close()
            httpServer = null

            prometheusRegistry?.close()
            prometheusRegistry = null

            isRunning.set(false)

            logger.info("Prometheus metrics exporter stopped")
        } catch (e: Exception) {
            logger.error("Error stopping Prometheus metrics exporter", e)
        }
    }

    /**
     * Check if exporter is running
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * Get metrics in Prometheus text format
     */
    fun getMetricsText(): String {
        val writer = StringWriter()
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples())
        return writer.toString()
    }

    /**
     * Get Kafka metrics instance
     */
    fun getKafkaMetrics(): KafkaEventMetrics = kafkaMetrics

    /**
     * Get metrics summary as JSON
     */
    fun getMetricsSummaryJson(): String {
        val summary = kafkaMetrics.getMetricsSummary()
        return """
            {
                "status": "healthy",
                "metrics": {
                    "events": {
                        "sent": ${summary.totalEventsSent},
                        "failed": ${summary.totalEventsFailed},
                        "successRate": ${summary.successRate}
                    },
                    "sessions": {
                        "active": ${summary.activeSessions}
                    },
                    "kafka": {
                        "connected": ${summary.connectionStatus},
                        "queueSize": ${summary.producerQueueSize}
                    },
                    "performance": {
                        "averageEventSizeBytes": ${summary.averageEventSizeBytes}
                    }
                },
                "exporter": {
                    "running": ${isRunning.get()},
                    "endpoint": "http://$host:$port$path"
                }
            }
            """.trimIndent()
    }

    /**
     * Custom metrics endpoint handler
     */
    class MetricsEndpointHandler(
        private val exporter: PrometheusMetricsExporter,
    ) {
        fun handleRequest(format: String = "prometheus"): String {
            return when (format.lowercase()) {
                "prometheus", "text" -> exporter.getMetricsText()
                "json" -> exporter.getMetricsSummaryJson()
                else -> throw IllegalArgumentException("Unsupported format: $format")
            }
        }
    }
}

/**
 * Configuration for Prometheus metrics exporter
 */
data class PrometheusExporterConfig(
    val enabled: Boolean = true,
    val port: Int = 9090,
    val host: String = "0.0.0.0",
    val path: String = "/metrics",
    val includeJvmMetrics: Boolean = true,
    val sampleInterval: Long = 60000, // milliseconds
)
