package org.scriptonbasestar.kcexts.events.mqtt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MqttEventListenerConfigTest {
    @Test
    fun `should create config with defaults`() {
        val config =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.brokerUrl" to null,
                ),
            )

        assertEquals("tcp://localhost:1883", config.brokerUrl)
        assertEquals("keycloak/events/user", config.userEventTopic)
        assertEquals("keycloak/events/admin", config.adminEventTopic)
        assertEquals("", config.topicPrefix)
        assertEquals(1, config.qos)
        assertFalse(config.retained)
        assertTrue(config.enableUserEvents)
        assertTrue(config.enableAdminEvents)
        assertTrue(config.includedEventTypes.isEmpty())
        assertEquals(MqttEventListenerConfig.MqttVersion.MQTT_3_1_1, config.mqttVersion)
        assertTrue(config.cleanSession)
        assertTrue(config.automaticReconnect)
        assertEquals(30, config.connectionTimeout)
        assertEquals(60, config.keepAliveInterval)
        assertEquals(10, config.maxInflight)
    }

    @Test
    fun `should parse all configuration values`() {
        val config =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.brokerUrl" to "ssl://prod-broker:8883",
                    "mqtt.clientId" to "test-client",
                    "username" to "testuser",
                    "password" to "testpass",
                    "mqtt.useTls" to "true",
                    "mqtt.tlsCaCertPath" to "/path/to/ca.crt",
                    "mqtt.tlsClientCertPath" to "/path/to/client.crt",
                    "mqtt.tlsClientKeyPath" to "/path/to/client.key",
                    "mqtt.mqttVersion" to "5",
                    "mqtt.cleanSession" to "false",
                    "mqtt.automaticReconnect" to "false",
                    "mqtt.connectionTimeout" to "60",
                    "mqtt.keepAliveInterval" to "120",
                    "mqtt.maxInflight" to "20",
                    "mqtt.userEventTopic" to "prod/events/user",
                    "mqtt.adminEventTopic" to "prod/events/admin",
                    "mqtt.topicPrefix" to "keycloak",
                    "mqtt.qos" to "2",
                    "mqtt.retained" to "true",
                    "mqtt.enableLastWill" to "true",
                    "mqtt.lastWillTopic" to "keycloak/status/{clientId}",
                    "mqtt.lastWillMessage" to "offline",
                    "mqtt.lastWillQos" to "2",
                    "mqtt.lastWillRetained" to "false",
                    "mqtt.enableUserEvents" to "false",
                    "mqtt.enableAdminEvents" to "false",
                    "mqtt.includedEventTypes" to "LOGIN,LOGOUT,REGISTER",
                ),
            )

        assertEquals("ssl://prod-broker:8883", config.brokerUrl)
        assertEquals("test-client", config.clientId)
        assertEquals("testuser", config.username)
        assertEquals("testpass", config.password)
        assertTrue(config.useTls)
        assertEquals("/path/to/ca.crt", config.tlsCaCertPath)
        assertEquals("/path/to/client.crt", config.tlsClientCertPath)
        assertEquals("/path/to/client.key", config.tlsClientKeyPath)
        assertEquals(MqttEventListenerConfig.MqttVersion.MQTT_5, config.mqttVersion)
        assertFalse(config.cleanSession)
        assertFalse(config.automaticReconnect)
        assertEquals(60, config.connectionTimeout)
        assertEquals(120, config.keepAliveInterval)
        assertEquals(20, config.maxInflight)
        assertEquals("prod/events/user", config.userEventTopic)
        assertEquals("prod/events/admin", config.adminEventTopic)
        assertEquals("keycloak", config.topicPrefix)
        assertEquals(2, config.qos)
        assertTrue(config.retained)
        assertTrue(config.enableLastWill)
        assertEquals("keycloak/status/{clientId}", config.lastWillTopic)
        assertEquals("offline", config.lastWillMessage)
        assertEquals(2, config.lastWillQos)
        assertFalse(config.lastWillRetained)
        assertFalse(config.enableUserEvents)
        assertFalse(config.enableAdminEvents)
        assertEquals(setOf("LOGIN", "LOGOUT", "REGISTER"), config.includedEventTypes)
    }

    @Test
    fun `should parse MQTT version variants`() {
        val config311a =
            MqttEventListenerConfig.fromConfig(mapOf("mqtt.mqttVersion" to "3.1.1"))
        assertEquals(MqttEventListenerConfig.MqttVersion.MQTT_3_1_1, config311a.mqttVersion)

        val config311b =
            MqttEventListenerConfig.fromConfig(mapOf("mqtt.mqttVersion" to "MQTT_3_1_1"))
        assertEquals(MqttEventListenerConfig.MqttVersion.MQTT_3_1_1, config311b.mqttVersion)

        val config311c =
            MqttEventListenerConfig.fromConfig(mapOf("mqtt.mqttVersion" to "3"))
        assertEquals(MqttEventListenerConfig.MqttVersion.MQTT_3_1_1, config311c.mqttVersion)

        val config5a =
            MqttEventListenerConfig.fromConfig(mapOf("mqtt.mqttVersion" to "5"))
        assertEquals(MqttEventListenerConfig.MqttVersion.MQTT_5, config5a.mqttVersion)

        val config5b =
            MqttEventListenerConfig.fromConfig(mapOf("mqtt.mqttVersion" to "5.0"))
        assertEquals(MqttEventListenerConfig.MqttVersion.MQTT_5, config5b.mqttVersion)

        val config5c =
            MqttEventListenerConfig.fromConfig(mapOf("mqtt.mqttVersion" to "MQTT_5"))
        assertEquals(MqttEventListenerConfig.MqttVersion.MQTT_5, config5c.mqttVersion)
    }

    @Test
    fun `should coerce QoS to valid range`() {
        val configQos0 = MqttEventListenerConfig.fromConfig(mapOf("mqtt.qos" to "0"))
        assertEquals(0, configQos0.qos)

        val configQos1 = MqttEventListenerConfig.fromConfig(mapOf("mqtt.qos" to "1"))
        assertEquals(1, configQos1.qos)

        val configQos2 = MqttEventListenerConfig.fromConfig(mapOf("mqtt.qos" to "2"))
        assertEquals(2, configQos2.qos)

        val configQosHigh = MqttEventListenerConfig.fromConfig(mapOf("mqtt.qos" to "5"))
        assertEquals(2, configQosHigh.qos) // Should be coerced to max 2

        val configQosLow = MqttEventListenerConfig.fromConfig(mapOf("mqtt.qos" to "-1"))
        assertEquals(0, configQosLow.qos) // Should be coerced to min 0
    }

    @Test
    fun `should handle empty included event types`() {
        val config =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.includedEventTypes" to "",
                ),
            )

        assertTrue(config.includedEventTypes.isEmpty())
    }

    @Test
    fun `should handle whitespace in included event types`() {
        val config =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.includedEventTypes" to "  LOGIN  ,  LOGOUT  ,  REGISTER  ",
                ),
            )

        assertEquals(setOf("LOGIN", "LOGOUT", "REGISTER"), config.includedEventTypes)
    }

    @Test
    fun `should filter empty values in included event types`() {
        val config =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.includedEventTypes" to "LOGIN,,LOGOUT,,,REGISTER",
                ),
            )

        assertEquals(setOf("LOGIN", "LOGOUT", "REGISTER"), config.includedEventTypes)
    }

    @Test
    fun `should handle null authentication credentials`() {
        val config =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "username" to null,
                    "password" to null,
                ),
            )

        assertNull(config.username)
        assertNull(config.password)
    }

    @Test
    fun `should build full user event topic correctly`() {
        val configNoPrefix =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.topicPrefix" to "",
                    "mqtt.userEventTopic" to "events/user",
                ),
            )
        assertEquals("events/user/test-realm/LOGIN", configNoPrefix.getFullUserEventTopic("test-realm", "LOGIN"))

        val configWithPrefix =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.topicPrefix" to "keycloak",
                    "mqtt.userEventTopic" to "events/user",
                ),
            )
        assertEquals(
            "keycloak/events/user/test-realm/LOGIN",
            configWithPrefix.getFullUserEventTopic("test-realm", "LOGIN"),
        )
    }

    @Test
    fun `should build full admin event topic correctly`() {
        val configNoPrefix =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.topicPrefix" to "",
                    "mqtt.adminEventTopic" to "events/admin",
                ),
            )
        assertEquals("events/admin/test-realm/CREATE", configNoPrefix.getFullAdminEventTopic("test-realm", "CREATE"))

        val configWithPrefix =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.topicPrefix" to "keycloak",
                    "mqtt.adminEventTopic" to "events/admin",
                ),
            )
        assertEquals(
            "keycloak/events/admin/test-realm/CREATE",
            configWithPrefix.getFullAdminEventTopic("test-realm", "CREATE"),
        )
    }

    @Test
    fun `should replace clientId placeholder in last will topic`() {
        val config =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.clientId" to "test-client-123",
                    "mqtt.lastWillTopic" to "keycloak/status/{clientId}",
                ),
            )

        assertEquals("keycloak/status/test-client-123", config.getLastWillTopicWithClientId())
    }

    @Test
    fun `should return null when last will topic is not set`() {
        val config =
            MqttEventListenerConfig.fromConfig(
                mapOf(
                    "mqtt.lastWillTopic" to null,
                ),
            )

        assertNull(config.getLastWillTopicWithClientId())
    }

    @Test
    fun `should generate default client ID with UUID pattern`() {
        val config = MqttEventListenerConfig.fromConfig(emptyMap())

        assertTrue(config.clientId.startsWith("keycloak-"))
        assertTrue(config.clientId.length > 20) // UUID format: keycloak-xxxxxxxx-xxxx-...
    }
}
