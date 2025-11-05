package org.scriptonbasestar.kcexts.events.redis

import org.scriptonbasestar.kcexts.events.common.model.EventMeta

/**
 * Redis Streams event message wrapper for batch processing
 */
data class RedisEventMessage(
    val streamKey: String,
    val messageId: String?,
    val fields: Map<String, String>,
    val meta: EventMeta,
)
