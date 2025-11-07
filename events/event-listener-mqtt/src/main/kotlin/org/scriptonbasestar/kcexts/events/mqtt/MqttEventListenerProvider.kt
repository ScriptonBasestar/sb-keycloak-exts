package org.scriptonbasestar.kcexts.events.mqtt

import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.batch.BatchProcessor
import org.scriptonbasestar.kcexts.events.common.dlq.DeadLetterQueue
import org.scriptonbasestar.kcexts.events.common.model.AuthDetails
import org.scriptonbasestar.kcexts.events.common.model.EventMeta
import org.scriptonbasestar.kcexts.events.common.model.KeycloakAdminEvent
import org.scriptonbasestar.kcexts.events.common.model.KeycloakEvent
import org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreaker
import org.scriptonbasestar.kcexts.events.common.resilience.RetryPolicy
import org.scriptonbasestar.kcexts.events.mqtt.metrics.MqttEventMetrics
import java.util.UUID

/**
 * MQTT Event Listener Provider for Keycloak.
 *
 * Publishes Keycloak events to MQTT broker with configurable QoS and topic structure.
 */
class MqttEventListenerProvider(
    private val session: KeycloakSession,
    private val config: MqttEventListenerConfig,
    private val connectionManager: MqttConnectionManager,
    private val metrics: MqttEventMetrics,
    private val circuitBreaker: CircuitBreaker,
    private val retryPolicy: RetryPolicy,
    private val deadLetterQueue: DeadLetterQueue,
    private val batchProcessor: BatchProcessor<MqttEventMessage>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(MqttEventListenerProvider::class.java)
    private val objectMapper = ObjectMapper()

    init {
        metrics.incrementActiveSessions()
    }

    override fun onEvent(event: Event) {
        if (!config.enableUserEvents) {
            logger.debug("User events are disabled")
            return
        }

        if (config.includedEventTypes.isNotEmpty() && !config.includedEventTypes.contains(event.type.name)) {
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
            val topic = config.getFullUserEventTopic(event.realmId ?: "unknown", event.type.name)

            // Send event with resilience
            sendEventWithResilience(
                topic = topic,
                message = json,
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
            )

            // Record metrics
            metrics.recordEventSent(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = topic,
                sizeBytes = json.toByteArray().size,
            )
            metrics.stopTimer(timerSample, event.type.name)

            logger.debug("User event sent to MQTT: type=${event.type}, userId=${event.userId}, topic=$topic")
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = config.userEventTopic,
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
            val topic = config.getFullAdminEventTopic(event.realmId ?: "unknown", event.operationType.name)

            // Send event with resilience
            sendEventWithResilience(
                topic = topic,
                message = json,
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
            )

            // Record metrics
            metrics.recordEventSent(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                destination = topic,
                sizeBytes = json.toByteArray().size,
            )
            metrics.stopTimer(timerSample, "ADMIN_${event.operationType.name}")

            logger.debug(
                "Admin event sent to MQTT: operation=${event.operationType}, " +
                    "realmId=${event.realmId}, topic=$topic",
            )
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                destination = config.adminEventTopic,
                errorType = e.javaClass.simpleName,
            )
            logger.error("Failed to process admin event", e)
        }
    }

    /**
     * Send event with circuit breaker, retry policy, dead letter queue, and batch processing
     */
    private fun sendEventWithResilience(
        topic: String,
        message: String,
        eventType: String,
        realm: String,
    ) {
        val eventMessage =
            MqttEventMessage(
                topic = topic,
                message = message,
                qos = config.qos,
                retained = config.retained,
                meta =
                    EventMeta(
                        eventType = eventType,
                        realm = realm,
                    ),
            )

        // Check if batching is enabled
        if (batchProcessor.isRunning()) {
            batchProcessor.add(eventMessage)
            logger.trace("Event added to batch: type=$eventType")
            return
        }

        // Direct send with circuit breaker and retry
        try {
            circuitBreaker.execute {
                retryPolicy.execute(
                    operation = {
                        if (!connectionManager.isConnected()) {
                            throw IllegalStateException("MQTT connection is not active")
                        }
                        connectionManager.publish(topic, message, config.qos, config.retained)

                        // Record MQTT-specific metrics
                        metrics.recordMessageByQos(config.qos)
                        if (config.retained) {
                            metrics.recordRetainedMessage()
                        }

                        logger.trace(
                            "Published to MQTT: topic=$topic, qos=${config.qos}, " +
                                "retained=${config.retained}, size=${message.length}",
                        )
                    },
                    onRetry = { attempt, exception, delay ->
                        logger.warn(
                            "Retry attempt $attempt for event type=$eventType, " +
                                "delay=${delay.toMillis()}ms, error=${exception.message}",
                        )
                    },
                )
            }
        } catch (e: org.scriptonbasestar.kcexts.events.common.resilience.RetryExhaustedException) {
            logger.error("All retry attempts exhausted for event type=$eventType", e)
            addToDeadLetterQueue(eventMessage, e)
            throw e
        } catch (e: org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreakerOpenException) {
            logger.warn("Circuit breaker open, adding to DLQ: type=$eventType")
            addToDeadLetterQueue(eventMessage, e)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error sending event type=$eventType", e)
            addToDeadLetterQueue(eventMessage, e)
            throw e
        }
    }

    /**
     * Add failed event to dead letter queue
     */
    private fun addToDeadLetterQueue(
        message: MqttEventMessage,
        exception: Exception,
    ) {
        deadLetterQueue.add(
            eventType = message.meta.eventType,
            eventData = message.message,
            realm = message.meta.realm,
            destination = message.topic,
            failureReason = exception.message ?: exception.javaClass.simpleName,
            attemptCount = retryPolicy.getConfig().maxAttempts,
            metadata =
                mapOf(
                    "topic" to message.topic,
                    "qos" to message.qos.toString(),
                    "retained" to message.retained.toString(),
                    "errorClass" to exception.javaClass.simpleName,
                ),
        )
    }

    override fun close() {
        try {
            metrics.decrementActiveSessions()
            // Connection manager is shared, don't close it here
            logger.debug("Event listener provider closed")
        } catch (e: Exception) {
            logger.error("Error closing event listener provider", e)
        }
    }
}
