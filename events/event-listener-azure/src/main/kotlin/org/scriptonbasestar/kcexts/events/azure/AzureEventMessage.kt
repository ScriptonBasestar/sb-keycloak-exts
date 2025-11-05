package org.scriptonbasestar.kcexts.events.azure

import org.scriptonbasestar.kcexts.events.common.model.EventMeta

/**
 * Azure Service Bus event message wrapper for batch processing
 */
data class AzureEventMessage(
    val senderKey: String,
    val messageBody: String,
    val queueName: String?,
    val topicName: String?,
    val properties: Map<String, String>,
    val meta: EventMeta,
    val isAdminEvent: Boolean,
)
