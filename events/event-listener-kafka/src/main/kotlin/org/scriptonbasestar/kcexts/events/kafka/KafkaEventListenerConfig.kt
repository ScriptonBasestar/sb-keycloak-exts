package org.scriptonbasestar.kcexts.events.kafka

import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession

class KafkaEventListenerConfig(
    session: KeycloakSession,
) {
    val bootstrapServers: String
    val eventTopic: String
    val adminEventTopic: String
    val clientId: String
    val includedEventTypes: Set<EventType>
    val enableAdminEvents: Boolean
    val enableUserEvents: Boolean

    init {
        val realmModel = session.context.realm
        val attributes = realmModel?.attributes ?: emptyMap()

        bootstrapServers = attributes["kafka.bootstrap.servers"]
            ?: System.getProperty("kafka.bootstrap.servers", "localhost:9092")

        eventTopic = attributes["kafka.event.topic"]
            ?: System.getProperty("kafka.event.topic", "keycloak.events")

        adminEventTopic = attributes["kafka.admin.event.topic"]
            ?: System.getProperty("kafka.admin.event.topic", "keycloak.admin.events")

        clientId = attributes["kafka.client.id"]
            ?: System.getProperty("kafka.client.id", "keycloak-event-listener")

        val includedTypesStr =
            attributes["kafka.included.event.types"]
                ?: System.getProperty("kafka.included.event.types", "")

        includedEventTypes =
            if (includedTypesStr.isBlank()) {
                EventType.values().toSet()
            } else {
                includedTypesStr
                    .split(",")
                    .mapNotNull { typeName ->
                        try {
                            EventType.valueOf(typeName.trim().uppercase())
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }.toSet()
            }

        enableAdminEvents = attributes["kafka.enable.admin.events"]?.toBoolean()
            ?: System.getProperty("kafka.enable.admin.events", "true").toBoolean()

        enableUserEvents = attributes["kafka.enable.user.events"]?.toBoolean()
            ?: System.getProperty("kafka.enable.user.events", "true").toBoolean()
    }
}
