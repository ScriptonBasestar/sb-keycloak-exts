package org.scriptonbasestar.kcexts.metering.model

import org.scriptonbasestar.kcexts.events.common.model.KeycloakEvent
import java.time.Instant

/**
 * User event metric for time-series storage
 *
 * Transformed from KeycloakEvent for analytics purposes
 */
data class UserEventMetric(
    val timestamp: Instant,
    val eventType: String,
    val realmId: String,
    val clientId: String?,
    val userId: String?,
    val sessionId: String?,
    val ipAddress: String?,
    val success: Boolean,
    val details: Map<String, String>,
) {
    companion object {
        fun fromKeycloakEvent(event: KeycloakEvent): UserEventMetric =
            UserEventMetric(
                timestamp = Instant.ofEpochMilli(event.time),
                eventType = event.type,
                realmId = event.realmId,
                clientId = event.clientId,
                userId = event.userId,
                sessionId = event.sessionId,
                ipAddress = event.ipAddress,
                success = !event.type.contains("ERROR"),
                details = event.details ?: emptyMap(),
            )
    }
}
