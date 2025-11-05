package org.scriptonbasestar.kcexts.events.kafka

import org.keycloak.Config
import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.config.ConfigLoader

class KafkaEventListenerConfig(
    session: KeycloakSession,
    configScope: Config.Scope?,
) {
    companion object {
        private const val PREFIX = "kafka"
    }

    private val configLoader = ConfigLoader.forRuntime(session, configScope, PREFIX)

    val bootstrapServers: String =
        configLoader.getString("bootstrap.servers", "localhost:9092") ?: "localhost:9092"
    val eventTopic: String = configLoader.getString("event.topic", "keycloak.events") ?: "keycloak.events"
    val adminEventTopic: String =
        configLoader.getString("admin.event.topic", "keycloak.admin.events") ?: "keycloak.admin.events"
    val clientId: String =
        configLoader.getString("client.id", "keycloak-event-listener") ?: "keycloak-event-listener"
    val includedEventTypes: Set<EventType> =
        configLoader
            .getString("included.event.types", "")
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { typeName ->
                try {
                    EventType.valueOf(typeName.trim().uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }
            }?.toSet()
            ?: EventType.values().toSet()
    val enableAdminEvents: Boolean = configLoader.getBoolean("enable.admin.events", true)
    val enableUserEvents: Boolean = configLoader.getBoolean("enable.user.events", true)
}
