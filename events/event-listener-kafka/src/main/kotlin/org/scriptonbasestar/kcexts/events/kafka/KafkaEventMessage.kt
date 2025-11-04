package org.scriptonbasestar.kcexts.events.kafka

/**
 * Kafka event message wrapper for batch processing
 */
data class KafkaEventMessage(
    val key: String,
    val value: String,
    val topic: String,
    val eventType: String,
    val realm: String,
)
