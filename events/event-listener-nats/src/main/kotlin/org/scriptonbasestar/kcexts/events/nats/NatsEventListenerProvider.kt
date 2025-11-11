package org.scriptonbasestar.kcexts.events.nats

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
import org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreakerOpenException
import org.scriptonbasestar.kcexts.events.common.resilience.RetryExhaustedException
import org.scriptonbasestar.kcexts.events.common.resilience.RetryPolicy
import org.scriptonbasestar.kcexts.events.nats.metrics.NatsEventMetrics
import java.util.UUID

class NatsEventListenerProvider(
    private val session: KeycloakSession,
    private val config: NatsEventListenerConfig,
    private val connectionManager: NatsConnectionManager,
    private val metrics: NatsEventMetrics,
    private val circuitBreaker: CircuitBreaker,
    private val retryPolicy: RetryPolicy,
    private val deadLetterQueue: DeadLetterQueue,
    private val batchProcessor: BatchProcessor<NatsEventMessage>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(NatsEventListenerProvider::class.java)
    private val objectMapper = ObjectMapper()

    override fun onEvent(event: Event) {
        if (!config.enableUserEvents) {
            logger.debug("User events are disabled")
            return
        }

        // Filter by event type if configured
        if (config.includedEventTypes.isNotEmpty() &&
            !config.includedEventTypes.contains(event.type.name)
        ) {
            logger.debug("Event type ${event.type.name} is not in included types")
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

            // Build subject: base.realmId.eventType
            val subject = "${config.userEventSubject}.${event.realmId}.${event.type.name}"

            // Send event with resilience patterns
            sendEventWithResilience(
                subject = subject,
                message = json,
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
            )

            metrics.recordEventSent(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = subject,
                sizeBytes = json.toByteArray().size,
            )
            metrics.stopTimer(timerSample, event.type.name)

            logger.debug("User event published to NATS: subject=$subject, type=${event.type}")
        } catch (e: CircuitBreakerOpenException) {
            metrics.recordEventFailed(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = config.userEventSubject,
                errorType = "CircuitBreakerOpen",
            )
            logger.warn("Circuit breaker is open, event rejected: type=${event.type}")
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = config.userEventSubject,
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

            // Build subject: base.realmId.operationType
            val subject = "${config.adminEventSubject}.${event.realmId}.${event.operationType.name}"

            // Send event with resilience patterns
            sendEventWithResilience(
                subject = subject,
                message = json,
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
            )

            metrics.recordEventSent(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                destination = subject,
                sizeBytes = json.toByteArray().size,
            )
            metrics.stopTimer(timerSample, "ADMIN_${event.operationType.name}")

            logger.debug("Admin event published to NATS: subject=$subject, type=${event.operationType}")
        } catch (e: CircuitBreakerOpenException) {
            metrics.recordEventFailed(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                destination = config.adminEventSubject,
                errorType = "CircuitBreakerOpen",
            )
            logger.warn("Circuit breaker is open, admin event rejected: type=${event.operationType}")
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                destination = config.adminEventSubject,
                errorType = e.javaClass.simpleName,
            )
            logger.error("Failed to process admin event", e)
        }
    }

    override fun close() {
        logger.debug("NatsEventListenerProvider closed")
    }

    /**
     * Send event with circuit breaker, retry policy, dead letter queue, and batch processing
     */
    private fun sendEventWithResilience(
        subject: String,
        message: String,
        eventType: String,
        realm: String,
    ) {
        val eventMessage =
            NatsEventMessage(
                subject = subject,
                message = message,
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
                        connectionManager.publish(subject, message)
                    },
                    onRetry = { attempt, exception, delay ->
                        logger.warn(
                            "Retry attempt $attempt for event type=$eventType, " +
                                "delay=${delay.toMillis()}ms, error=${exception.message}",
                        )
                    },
                )
            }
        } catch (e: RetryExhaustedException) {
            logger.error("All retry attempts exhausted for event type=$eventType", e)
            addToDeadLetterQueue(eventMessage, e)
            throw e
        } catch (e: CircuitBreakerOpenException) {
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
        message: NatsEventMessage,
        exception: Exception,
    ) {
        deadLetterQueue.add(
            eventType = message.meta.eventType,
            eventData = message.message,
            realm = message.meta.realm,
            destination = message.subject,
            failureReason = exception.message ?: exception.javaClass.simpleName,
            attemptCount = retryPolicy.getConfig().maxAttempts,
            metadata =
                mapOf(
                    "subject" to message.subject,
                    "errorClass" to exception.javaClass.simpleName,
                ),
        )
    }
}
