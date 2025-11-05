package org.scriptonbasestar.kcexts.events.nats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NatsEventListenerConfigTest {
    @Test
    fun `should create config with defaults`() {
        val config =
            NatsEventListenerConfig.fromConfig(
                mapOf(
                    "nats.server.url" to null,
                ),
            )

        assertEquals("nats://localhost:4222", config.serverUrl)
        assertEquals("keycloak.events.user", config.userEventSubject)
        assertEquals("keycloak.events.admin", config.adminEventSubject)
        assertTrue(config.enableUserEvents)
        assertTrue(config.enableAdminEvents)
        assertTrue(config.includedEventTypes.isEmpty())
    }

    @Test
    fun `should parse all configuration values`() {
        val config =
            NatsEventListenerConfig.fromConfig(
                mapOf(
                    "nats.server.url" to "nats://prod-server:4222",
                    "username" to "testuser",
                    "password" to "testpass",
                    "token" to "testtoken",
                    "nats.use.tls" to "true",
                    "nats.subject.user" to "prod.events.user",
                    "nats.subject.admin" to "prod.events.admin",
                    "nats.enable.user.events" to "false",
                    "nats.enable.admin.events" to "false",
                    "nats.included.event.types" to "LOGIN,LOGOUT,REGISTER",
                    "nats.connection.timeout.ms" to "30000",
                    "nats.max.reconnects" to "10",
                    "nats.reconnect.wait.ms" to "1000",
                    "nats.no.echo" to "true",
                    "nats.max.pings.out" to "5",
                ),
            )

        assertEquals("nats://prod-server:4222", config.serverUrl)
        assertEquals("testuser", config.username)
        assertEquals("testpass", config.password)
        assertEquals("testtoken", config.token)
        assertTrue(config.useTls)
        assertEquals("prod.events.user", config.userEventSubject)
        assertEquals("prod.events.admin", config.adminEventSubject)
        assertFalse(config.enableUserEvents)
        assertFalse(config.enableAdminEvents)
        assertEquals(setOf("LOGIN", "LOGOUT", "REGISTER"), config.includedEventTypes)
        assertEquals(30000, config.connectionTimeout)
        assertEquals(10, config.maxReconnects)
        assertEquals(1000L, config.reconnectWait)
        assertTrue(config.noEcho)
        assertEquals(5, config.maxPingsOut)
    }

    @Test
    fun `should handle empty included event types`() {
        val config =
            NatsEventListenerConfig.fromConfig(
                mapOf(
                    "nats.included.event.types" to "",
                ),
            )

        assertTrue(config.includedEventTypes.isEmpty())
    }

    @Test
    fun `should handle whitespace in included event types`() {
        val config =
            NatsEventListenerConfig.fromConfig(
                mapOf(
                    "nats.included.event.types" to "  LOGIN  ,  LOGOUT  ,  REGISTER  ",
                ),
            )

        assertEquals(setOf("LOGIN", "LOGOUT", "REGISTER"), config.includedEventTypes)
    }

    @Test
    fun `should filter empty values in included event types`() {
        val config =
            NatsEventListenerConfig.fromConfig(
                mapOf(
                    "nats.included.event.types" to "LOGIN,,LOGOUT,,,REGISTER",
                ),
            )

        assertEquals(setOf("LOGIN", "LOGOUT", "REGISTER"), config.includedEventTypes)
    }

    @Test
    fun `should handle null authentication credentials`() {
        val config =
            NatsEventListenerConfig.fromConfig(
                mapOf(
                    "username" to null,
                    "password" to null,
                    "token" to null,
                ),
            )

        assertNull(config.username)
        assertNull(config.password)
        assertNull(config.token)
    }
}
