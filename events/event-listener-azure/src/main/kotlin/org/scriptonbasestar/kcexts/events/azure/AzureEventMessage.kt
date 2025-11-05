package org.scriptonbasestar.kcexts.events.azure

/**
 * Azure Service Bus event message wrapper for batch processing
 */
data class AzureEventMessage(
    val senderKey: String,
    val messageBody: String,
    val queueName: String?,
    val topicName: String?,
    val properties: Map<String, String>,
    val eventType: String,
    val realm: String,
    val isAdminEvent: Boolean,
)
