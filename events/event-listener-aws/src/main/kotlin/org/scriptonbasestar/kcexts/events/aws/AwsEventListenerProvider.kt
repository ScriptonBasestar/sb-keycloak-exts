package org.scriptonbasestar.kcexts.events.aws

import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.aws.config.AwsEventListenerConfig
import org.scriptonbasestar.kcexts.events.aws.metrics.AwsEventMetrics
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
import java.util.*

/**
 * AWS SQS/SNS Event Listener Provider
 */
class AwsEventListenerProvider(
    private val session: KeycloakSession,
    private val config: AwsEventListenerConfig,
    private val connectionManager: AwsConnectionManager,
    private val metrics: AwsEventMetrics,
    private val circuitBreaker: CircuitBreaker,
    private val retryPolicy: RetryPolicy,
    private val deadLetterQueue: DeadLetterQueue,
    private val batchProcessor: BatchProcessor<AwsEventMessage>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(AwsEventListenerProvider::class.java)
    private val objectMapper = ObjectMapper()

    override fun onEvent(event: Event) {
        if (!config.enableUserEvents) return
        if (!config.includedEventTypes.contains(event.type)) return

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
            val attributes =
                mapOf(
                    "eventType" to event.type.name,
                    "realmId" to (event.realmId ?: ""),
                    "userId" to (event.userId ?: ""),
                )

            sendEventWithResilience(
                messageBody = json,
                attributes = attributes,
                isAdminEvent = false,
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
            )

            metrics.stopTimer(timerSample, event.type.name)
        } catch (e: CircuitBreakerOpenException) {
            logger.warn("Circuit breaker is open, event rejected: type=${event.type}")
        } catch (e: Exception) {
            logger.error("Failed to process user event", e)
        }
    }

    override fun onEvent(
        event: AdminEvent,
        includeRepresentation: Boolean,
    ) {
        if (!config.enableAdminEvents) return

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
            val attributes =
                mapOf(
                    "operationType" to event.operationType.name,
                    "realmId" to (event.realmId ?: ""),
                    "userId" to (event.authDetails.userId ?: ""),
                )

            sendEventWithResilience(
                messageBody = json,
                attributes = attributes,
                isAdminEvent = true,
                eventType = "ADMIN_${event.operationType.name}",
                realm = event.realmId ?: "unknown",
            )

            metrics.stopTimer(timerSample, "ADMIN_${event.operationType.name}")
        } catch (e: CircuitBreakerOpenException) {
            logger.warn("Circuit breaker is open, admin event rejected")
        } catch (e: Exception) {
            logger.error("Failed to process admin event", e)
        }
    }

    override fun close() {
        logger.debug("AwsEventListenerProvider closed")
    }

    private fun sendEventWithResilience(
        messageBody: String,
        attributes: Map<String, String>,
        isAdminEvent: Boolean,
        eventType: String,
        realm: String,
    ) {
        val payloadSize = messageBody.toByteArray().size
        val destinationMessages =
            buildList {
                if (config.useSqs) {
                    add(
                        AwsEventMessage(
                            messageBody = messageBody,
                            queueUrl = if (isAdminEvent) config.sqsAdminEventsQueueUrl else config.sqsUserEventsQueueUrl,
                            topicArn = null,
                            messageAttributes = attributes,
                            meta =
                                EventMeta(
                                    eventType = eventType,
                                    realm = realm,
                                ),
                        ),
                    )
                }
                if (config.useSns) {
                    add(
                        AwsEventMessage(
                            messageBody = messageBody,
                            queueUrl = null,
                            topicArn = if (isAdminEvent) config.snsAdminEventsTopicArn else config.snsUserEventsTopicArn,
                            messageAttributes = attributes,
                            meta =
                                EventMeta(
                                    eventType = eventType,
                                    realm = realm,
                                ),
                        ),
                    )
                }
            }

        if (destinationMessages.isEmpty()) {
            logger.warn("Skipping event $eventType - no AWS destination configured")
            return
        }

        var primaryException: Exception? = null
        destinationMessages.forEach { message ->
            if (batchProcessor.isRunning()) {
                batchProcessor.add(message)
                return@forEach
            }

            try {
                circuitBreaker.execute {
                    retryPolicy.execute(
                        operation = {
                            when {
                                message.queueUrl != null ->
                                    connectionManager.sendToSqs(message.queueUrl, message.messageBody, message.messageAttributes)
                                message.topicArn != null ->
                                    connectionManager.sendToSns(message.topicArn, message.messageBody, message.messageAttributes)
                                else -> logger.warn("No destination configured for message: ${message.meta.eventType}")
                            }
                        },
                    )
                }
                metrics.recordEventSent(
                    eventType = eventType,
                    realm = realm,
                    destination = formatDestination(message),
                    sizeBytes = payloadSize,
                )
            } catch (e: RetryExhaustedException) {
                addToDeadLetterQueue(message, e)
                if (primaryException == null) {
                    primaryException = e
                }
            } catch (e: CircuitBreakerOpenException) {
                addToDeadLetterQueue(message, e)
                if (primaryException == null) {
                    primaryException = e
                }
            } catch (e: Exception) {
                addToDeadLetterQueue(message, e)
                if (primaryException == null) {
                    primaryException = e
                }
            }
        }

        primaryException?.let { throw it }
    }

    private fun addToDeadLetterQueue(
        message: AwsEventMessage,
        exception: Exception,
    ) {
        val destination = formatDestination(message)
        deadLetterQueue.add(
            eventType = message.meta.eventType,
            eventData = message.messageBody,
            realm = message.meta.realm,
            destination = destination,
            failureReason = exception.message ?: exception.javaClass.simpleName,
            attemptCount = retryPolicy.getConfig().maxAttempts,
        )
        metrics.recordEventFailed(
            eventType = message.meta.eventType,
            realm = message.meta.realm,
            destination = destination,
            errorType = exception.javaClass.simpleName,
        )
    }

    private fun formatDestination(message: AwsEventMessage): String =
        when {
            message.queueUrl != null -> "queue:${message.queueUrl}"
            message.topicArn != null -> "topic:${message.topicArn}"
            else -> "unknown"
        }
}
