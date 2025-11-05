package org.scriptonbasestar.kcexts.events.common.model

/**
 * Shared metadata describing Keycloak event context.
 */
data class EventMeta(
    val eventType: String,
    val realm: String,
)
