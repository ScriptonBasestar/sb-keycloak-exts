package org.scriptonbasestar.kcexts.events.nats

/**
 * NATS event message wrapper for batch processing
 */
data class NatsEventMessage(
    val subject: String,
    val message: String,
    val eventType: String,
    val realm: String,
)
