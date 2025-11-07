package org.scriptonbasestar.kcexts.events.mqtt.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MqttEventMetricsTest {
    private lateinit var metrics: MqttEventMetrics

    @BeforeEach
    fun setup() {
        metrics = MqttEventMetrics()
    }

    @Test
    fun `should record events sent`() {
        metrics.recordEventSent(
            eventType = "LOGIN",
            realm = "master",
            destination = "keycloak/events/user/master/LOGIN",
            sizeBytes = 256,
        )

        val summary = metrics.getMetricsSummary()
        assertEquals(1L, summary.totalSent)
        assertEquals(0L, summary.totalFailed)
    }

    @Test
    fun `should record events failed`() {
        metrics.recordEventFailed(
            eventType = "LOGIN",
            realm = "master",
            destination = "keycloak/events/user/master/LOGIN",
            errorType = "MqttException",
        )

        val summary = metrics.getMetricsSummary()
        assertEquals(0L, summary.totalSent)
        assertEquals(1L, summary.totalFailed)
    }

    @Test
    fun `should accumulate multiple events`() {
        repeat(5) {
            metrics.recordEventSent(
                eventType = "LOGIN",
                realm = "master",
                destination = "keycloak/events/user/master/LOGIN",
                sizeBytes = 100,
            )
        }

        repeat(2) {
            metrics.recordEventFailed(
                eventType = "LOGOUT",
                realm = "master",
                destination = "keycloak/events/user/master/LOGOUT",
                errorType = "TimeoutException",
            )
        }

        val summary = metrics.getMetricsSummary()
        assertEquals(5L, summary.totalSent)
        assertEquals(2L, summary.totalFailed)
    }

    @Test
    fun `should track timer samples`() {
        val sample = metrics.startTimer()
        Thread.sleep(10)
        metrics.stopTimer(sample, "LOGIN")

        val summary = metrics.getMetricsSummary()
        assertTrue(summary.avgLatencyMs >= 0)
    }

    @Test
    fun `should build event type breakdown`() {
        metrics.recordEventSent("LOGIN", "master", "topic1", 100)
        metrics.recordEventSent("LOGOUT", "master", "topic2", 150)
        metrics.recordEventSent("LOGIN", "master", "topic1", 200)

        val summary = metrics.getMetricsSummary()
        assertEquals(3L, summary.totalSent)

        val breakdown = summary.eventsByType
        assertTrue(breakdown.containsKey("LOGIN:master:topic1"))
        assertTrue(breakdown.containsKey("LOGOUT:master:topic2"))
    }

    @Test
    fun `should build errors by type`() {
        metrics.recordEventFailed("LOGIN", "master", "topic", "MqttException")
        metrics.recordEventFailed("LOGOUT", "master", "topic", "TimeoutException")
        metrics.recordEventFailed("LOGIN", "master", "topic", "MqttException")

        val summary = metrics.getMetricsSummary()
        assertEquals(3L, summary.totalFailed)

        val errorsByType = summary.errorsByType
        assertTrue(errorsByType.containsKey("LOGIN:master:topic:MqttException"))
        assertTrue(errorsByType.containsKey("LOGOUT:master:topic:TimeoutException"))
    }

    @Test
    fun `should reset metrics`() {
        metrics.recordEventSent("LOGIN", "master", "topic", 100)
        metrics.recordEventFailed("LOGOUT", "master", "topic", "Error")
        metrics.recordMessageByQos(1)
        metrics.recordRetainedMessage()
        metrics.incrementActiveSessions()

        metrics.reset()

        val summary = metrics.getMetricsSummary()
        assertEquals(0L, summary.totalSent)
        assertEquals(0L, summary.totalFailed)

        val mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals(0L, mqttSummary["eventsSent"])
        assertEquals(0L, mqttSummary["eventsFailed"])
        assertEquals(0L, mqttSummary["activeSessions"])
        assertEquals(0L, mqttSummary["retainedMessages"])
    }

    @Test
    fun `should record MQTT-specific metrics - QoS tracking`() {
        metrics.recordMessageByQos(0)
        metrics.recordMessageByQos(1)
        metrics.recordMessageByQos(1)
        metrics.recordMessageByQos(2)

        val mqttSummary = metrics.getMqttMetricsSummary()
        val qosStats = mqttSummary["messagesByQos"] as Map<*, *>

        assertEquals(1L, qosStats[0])
        assertEquals(2L, qosStats[1])
        assertEquals(1L, qosStats[2])
    }

    @Test
    fun `should record MQTT-specific metrics - Retained messages`() {
        metrics.recordRetainedMessage()
        metrics.recordRetainedMessage()
        metrics.recordRetainedMessage()

        val mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals(3L, mqttSummary["retainedMessages"])
    }

    @Test
    fun `should record MQTT-specific metrics - Connection status`() {
        metrics.setConnectionStatus(true)
        var mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals("connected", mqttSummary["connectionStatus"])

        metrics.setConnectionStatus(false)
        mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals("disconnected", mqttSummary["connectionStatus"])
    }

    @Test
    fun `should record MQTT-specific metrics - Reconnect count`() {
        metrics.recordReconnect()
        metrics.recordReconnect()
        metrics.recordReconnect()

        val mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals(3L, mqttSummary["reconnectCount"])
    }

    @Test
    fun `should track active sessions`() {
        metrics.incrementActiveSessions()
        metrics.incrementActiveSessions()
        metrics.incrementActiveSessions()

        var mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals(3L, mqttSummary["activeSessions"])

        metrics.decrementActiveSessions()
        mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals(2L, mqttSummary["activeSessions"])
    }

    @Test
    fun `should calculate average event size`() {
        metrics.recordEventSent("LOGIN", "master", "topic", 100)
        metrics.recordEventSent("LOGOUT", "master", "topic", 200)
        metrics.recordEventSent("REGISTER", "master", "topic", 300)

        val mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals(200L, mqttSummary["avgEventSizeBytes"]) // (100 + 200 + 300) / 3 = 200
    }

    @Test
    fun `should return zero average size when no events`() {
        val mqttSummary = metrics.getMqttMetricsSummary()
        assertEquals(0L, mqttSummary["avgEventSizeBytes"])
    }

    @Test
    fun `should provide comprehensive MQTT metrics summary`() {
        metrics.recordEventSent("LOGIN", "master", "topic1", 100)
        metrics.recordEventFailed("LOGOUT", "master", "topic2", "Error")
        metrics.recordMessageByQos(1)
        metrics.recordRetainedMessage()
        metrics.setConnectionStatus(true)
        metrics.recordReconnect()
        metrics.incrementActiveSessions()

        val mqttSummary = metrics.getMqttMetricsSummary()

        assertEquals(1L, mqttSummary["eventsSent"])
        assertEquals(1L, mqttSummary["eventsFailed"])
        assertEquals(1L, mqttSummary["activeSessions"])
        assertEquals(100L, mqttSummary["avgEventSizeBytes"])
        assertEquals("connected", mqttSummary["connectionStatus"])
        assertEquals(1L, mqttSummary["retainedMessages"])
        assertEquals(1L, mqttSummary["reconnectCount"])

        @Suppress("UNCHECKED_CAST")
        val qosStats = mqttSummary["messagesByQos"] as Map<Int, Long>
        assertEquals(1L, qosStats[1])

        @Suppress("UNCHECKED_CAST")
        val eventsByType = mqttSummary["eventsByType"] as Map<String, Long>
        assertTrue(eventsByType.containsKey("LOGIN:master:topic1"))

        @Suppress("UNCHECKED_CAST")
        val errorsByType = mqttSummary["errorsByType"] as Map<String, Long>
        assertTrue(errorsByType.containsKey("LOGOUT:master:topic2:Error"))
    }
}
