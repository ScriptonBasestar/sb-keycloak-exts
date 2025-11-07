package org.scriptonbasestar.kcexts.events.mqtt

import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.config.ConfigLoader

data class MqttEventListenerConfig(
    val brokerUrl: String,
    val clientId: String,
    val username: String? = null,
    val password: String? = null,
    val useTls: Boolean = false,
    val tlsCaCertPath: String? = null,
    val tlsClientCertPath: String? = null,
    val tlsClientKeyPath: String? = null,
    val mqttVersion: MqttVersion = MqttVersion.MQTT_3_1_1,
    val cleanSession: Boolean = true,
    val automaticReconnect: Boolean = true,
    val connectionTimeout: Int = 30,
    val keepAliveInterval: Int = 60,
    val maxInflight: Int = 10,
    val userEventTopic: String = "keycloak/events/user",
    val adminEventTopic: String = "keycloak/events/admin",
    val topicPrefix: String = "",
    val qos: Int = 1,
    val retained: Boolean = false,
    val enableLastWill: Boolean = false,
    val lastWillTopic: String? = null,
    val lastWillMessage: String? = null,
    val lastWillQos: Int = 1,
    val lastWillRetained: Boolean = true,
    val enableUserEvents: Boolean = true,
    val enableAdminEvents: Boolean = true,
    val includedEventTypes: Set<String> = emptySet(),
) {
    enum class MqttVersion(
        val protocolVersion: Int,
    ) {
        MQTT_3_1_1(4),
        MQTT_5(5),
    }

    fun getFullUserEventTopic(
        realmId: String,
        eventType: String,
    ): String = buildTopic(userEventTopic, realmId, eventType)

    fun getFullAdminEventTopic(
        realmId: String,
        operationType: String,
    ): String = buildTopic(adminEventTopic, realmId, operationType)

    fun getLastWillTopicWithClientId(): String? = lastWillTopic?.replace("{clientId}", clientId)

    private fun buildTopic(
        baseTopic: String,
        realmId: String,
        eventType: String,
    ): String {
        val base = if (topicPrefix.isNotEmpty()) "$topicPrefix/$baseTopic" else baseTopic
        return "$base/$realmId/$eventType"
    }

    companion object {
        const val CONFIG_PREFIX = "mqtt"

        fun fromRuntime(
            session: KeycloakSession,
            configScope: Config.Scope?,
        ): MqttEventListenerConfig = fromLoader(ConfigLoader.forRuntime(session, configScope, CONFIG_PREFIX))

        fun fromInit(configScope: Config.Scope): MqttEventListenerConfig =
            fromLoader(ConfigLoader.forInitTime(configScope, CONFIG_PREFIX))

        fun fromConfig(config: Map<String, String?>): MqttEventListenerConfig =
            MqttEventListenerConfig(
                brokerUrl = config.valueFor("brokerUrl", "broker.url") ?: "tcp://localhost:1883",
                clientId = config.valueFor("clientId", "client.id") ?: generateDefaultClientId(),
                username = config.valueFor("username"),
                password = config.valueFor("password"),
                useTls = config.valueFor("useTls", "use.tls")?.toBoolean() ?: false,
                tlsCaCertPath = config.valueFor("tlsCaCertPath", "tls.ca.cert.path"),
                tlsClientCertPath = config.valueFor("tlsClientCertPath", "tls.client.cert.path"),
                tlsClientKeyPath = config.valueFor("tlsClientKeyPath", "tls.client.key.path"),
                mqttVersion =
                    parseMqttVersion(
                        config.valueFor("mqttVersion", "mqtt.version"),
                    ),
                cleanSession = config.valueFor("cleanSession", "clean.session")?.toBoolean() ?: true,
                automaticReconnect = config.valueFor("automaticReconnect", "automatic.reconnect")?.toBoolean() ?: true,
                connectionTimeout =
                    config.valueFor("connectionTimeout", "connection.timeout.seconds")?.toIntOrNull() ?: 30,
                keepAliveInterval =
                    config.valueFor("keepAliveInterval", "keep.alive.interval.seconds")?.toIntOrNull() ?: 60,
                maxInflight = config.valueFor("maxInflight", "max.inflight")?.toIntOrNull() ?: 10,
                userEventTopic =
                    config.valueFor("userEventTopic", "topic.user.event") ?: "keycloak/events/user",
                adminEventTopic =
                    config.valueFor("adminEventTopic", "topic.admin.event") ?: "keycloak/events/admin",
                topicPrefix = config.valueFor("topicPrefix", "topic.prefix") ?: "",
                qos = config.valueFor("qos")?.toIntOrNull()?.coerceIn(0, 2) ?: 1,
                retained = config.valueFor("retained")?.toBoolean() ?: false,
                enableLastWill = config.valueFor("enableLastWill", "enable.last.will")?.toBoolean() ?: false,
                lastWillTopic = config.valueFor("lastWillTopic", "last.will.topic"),
                lastWillMessage = config.valueFor("lastWillMessage", "last.will.message"),
                lastWillQos = config.valueFor("lastWillQos", "last.will.qos")?.toIntOrNull()?.coerceIn(0, 2) ?: 1,
                lastWillRetained = config.valueFor("lastWillRetained", "last.will.retained")?.toBoolean() ?: true,
                enableUserEvents = config.valueFor("enableUserEvents", "enable.user.events")?.toBoolean() ?: true,
                enableAdminEvents = config.valueFor("enableAdminEvents", "enable.admin.events")?.toBoolean() ?: true,
                includedEventTypes = parseEventTypes(config.valueFor("includedEventTypes", "included.event.types")),
            )

        private fun fromLoader(loader: ConfigLoader): MqttEventListenerConfig =
            MqttEventListenerConfig(
                brokerUrl = loader.getString("broker.url", "tcp://localhost:1883") ?: "tcp://localhost:1883",
                clientId = loader.getString("client.id") ?: generateDefaultClientId(),
                username = loader.getString("username"),
                password = loader.getString("password"),
                useTls = loader.getBoolean("use.tls", false),
                tlsCaCertPath = loader.getString("tls.ca.cert.path"),
                tlsClientCertPath = loader.getString("tls.client.cert.path"),
                tlsClientKeyPath = loader.getString("tls.client.key.path"),
                mqttVersion = parseMqttVersion(loader.getString("mqtt.version")),
                cleanSession = loader.getBoolean("clean.session", true),
                automaticReconnect = loader.getBoolean("automatic.reconnect", true),
                connectionTimeout = loader.getInt("connection.timeout.seconds", 30),
                keepAliveInterval = loader.getInt("keep.alive.interval.seconds", 60),
                maxInflight = loader.getInt("max.inflight", 10),
                userEventTopic =
                    loader.getString("topic.user.event", "keycloak/events/user") ?: "keycloak/events/user",
                adminEventTopic =
                    loader.getString("topic.admin.event", "keycloak/events/admin") ?: "keycloak/events/admin",
                topicPrefix = loader.getString("topic.prefix", "") ?: "",
                qos = loader.getInt("qos", 1).coerceIn(0, 2),
                retained = loader.getBoolean("retained", false),
                enableLastWill = loader.getBoolean("enable.last.will", false),
                lastWillTopic = loader.getString("last.will.topic"),
                lastWillMessage = loader.getString("last.will.message"),
                lastWillQos = loader.getInt("last.will.qos", 1).coerceIn(0, 2),
                lastWillRetained = loader.getBoolean("last.will.retained", true),
                enableUserEvents = loader.getBoolean("enable.user.events", true),
                enableAdminEvents = loader.getBoolean("enable.admin.events", true),
                includedEventTypes = parseEventTypes(loader.getString("included.event.types")),
            )

        private fun parseEventTypes(raw: String?): Set<String> =
            raw
                ?.split(",")
                ?.map { it.trim().uppercase() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()

        private fun parseMqttVersion(raw: String?): MqttVersion =
            when (raw?.trim()?.uppercase()) {
                "3.1.1", "MQTT_3_1_1", "3" -> MqttVersion.MQTT_3_1_1
                "5", "5.0", "MQTT_5" -> MqttVersion.MQTT_5
                else -> MqttVersion.MQTT_3_1_1
            }

        private fun generateDefaultClientId(): String = "keycloak-${java.util.UUID.randomUUID()}"

        private fun Map<String, String?>.valueFor(vararg keys: String): String? =
            keys
                .asSequence()
                .mapNotNull { key -> this[key] ?: this["$CONFIG_PREFIX.$key"] }
                .firstOrNull()
    }
}
