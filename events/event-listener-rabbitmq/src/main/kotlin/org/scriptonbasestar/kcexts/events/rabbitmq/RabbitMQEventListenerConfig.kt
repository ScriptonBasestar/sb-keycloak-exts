package org.scriptonbasestar.kcexts.events.rabbitmq

import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.config.ConfigLoader

data class RabbitMQEventListenerConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val virtualHost: String = "/",
    val useSsl: Boolean = false,
    val exchangeName: String,
    val exchangeType: String = "topic",
    val exchangeDurable: Boolean = true,
    val queueDurable: Boolean = true,
    val queueAutoDelete: Boolean = false,
    val userEventRoutingKey: String = "keycloak.events.user",
    val adminEventRoutingKey: String = "keycloak.events.admin",
    val enableUserEvents: Boolean = true,
    val enableAdminEvents: Boolean = true,
    val includedEventTypes: Set<String> = emptySet(),
    val connectionTimeout: Int = 60_000,
    val requestedHeartbeat: Int = 60,
    val networkRecoveryInterval: Long = 5_000,
    val automaticRecoveryEnabled: Boolean = true,
    val publisherConfirms: Boolean = false,
    val publisherConfirmTimeout: Long = 5_000,
) {
    companion object {
        const val CONFIG_PREFIX = "rabbitmq"

        fun fromRuntime(
            session: KeycloakSession,
            configScope: Config.Scope?,
        ): RabbitMQEventListenerConfig = fromLoader(ConfigLoader.forRuntime(session, configScope, CONFIG_PREFIX))

        fun fromInit(configScope: Config.Scope): RabbitMQEventListenerConfig =
            fromLoader(ConfigLoader.forInitTime(configScope, CONFIG_PREFIX))

        fun fromConfig(config: Map<String, String?>): RabbitMQEventListenerConfig =
            RabbitMQEventListenerConfig(
                host = config.valueFor("host") ?: "localhost",
                port = config.valueFor("port")?.toIntOrNull() ?: 5672,
                username = config.valueFor("username") ?: "guest",
                password = config.valueFor("password") ?: "guest",
                virtualHost = config.valueFor("virtualHost", "virtual.host") ?: "/",
                useSsl = config.valueFor("useSsl", "use.ssl")?.toBoolean() ?: false,
                exchangeName = config.valueFor("exchangeName", "exchange.name") ?: "keycloak-events",
                exchangeType = config.valueFor("exchangeType", "exchange.type") ?: "topic",
                exchangeDurable = config.valueFor("exchangeDurable", "exchange.durable")?.toBoolean() ?: true,
                queueDurable = config.valueFor("queueDurable", "queue.durable")?.toBoolean() ?: true,
                queueAutoDelete = config.valueFor("queueAutoDelete", "queue.auto.delete")?.toBoolean() ?: false,
                userEventRoutingKey = config.valueFor("userEventRoutingKey", "routing.user") ?: "keycloak.events.user",
                adminEventRoutingKey =
                    config.valueFor("adminEventRoutingKey", "routing.admin") ?: "keycloak.events.admin",
                enableUserEvents = config.valueFor("enableUserEvents", "enable.user.events")?.toBoolean() ?: true,
                enableAdminEvents = config.valueFor("enableAdminEvents", "enable.admin.events")?.toBoolean() ?: true,
                includedEventTypes = parseEventTypes(config.valueFor("includedEventTypes", "included.event.types")),
                connectionTimeout =
                    config.valueFor("connectionTimeout", "connection.timeout.ms")?.toIntOrNull() ?: 60_000,
                requestedHeartbeat = config.valueFor("requestedHeartbeat", "requested.heartbeat")?.toIntOrNull() ?: 60,
                networkRecoveryInterval =
                    config.valueFor("networkRecoveryInterval", "network.recovery.interval.ms")?.toLongOrNull() ?: 5_000,
                automaticRecoveryEnabled =
                    config.valueFor("automaticRecoveryEnabled", "automatic.recovery.enabled")?.toBoolean() ?: true,
                publisherConfirms = config.valueFor("publisherConfirms", "publisher.confirms")?.toBoolean() ?: false,
                publisherConfirmTimeout =
                    config.valueFor("publisherConfirmTimeout", "publisher.confirm.timeout.ms")?.toLongOrNull()
                        ?: 5_000,
            )

        private fun fromLoader(loader: ConfigLoader): RabbitMQEventListenerConfig =
            RabbitMQEventListenerConfig(
                host = loader.getString("host", "localhost") ?: "localhost",
                port = loader.getInt("port", 5672),
                username = loader.getString("username", "guest") ?: "guest",
                password = loader.getString("password", "guest") ?: "guest",
                virtualHost = loader.getString("virtual.host", "/") ?: "/",
                useSsl = loader.getBoolean("use.ssl", false),
                exchangeName = loader.getString("exchange.name", "keycloak-events") ?: "keycloak-events",
                exchangeType = loader.getString("exchange.type", "topic") ?: "topic",
                exchangeDurable = loader.getBoolean("exchange.durable", true),
                queueDurable = loader.getBoolean("queue.durable", true),
                queueAutoDelete = loader.getBoolean("queue.auto.delete", false),
                userEventRoutingKey =
                    loader.getString(
                        "routing.user",
                        "keycloak.events.user",
                    ) ?: "keycloak.events.user",
                adminEventRoutingKey =
                    loader.getString("routing.admin", "keycloak.events.admin") ?: "keycloak.events.admin",
                enableUserEvents = loader.getBoolean("enable.user.events", true),
                enableAdminEvents = loader.getBoolean("enable.admin.events", true),
                includedEventTypes = parseEventTypes(loader.getString("included.event.types")),
                connectionTimeout = loader.getInt("connection.timeout.ms", 60_000),
                requestedHeartbeat = loader.getInt("requested.heartbeat", 60),
                networkRecoveryInterval = loader.getLong("network.recovery.interval.ms", 5_000),
                automaticRecoveryEnabled = loader.getBoolean("automatic.recovery.enabled", true),
                publisherConfirms = loader.getBoolean("publisher.confirms", false),
                publisherConfirmTimeout = loader.getLong("publisher.confirm.timeout.ms", 5_000),
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
