package org.scriptonbasestar.kcexts.events.azure

import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.azure.config.AzureEventListenerConfig
import org.scriptonbasestar.kcexts.events.azure.metrics.AzureEventMetrics
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
import java.util.UUID

/**
 * Azure Service Bus Event Listener Provider
 */
class AzureEventListenerProvider(
    private val session: KeycloakSession,
    private val config: AzureEventListenerConfig,
    private val sender: AzureConnectionManager,
    private val senderKey: String,
    private val metrics: AzureEventMetrics,
    private val circuitBreaker: CircuitBreaker,
    private val retryPolicy: RetryPolicy,
    private val deadLetterQueue: DeadLetterQueue,
    private val batchProcessor: BatchProcessor<AzureEventMessage>,
) : EventListenerProvider {
    private val logger = Logger.getLogger(AzureEventListenerProvider::class.java)
    private val objectMapper = ObjectMapper()

    override fun onEvent(event: Event) {
        if (!config.enableUserEvents) {
            logger.debug("User events are disabled")
            return
        }

        if (!config.useQueue && !config.useTopic) {
            logger.warn("No destination configured for user events")
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
            val properties =
                mapOf(
                    "eventType" to event.type.name,
                    "realmId" to (event.realmId ?: ""),
                    "userId" to (event.userId ?: ""),
                )

            sendEventWithResilience(
                messageBody = json,
                properties = properties,
                isAdminEvent = false,
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
            )
            metrics.stopTimer(timerSample, event.type.name)

            logger.debug("User event sent to Azure Service Bus: type=${event.type}, userId=${event.userId}")
        } catch (e: CircuitBreakerOpenException) {
            recordFailure(event.type.name, event.realmId ?: "unknown", e)
            logger.warn("Circuit breaker is open, event rejected: type=${event.type}")
        } catch (e: Exception) {
            recordFailure(event.type.name, event.realmId ?: "unknown", e)
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

        if (!config.useQueue && !config.useTopic) {
            logger.warn("No destination configured for admin events")
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
            val properties =
                mapOf(
                    "operationType" to event.operationType.name,
                    "realmId" to (event.realmId ?: ""),
                    "userId" to (event.authDetails.userId ?: ""),
                )

            val eventType = "ADMIN_${event.operationType.name}"

            sendEventWithResilience(
                messageBody = json,
                properties = properties,
                isAdminEvent = true,
                eventType = eventType,
                realm = event.realmId ?: "unknown",
            )
            metrics.stopTimer(timerSample, eventType)

            logger.debug(
                "Admin event sent to Azure Service Bus: type=${event.operationType}, userId=${event.authDetails.userId}",
            )
        } catch (e: CircuitBreakerOpenException) {
            recordFailure("ADMIN_${event.operationType.name}", event.realmId ?: "unknown", e)
            logger.warn("Circuit breaker is open, admin event rejected: type=${event.operationType}")
        } catch (e: Exception) {
            recordFailure("ADMIN_${event.operationType.name}", event.realmId ?: "unknown", e)
            logger.error("Failed to process admin event", e)
        }
    }

    override fun close() {
        logger.debug("AzureEventListenerProvider closed")
    }

    private fun sendEventWithResilience(
        messageBody: String,
        properties: Map<String, String>,
        isAdminEvent: Boolean,
        eventType: String,
        realm: String,
    ) {
        val payloadSize = messageBody.toByteArray().size
        val destinationMessages =
            buildList {
                if (config.useQueue) {
                    add(
                        AzureEventMessage(
                            senderKey = senderKey,
                            messageBody = messageBody,
                            queueName = if (isAdminEvent) config.adminEventsQueueName else config.userEventsQueueName,
                            topicName = null,
                            properties = properties,
                            meta =
                                EventMeta(
                                    eventType = eventType,
                                    realm = realm,
                                ),
                            isAdminEvent = isAdminEvent,
                        ),
                    )
                }
                if (config.useTopic) {
                    add(
                        AzureEventMessage(
                            senderKey = senderKey,
                            messageBody = messageBody,
                            queueName = null,
                            topicName = if (isAdminEvent) config.adminEventsTopicName else config.userEventsTopicName,
                            properties = properties,
                            meta =
                                EventMeta(
                                    eventType = eventType,
                                    realm = realm,
                                ),
                            isAdminEvent = isAdminEvent,
                        ),
                    )
                }
            }

        if (destinationMessages.isEmpty()) {
            logger.warn("Skipping event $eventType - no Azure Service Bus destination configured")
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
                            sendDirect(message)
                        },
                    )
                }
                metrics.recordEventSent(
                    eventType = eventType,
                    realm = realm,
                    destination = formatDestination(message),
                    sizeBytes = payloadSize,
                )
                metrics.updateConnectionStatus(sender.isConnected())
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

    private fun sendDirect(message: AzureEventMessage) {
        var delivered = false
        message.queueName?.let { queue ->
            sender.sendToQueue(queue, message.messageBody, message.properties)
            delivered = true
        }
        message.topicName?.let { topic ->
            sender.sendToTopic(topic, message.messageBody, message.properties)
            delivered = true
        }
        if (!delivered) {
            logger.warn("No destination configured for message: ${message.meta.eventType}")
        }
    }

    private fun addToDeadLetterQueue(
        message: AzureEventMessage,
        exception: Exception,
    ) {
        val destination = message.queueName ?: message.topicName ?: "unknown"
        deadLetterQueue.add(
            eventType = message.meta.eventType,
            eventData = message.messageBody,
            realm = message.meta.realm,
            destination = destination,
            failureReason = exception.message ?: exception.javaClass.simpleName,
            attemptCount = retryPolicy.getConfig().maxAttempts,
            metadata =
                buildMap {
                    put("isAdminEvent", message.isAdminEvent.toString())
                    put("senderKey", message.senderKey)
                    message.properties.forEach { (key, value) ->
                        put(key, value)
                    }
                },
        )
        metrics.recordEventFailed(
            eventType = message.meta.eventType,
            realm = message.meta.realm,
            destination =
                if (message.queueName != null) {
                    "queue:${message.queueName}"
                } else {
                    "topic:${message.topicName ?: "unknown"}"
                },
            errorType = exception.javaClass.simpleName,
        )
        metrics.updateConnectionStatus(sender.isConnected())
    }

    private fun recordFailure(
        eventType: String,
        realm: String,
        exception: Exception,
    ) {
        val destination =
            if (config.useQueue) {
                if (eventType.startsWith("ADMIN_")) {
                    "queue:${config.adminEventsQueueName}"
                } else {
                    "queue:${config.userEventsQueueName}"
                }
            } else {
                if (eventType.startsWith("ADMIN_")) {
                    "topic:${config.adminEventsTopicName}"
                } else {
                    "topic:${config.userEventsTopicName}"
                }
            }

        metrics.recordEventFailed(
            eventType = eventType,
            realm = realm,
            destination = destination,
            errorType = exception.javaClass.simpleName,
        )
        metrics.updateConnectionStatus(sender.isConnected())
    }

    private fun formatDestination(message: AzureEventMessage): String =
        when {
            message.queueName != null -> "queue:${message.queueName}"
            message.topicName != null -> "topic:${message.topicName}"
            else -> "unknown"
        }
}
