package org.scriptonbasestar.kcexts.events.rabbitmq

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RabbitMQEventListenerConfigTest {
    @Test
    fun `should create config with default values`() {
        val config =
            RabbitMQEventListenerConfig.fromConfig(emptyMap())

        assertEquals("localhost", config.host)
        assertEquals(5672, config.port)
        assertEquals("guest", config.username)
        assertEquals("guest", config.password)
        assertEquals("/", config.virtualHost)
        assertEquals("keycloak-events", config.exchangeName)
        assertEquals("topic", config.exchangeType)
        assertTrue(config.exchangeDurable)
        assertTrue(config.queueDurable)
        assertFalse(config.queueAutoDelete)
        assertFalse(config.useSsl)
    }

    @Test
    fun `should create config with custom values`() {
        val configMap =
            mapOf(
                "rabbitmq.host" to "rabbitmq.example.com",
                "rabbitmq.port" to "5673",
                "rabbitmq.username" to "keycloak",
                "rabbitmq.password" to "secret",
                "rabbitmq.virtual.host" to "/production",
                "rabbitmq.use.ssl" to "true",
                "rabbitmq.exchange.name" to "custom-exchange",
                "rabbitmq.exchange.type" to "fanout",
                "rabbitmq.exchange.durable" to "false",
            )

        val config = RabbitMQEventListenerConfig.fromConfig(configMap)

        assertEquals("rabbitmq.example.com", config.host)
        assertEquals(5673, config.port)
        assertEquals("keycloak", config.username)
        assertEquals("secret", config.password)
        assertEquals("/production", config.virtualHost)
        assertTrue(config.useSsl)
        assertEquals("custom-exchange", config.exchangeName)
        assertEquals("fanout", config.exchangeType)
        assertFalse(config.exchangeDurable)
    }

    @Test
    fun `should parse routing keys correctly`() {
        val configMap =
            mapOf(
                "rabbitmq.routing.user" to "app.user.events",
                "rabbitmq.routing.admin" to "app.admin.events",
            )

        val config = RabbitMQEventListenerConfig.fromConfig(configMap)

        assertEquals("app.user.events", config.userEventRoutingKey)
        assertEquals("app.admin.events", config.adminEventRoutingKey)
    }

    @Test
    fun `should parse event filtering settings`() {
        val configMap =
            mapOf(
                "rabbitmq.enable.user.events" to "false",
                "rabbitmq.enable.admin.events" to "true",
                "rabbitmq.included.event.types" to "LOGIN,LOGOUT,REGISTER",
            )

        val config = RabbitMQEventListenerConfig.fromConfig(configMap)

        assertFalse(config.enableUserEvents)
        assertTrue(config.enableAdminEvents)
        assertEquals(setOf("LOGIN", "LOGOUT", "REGISTER"), config.includedEventTypes)
    }

    @Test
    fun `should parse connection settings`() {
        val configMap =
            mapOf(
                "rabbitmq.connection.timeout.ms" to "30000",
                "rabbitmq.requested.heartbeat" to "30",
                "rabbitmq.network.recovery.interval.ms" to "10000",
                "rabbitmq.automatic.recovery.enabled" to "false",
            )

        val config = RabbitMQEventListenerConfig.fromConfig(configMap)

        assertEquals(30000, config.connectionTimeout)
        assertEquals(30, config.requestedHeartbeat)
        assertEquals(10000, config.networkRecoveryInterval)
        assertFalse(config.automaticRecoveryEnabled)
    }

    @Test
    fun `should parse publisher confirms settings`() {
        val configMap =
            mapOf(
                "rabbitmq.publisher.confirms" to "true",
                "rabbitmq.publisher.confirm.timeout.ms" to "10000",
            )

        val config = RabbitMQEventListenerConfig.fromConfig(configMap)

        assertTrue(config.publisherConfirms)
        assertEquals(10000, config.publisherConfirmTimeout)
    }

    @Test
    fun `should handle invalid port gracefully`() {
        val configMap = mapOf("rabbitmq.port" to "invalid")

        val config = RabbitMQEventListenerConfig.fromConfig(configMap)

        assertEquals(5672, config.port) // Should use default
    }

    @Test
    fun `should handle empty included event types`() {
        val configMap = mapOf("rabbitmq.included.event.types" to "")

        val config = RabbitMQEventListenerConfig.fromConfig(configMap)

        assertTrue(config.includedEventTypes.isEmpty())
    }

    @Test
    fun `should trim whitespace in event types`() {
        val configMap = mapOf("rabbitmq.included.event.types" to " LOGIN , LOGOUT , REGISTER ")

        val config = RabbitMQEventListenerConfig.fromConfig(configMap)

        assertEquals(setOf("LOGIN", "LOGOUT", "REGISTER"), config.includedEventTypes)
    }
}
