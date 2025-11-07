package org.scriptonbasestar.kcexts.events.mqtt

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.common.connection.ConnectionException
import org.scriptonbasestar.kcexts.events.common.connection.EventConnectionManager
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * MQTT implementation of EventConnectionManager.
 *
 * Manages MQTT connection lifecycle and message publishing using Eclipse Paho client.
 */
class MqttConnectionManager(
    private val config: MqttEventListenerConfig,
) : EventConnectionManager {
    private val logger = Logger.getLogger(MqttConnectionManager::class.java)

    @Volatile
    private var mqttClient: MqttClient? = null

    init {
        connect()
    }

    private fun connect() {
        try {
            val persistence = MemoryPersistence()
            val client = MqttClient(config.brokerUrl, config.clientId, persistence)

            val connectionOptions = createConnectionOptions()

            // Set callback for connection monitoring
            client.setCallback(ConnectionCallback())

            client.connect(connectionOptions)
            mqttClient = client

            logger.info("Connected to MQTT broker: ${config.brokerUrl} with client ID: ${config.clientId}")
        } catch (e: MqttException) {
            logger.error("Failed to connect to MQTT broker", e)
            throw ConnectionException("Failed to connect to MQTT broker: ${config.brokerUrl}", e)
        }
    }

    private fun createConnectionOptions(): MqttConnectOptions =
        MqttConnectOptions().apply {
            // MQTT protocol version
            mqttVersion = config.mqttVersion.protocolVersion

            // Connection settings
            isCleanSession = config.cleanSession
            isAutomaticReconnect = config.automaticReconnect
            connectionTimeout = config.connectionTimeout
            keepAliveInterval = config.keepAliveInterval
            maxInflight = config.maxInflight

            // Authentication
            config.username?.let { userName = it }
            config.password?.let { password = it.toCharArray() }

            // TLS/SSL
            if (config.useTls) {
                socketFactory = createSSLSocketFactory()
            }

            // Last Will and Testament
            if (config.enableLastWill && config.lastWillTopic != null && config.lastWillMessage != null) {
                setWill(
                    config.getLastWillTopicWithClientId() ?: config.lastWillTopic,
                    config.lastWillMessage.toByteArray(Charsets.UTF_8),
                    config.lastWillQos,
                    config.lastWillRetained,
                )
                logger.info(
                    "Last Will configured: topic=${config.lastWillTopic}, " +
                        "qos=${config.lastWillQos}, retained=${config.lastWillRetained}",
                )
            }
        }

    private fun createSSLSocketFactory(): javax.net.ssl.SSLSocketFactory =
        try {
            val trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

            // Load CA certificate if provided
            val trustStore =
                if (config.tlsCaCertPath != null) {
                    KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                        load(FileInputStream(config.tlsCaCertPath), null)
                    }
                } else {
                    null
                }

            trustManagerFactory.init(trustStore)

            val keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

            // Load client certificate if provided
            val keyStore =
                if (config.tlsClientCertPath != null && config.tlsClientKeyPath != null) {
                    KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                        load(FileInputStream(config.tlsClientCertPath), null)
                    }
                } else {
                    null
                }

            keyManagerFactory.init(keyStore, null)

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(
                keyManagerFactory.keyManagers,
                trustManagerFactory.trustManagers,
                SecureRandom(),
            )

            sslContext.socketFactory
        } catch (e: Exception) {
            logger.error("Failed to create SSL socket factory", e)
            throw ConnectionException("Failed to configure TLS/SSL", e)
        }

    /**
     * Publish message to MQTT topic.
     *
     * @param topic MQTT topic
     * @param message Message content (JSON string)
     * @param qos Quality of Service level (0, 1, or 2)
     * @param retained Retain flag
     * @throws MqttException if publish fails
     */
    fun publish(
        topic: String,
        message: String,
        qos: Int = config.qos,
        retained: Boolean = config.retained,
    ) {
        val client = mqttClient ?: throw IllegalStateException("Not connected to MQTT broker")

        if (!client.isConnected) {
            logger.warn("MQTT client not connected, attempting reconnect")
            connect()
        }

        val mqttMessage =
            MqttMessage(message.toByteArray(Charsets.UTF_8)).apply {
                this.qos = qos.coerceIn(0, 2)
                isRetained = retained
            }

        client.publish(topic, mqttMessage)
        logger.debug("Published message to topic: $topic (QoS=$qos, retained=$retained)")
    }

    /**
     * Send message to specified MQTT topic (EventConnectionManager interface).
     *
     * @param destination MQTT topic
     * @param message Message content (JSON string)
     * @return true if successfully sent, false on error
     */
    override fun send(
        destination: String,
        message: String,
    ): Boolean =
        try {
            publish(destination, message)
            true
        } catch (e: Exception) {
            logger.error("Failed to send message to MQTT topic '$destination'", e)
            false
        }

    /**
     * Check if connected to MQTT broker.
     */
    override fun isConnected(): Boolean = mqttClient?.isConnected ?: false

    /**
     * Close the MQTT connection.
     */
    override fun close() {
        try {
            mqttClient?.let { client ->
                if (client.isConnected) {
                    client.disconnect()
                }
                client.close()
            }
            logger.info("MQTT connection closed")
        } catch (e: Exception) {
            logger.error("Error closing MQTT connection", e)
        } finally {
            mqttClient = null
        }
    }

    /**
     * MQTT callback for connection monitoring.
     */
    private inner class ConnectionCallback : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            logger.warn("MQTT connection lost: ${cause?.message}", cause)
            if (config.automaticReconnect) {
                logger.info("Automatic reconnect is enabled, client will attempt to reconnect")
            }
        }

        override fun messageArrived(
            topic: String?,
            message: MqttMessage?,
        ) {
            // Not subscribed to any topics, this should not be called
            logger.debug("Unexpected message arrived on topic: $topic")
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            logger.trace("Message delivery complete: ${token?.messageId}")
        }
    }
}
