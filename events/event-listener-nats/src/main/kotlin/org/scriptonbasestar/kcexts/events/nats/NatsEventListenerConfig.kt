package org.scriptonbasestar.kcexts.events.nats

import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.config.ConfigLoader

data class NatsEventListenerConfig(
    val serverUrl: String,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val useTls: Boolean = false,
    val userEventSubject: String = "keycloak.events.user",
    val adminEventSubject: String = "keycloak.events.admin",
    val enableUserEvents: Boolean = true,
    val enableAdminEvents: Boolean = true,
    val includedEventTypes: Set<String> = emptySet(),
    val connectionTimeout: Int = 60_000,
    val maxReconnects: Int = 60,
    val reconnectWait: Long = 2_000,
    val noEcho: Boolean = false,
    val maxPingsOut: Int = 2,
) {
    companion object {
        const val CONFIG_PREFIX = "nats"

        fun fromRuntime(
            session: KeycloakSession,
            configScope: Config.Scope?,
        ): NatsEventListenerConfig = fromLoader(ConfigLoader.forRuntime(session, configScope, CONFIG_PREFIX))

        fun fromInit(configScope: Config.Scope): NatsEventListenerConfig =
            fromLoader(ConfigLoader.forInitTime(configScope, CONFIG_PREFIX))

        fun fromConfig(config: Map<String, String?>): NatsEventListenerConfig =
            NatsEventListenerConfig(
                serverUrl = config.valueFor("serverUrl", "server.url") ?: "nats://localhost:4222",
                username = config.valueFor("username"),
                password = config.valueFor("password"),
                token = config.valueFor("token"),
                useTls = config.valueFor("useTls", "use.tls")?.toBoolean() ?: false,
                userEventSubject = config.valueFor("userEventSubject", "subject.user") ?: "keycloak.events.user",
                adminEventSubject = config.valueFor("adminEventSubject", "subject.admin") ?: "keycloak.events.admin",
                enableUserEvents = config.valueFor("enableUserEvents", "enable.user.events")?.toBoolean() ?: true,
                enableAdminEvents = config.valueFor("enableAdminEvents", "enable.admin.events")?.toBoolean() ?: true,
                includedEventTypes = parseEventTypes(config.valueFor("includedEventTypes", "included.event.types")),
                connectionTimeout =
                    config.valueFor("connectionTimeout", "connection.timeout.ms")?.toIntOrNull() ?: 60_000,
                maxReconnects = config.valueFor("maxReconnects", "max.reconnects")?.toIntOrNull() ?: 60,
                reconnectWait = config.valueFor("reconnectWait", "reconnect.wait.ms")?.toLongOrNull() ?: 2_000,
                noEcho = config.valueFor("noEcho", "no.echo")?.toBoolean() ?: false,
                maxPingsOut = config.valueFor("maxPingsOut", "max.pings.out")?.toIntOrNull() ?: 2,
            )

        private fun fromLoader(loader: ConfigLoader): NatsEventListenerConfig =
            NatsEventListenerConfig(
                serverUrl = loader.getString("server.url", "nats://localhost:4222") ?: "nats://localhost:4222",
                username = loader.getString("username"),
                password = loader.getString("password"),
                token = loader.getString("token"),
                useTls = loader.getBoolean("use.tls", false),
                userEventSubject = loader.getString("subject.user", "keycloak.events.user") ?: "keycloak.events.user",
                adminEventSubject =
                    loader.getString("subject.admin", "keycloak.events.admin") ?: "keycloak.events.admin",
                enableUserEvents = loader.getBoolean("enable.user.events", true),
                enableAdminEvents = loader.getBoolean("enable.admin.events", true),
                includedEventTypes = parseEventTypes(loader.getString("included.event.types")),
                connectionTimeout = loader.getInt("connection.timeout.ms", 60_000),
                maxReconnects = loader.getInt("max.reconnects", 60),
                reconnectWait = loader.getLong("reconnect.wait.ms", 2_000),
                noEcho = loader.getBoolean("no.echo", false),
                maxPingsOut = loader.getInt("max.pings.out", 2),
            )

        private fun parseEventTypes(raw: String?): Set<String> =
            raw
                ?.split(",")
                ?.map { it.trim().uppercase() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()

        private fun Map<String, String?>.valueFor(vararg keys: String): String? =
            keys
                .asSequence()
                .mapNotNull { key -> this[key] ?: this["$CONFIG_PREFIX.$key"] }
                .firstOrNull()
    }
}
