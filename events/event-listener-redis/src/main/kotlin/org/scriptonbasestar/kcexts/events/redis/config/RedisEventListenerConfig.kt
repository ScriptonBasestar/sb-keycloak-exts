package org.scriptonbasestar.kcexts.events.redis.config

import org.keycloak.Config
import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.config.ConfigLoader

/**
 * Configuration for Redis Streams Event Listener
 */
class RedisEventListenerConfig(
    session: KeycloakSession,
    configScope: Config.Scope?,
) {
    companion object {
        private const val PREFIX = "redis"
    }

    private val configLoader = ConfigLoader.forRuntime(session, configScope, PREFIX)

    val redisUri: String = configLoader.getString("uri", "redis://localhost:6379") ?: "redis://localhost:6379"
    val userEventsStream: String =
        configLoader.getString("user.events.stream", "keycloak:events:user") ?: "keycloak:events:user"
    val adminEventsStream: String =
        configLoader.getString("admin.events.stream", "keycloak:events:admin") ?: "keycloak:events:admin"
    val streamMaxLength: Long = configLoader.getLong("stream.max.length", 10000)
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
