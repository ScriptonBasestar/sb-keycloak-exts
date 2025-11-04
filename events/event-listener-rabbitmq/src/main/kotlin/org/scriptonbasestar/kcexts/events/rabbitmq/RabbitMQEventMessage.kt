package org.scriptonbasestar.kcexts.events.rabbitmq

/**
 * RabbitMQ event message wrapper for batch processing
 */
data class RabbitMQEventMessage(
    val routingKey: String,
    val message: String,
    val exchange: String,
    val eventType: String,
    val realm: String,
)
