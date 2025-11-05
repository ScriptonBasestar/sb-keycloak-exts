package org.scriptonbasestar.kcexts.events.nats

import org.scriptonbasestar.kcexts.events.common.model.EventMeta

/**
 * NATS event message wrapper for batch processing
 */
data class NatsEventMessage(
    val subject: String,
    val message: String,
    val meta: EventMeta,
)
