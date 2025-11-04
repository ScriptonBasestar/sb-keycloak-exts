package org.scriptonbasestar.kcexts.events.redis

/**
 * Redis Streams event message wrapper for batch processing
 */
data class RedisEventMessage(
    val streamKey: String,
    val messageId: String?,
    val fields: Map<String, String>,
    val eventType: String,
    val realm: String,
)
