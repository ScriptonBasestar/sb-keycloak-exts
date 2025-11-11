package org.scriptonbasestar.kcexts.metering.processor

import org.slf4j.LoggerFactory
import org.scriptonbasestar.kcexts.events.common.model.KeycloakEvent
import org.scriptonbasestar.kcexts.metering.metrics.MetricsExporter
import org.scriptonbasestar.kcexts.metering.model.UserEventMetric
import org.scriptonbasestar.kcexts.metering.storage.StorageBackend

/**
 * Event processor for transforming and storing Keycloak events
 *
 * Responsibilities:
 * 1. Transform KeycloakEvent to UserEventMetric
 * 2. Store metrics in time-series database
 * 3. Update Prometheus metrics
 */
class EventProcessor(
    private val storage: StorageBackend,
    private val metricsExporter: MetricsExporter,
) {
    private val logger = LoggerFactory.getLogger(EventProcessor::class.java)

    fun processUserEvent(event: KeycloakEvent) {
        try {
            // Transform to metric
            val metric = UserEventMetric.fromKeycloakEvent(event)

            // Store in time-series database
            storage.storeUserEvent(metric)

            // Update Prometheus metrics
            metricsExporter.recordEvent(
                eventType = event.type,
                realmId = event.realmId,
                clientId = event.clientId ?: "unknown",
                success = metric.success,
            )

            logger.trace("Processed user event: ${event.type} for realm: ${event.realmId}")
        } catch (e: Exception) {
            logger.error("Failed to process user event: ${event.id}", e)
            metricsExporter.recordError(event.type, e::class.java.simpleName)
        }
    }

    /**
     * Process batch of events (more efficient)
     */
    fun processUserEventsBatch(events: List<KeycloakEvent>) {
        if (events.isEmpty()) {
            return
        }

        try {
            val metrics = events.map { UserEventMetric.fromKeycloakEvent(it) }

            // Batch store
            storage.batchStoreUserEvents(metrics)

            // Update metrics for each event
            metrics.forEach { metric ->
                metricsExporter.recordEvent(
                    eventType = metric.eventType,
                    realmId = metric.realmId,
                    clientId = metric.clientId ?: "unknown",
                    success = metric.success,
                )
            }

            logger.debug("Processed ${events.size} user events in batch")
        } catch (e: Exception) {
            logger.error("Failed to process user events batch", e)
            metricsExporter.recordError("BATCH", e::class.java.simpleName)
        }
    }
}
