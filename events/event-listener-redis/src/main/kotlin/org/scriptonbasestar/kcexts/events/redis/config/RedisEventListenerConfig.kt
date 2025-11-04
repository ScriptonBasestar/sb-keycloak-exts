package org.scriptonbasestar.kcexts.events.redis.config

import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession

/**
 * Configuration for Redis Streams Event Listener
 */
class RedisEventListenerConfig(
    session: KeycloakSession,
) {
    val redisUri: String
    val userEventsStream: String
    val adminEventsStream: String
    val streamMaxLength: Long
    val includedEventTypes: Set<EventType>
    val enableAdminEvents: Boolean
    val enableUserEvents: Boolean

    init {
        val realmModel = session.context.realm
        val attributes = realmModel?.attributes ?: emptyMap()

        redisUri =
            attributes["redis.uri"]
                ?: System.getProperty("redis.uri", "redis://localhost:6379")

        userEventsStream =
            attributes["redis.user.events.stream"]
                ?: System.getProperty("redis.user.events.stream", "keycloak:events:user")

        adminEventsStream =
            attributes["redis.admin.events.stream"]
                ?: System.getProperty("redis.admin.events.stream", "keycloak:events:admin")

        streamMaxLength =
            (
                attributes["redis.stream.max.length"]
                    ?: System.getProperty("redis.stream.max.length", "10000")
            ).toLong()

        val includedTypesStr =
            attributes["redis.included.event.types"]
                ?: System.getProperty("redis.included.event.types", "")

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

        enableAdminEvents =
            attributes["redis.enable.admin.events"]?.toBoolean()
                ?: System.getProperty("redis.enable.admin.events", "true").toBoolean()

        enableUserEvents =
            attributes["redis.enable.user.events"]?.toBoolean()
                ?: System.getProperty("redis.enable.user.events", "true").toBoolean()
    }
}
