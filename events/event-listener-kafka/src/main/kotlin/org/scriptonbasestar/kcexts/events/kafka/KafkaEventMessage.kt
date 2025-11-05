package org.scriptonbasestar.kcexts.events.kafka

import org.scriptonbasestar.kcexts.events.common.model.EventMeta

/**
 * Kafka event message wrapper for batch processing
 */
data class KafkaEventMessage(
    val key: String,
    val value: String,
    val topic: String,
    val meta: EventMeta,
)
