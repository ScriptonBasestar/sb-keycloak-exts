package org.scriptonbasestar.kcexts.events.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.model.AuthDetails
import org.scriptonbasestar.kcexts.events.common.model.KeycloakAdminEvent
import org.scriptonbasestar.kcexts.events.common.model.KeycloakEvent
import org.scriptonbasestar.kcexts.events.kafka.metrics.KafkaEventMetrics
import java.util.*

class KafkaEventListenerProvider(
    private val session: KeycloakSession,
    private val config: KafkaEventListenerConfig,
    private val producerManager: KafkaProducerManager,
    private val metrics: KafkaEventMetrics,
) : EventListenerProvider {
    private val logger = Logger.getLogger(KafkaEventListenerProvider::class.java)
    private val objectMapper = ObjectMapper()

    override fun onEvent(event: Event) {
        if (!config.enableUserEvents) {
            logger.debug("User events are disabled")
            return
        }

        if (!config.includedEventTypes.contains(event.type)) {
            logger.debug("Event type ${event.type} is not included in configuration")
            return
        }

        val timerSample = metrics.startTimer()
        try {
            val keycloakEvent =
                KeycloakEvent(
                    id = UUID.randomUUID().toString(),
                    time = event.time,
                    type = event.type.name,
                    realmId = event.realmId,
                    clientId = event.clientId,
                    userId = event.userId,
                    sessionId = event.sessionId,
                    ipAddress = event.ipAddress,
                    details = event.details,
                )

            val json = objectMapper.writeValueAsString(keycloakEvent)
            val key = "${event.realmId}:${event.type}:${event.userId ?: "anonymous"}"

            producerManager.sendUserEvent(key, json)

            // Record metrics
            metrics.recordEventSent(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                topic = config.eventTopic,
                sizeBytes = json.toByteArray().size,
            )
            metrics.stopTimer(timerSample, event.type.name)

            logger.debug("User event sent to Kafka: type=${event.type}, userId=${event.userId}")
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                topic = config.eventTopic,
                errorType = e.javaClass.simpleName,
            )
            logger.error("Failed to process user event", e)
        }
    }

    override fun onEvent(
        event: AdminEvent,
        includeRepresentation: Boolean,
    ) {
        if (!config.enableAdminEvents) {
            logger.debug("Admin events are disabled")
            return
        }

        val timerSample = metrics.startTimer()
        try {
            val authDetails =
                AuthDetails(
                    realmId = event.authDetails.realmId,
                    clientId = event.authDetails.clientId,
                    userId = event.authDetails.userId,
                    ipAddress = event.authDetails.ipAddress,
                )

            val keycloakAdminEvent =
                KeycloakAdminEvent(
                    id = UUID.randomUUID().toString(),
                    time = event.time,
                    operationType = event.operationType.name,
                    realmId = event.realmId,
                    authDetails = authDetails,
                    resourcePath = event.resourcePath,
                    representation = if (includeRepresentation) event.representation else null,
                )

            val json = objectMapper.writeValueAsString(keycloakAdminEvent)
            val key = "${event.realmId}:${event.operationType}:${event.authDetails.userId ?: "anonymous"}"

            producerManager.sendAdminEvent(key, json)

            // Record metrics
            metrics.recordEventSent(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                topic = config.adminEventTopic,
                sizeBytes = json.toByteArray().size,
            )
            metrics.stopTimer(timerSample, "ADMIN_${event.operationType.name}")

            logger.debug("Admin event sent to Kafka: type=${event.operationType}, userId=${event.authDetails.userId}")
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                topic = config.adminEventTopic,
                errorType = e.javaClass.simpleName,
            )
            logger.error("Failed to process admin event", e)
        }
    }

    override fun close() {
        // Provider는 Factory에서 관리하므로 여기서는 별도 정리 불필요
        logger.debug("KafkaEventListenerProvider closed")
    }
}
