package org.scriptonbasestar.kcexts.events.rabbitmq

import org.scriptonbasestar.kcexts.events.common.model.EventMeta

/**
 * RabbitMQ event message wrapper for batch processing
 */
data class RabbitMQEventMessage(
    val routingKey: String,
    val message: String,
    val exchange: String,
    val meta: EventMeta,
)
