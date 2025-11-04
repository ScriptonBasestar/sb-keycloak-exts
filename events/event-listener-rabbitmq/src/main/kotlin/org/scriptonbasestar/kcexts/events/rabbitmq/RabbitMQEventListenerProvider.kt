package org.scriptonbasestar.kcexts.events.rabbitmq

import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.batch.BatchProcessor
import org.scriptonbasestar.kcexts.events.common.dlq.DeadLetterQueue
import org.scriptonbasestar.kcexts.events.common.model.AuthDetails
import org.scriptonbasestar.kcexts.events.common.model.KeycloakAdminEvent
import org.scriptonbasestar.kcexts.events.common.model.KeycloakEvent
import org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreaker
import org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreakerOpenException
import org.scriptonbasestar.kcexts.events.common.resilience.RetryExhaustedException
import org.scriptonbasestar.kcexts.events.common.resilience.RetryPolicy
import org.scriptonbasestar.kcexts.events.rabbitmq.metrics.RabbitMQEventMetrics
import java.util.UUID

class RabbitMQEventListenerProvider(
    private val session: KeycloakSession,
    private val config: RabbitMQEventListenerConfig,
    private val connectionManager: RabbitMQConnectionManager,
    private val metrics: RabbitMQEventMetrics,
    private val circuitBreaker: CircuitBreaker,
    private val retryPolicy: RetryPolicy,
    private val deadLetterQueue: DeadLetterQueue,
    private val batchProcessor: BatchProcessor<RabbitMQEventMessage>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(RabbitMQEventListenerProvider::class.java)
    private val objectMapper = ObjectMapper()

    override fun onEvent(event: Event) {
        if (!config.enableUserEvents) {
            logger.debug("User events are disabled")
            return
        }

        if (config.includedEventTypes.isNotEmpty() &&
            !config.includedEventTypes.contains(event.type.name)
        ) {
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
            val routingKey = "${config.userEventRoutingKey}.${event.realmId}.${event.type.name}"

            // Send event with resilience patterns
            sendEventWithResilience(
                routingKey = routingKey,
                message = json,
                exchange = config.exchangeName,
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
            )

            // Record metrics
            metrics.recordEventSent(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = config.exchangeName,
                sizeBytes = json.toByteArray().size,
            )
            metrics.stopTimer(timerSample, event.type.name)

            logger.debug("User event sent to RabbitMQ: type=${event.type}, userId=${event.userId}")
        } catch (e: CircuitBreakerOpenException) {
            metrics.recordEventFailed(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = config.exchangeName,
                errorType = "CircuitBreakerOpen",
            )
            logger.warn("Circuit breaker is open, event rejected: type=${event.type}")
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = config.exchangeName,
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
            val routingKey = "${config.adminEventRoutingKey}.${event.realmId}.${event.operationType.name}"

            // Send event with resilience patterns
            sendEventWithResilience(
                routingKey = routingKey,
                message = json,
                exchange = config.exchangeName,
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
            )

            // Record metrics
            metrics.recordEventSent(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                destination = config.exchangeName,
                sizeBytes = json.toByteArray().size,
            )
            metrics.stopTimer(timerSample, "ADMIN_${event.operationType.name}")

            logger.debug(
                "Admin event sent to RabbitMQ: type=${event.operationType}, userId=${event.authDetails.userId}",
            )
        } catch (e: CircuitBreakerOpenException) {
            metrics.recordEventFailed(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                destination = config.exchangeName,
                errorType = "CircuitBreakerOpen",
            )
            logger.warn("Circuit breaker is open, admin event rejected: type=${event.operationType}")
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
                destination = config.exchangeName,
                errorType = e.javaClass.simpleName,
            )
            logger.error("Failed to process admin event", e)
        }
    }

    override fun close() {
        // Provider는 Factory에서 관리하므로 여기서는 별도 정리 불필요
        logger.debug("RabbitMQEventListenerProvider closed")
    }

    /**
     * Send event with circuit breaker, retry policy, dead letter queue, and batch processing
     */
    private fun sendEventWithResilience(
        routingKey: String,
        message: String,
        exchange: String,
        eventType: String,
        realm: String,
    ) {
        val eventMessage =
            RabbitMQEventMessage(
                routingKey = routingKey,
                message = message,
                exchange = exchange,
                eventType = eventType,
                realm = realm,
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
                        connectionManager.publishMessage(routingKey, message)
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
        message: RabbitMQEventMessage,
        exception: Exception,
    ) {
        deadLetterQueue.add(
            eventType = message.eventType,
            eventData = message.message,
            realm = message.realm,
            destination = message.exchange,
            failureReason = exception.message ?: exception.javaClass.simpleName,
            attemptCount = retryPolicy.getConfig().maxAttempts,
            metadata =
                mapOf(
                    "routingKey" to message.routingKey,
                    "errorClass" to exception.javaClass.simpleName,
                ),
        )
    }
}
