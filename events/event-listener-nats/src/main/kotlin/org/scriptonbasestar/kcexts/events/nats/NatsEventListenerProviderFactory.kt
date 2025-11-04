package org.scriptonbasestar.kcexts.events.nats

import org.jboss.logging.Logger
import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.scriptonbasestar.kcexts.events.nats.metrics.NatsEventMetrics
import java.util.concurrent.ConcurrentHashMap

class NatsEventListenerProviderFactory : EventListenerProviderFactory {
    private val logger = Logger.getLogger(NatsEventListenerProviderFactory::class.java)
    private val connectionManagers = ConcurrentHashMap<String, NatsConnectionManager>()

    private lateinit var metrics: NatsEventMetrics
    private lateinit var defaultConfig: NatsEventListenerConfig

    override fun create(session: KeycloakSession): EventListenerProvider =
        try {
            val connectionManager = getOrCreateConnectionManager(defaultConfig)
            NatsEventListenerProvider(session, defaultConfig, connectionManager, metrics)
        } catch (e: Exception) {
            logger.error("Failed to create NatsEventListenerProvider", e)
            throw e
        }

    private fun getOrCreateConnectionManager(config: NatsEventListenerConfig): NatsConnectionManager {
        val key = config.serverUrl
        return connectionManagers.computeIfAbsent(key) {
            logger.info("Creating new NatsConnectionManager for server: $key")
            NatsConnectionManager(config)
        }
    }

    override fun init(config: Config.Scope) {
        logger.info("Initializing NatsEventListenerProviderFactory")

        // Load configuration
        val configMap =
            mapOf(
                "serverUrl" to config.get("serverUrl"),
                "username" to config.get("username"),
                "password" to config.get("password"),
                "token" to config.get("token"),
                "useTls" to config.get("useTls"),
                "userEventSubject" to config.get("userEventSubject"),
                "adminEventSubject" to config.get("adminEventSubject"),
                "enableUserEvents" to config.get("enableUserEvents"),
                "enableAdminEvents" to config.get("enableAdminEvents"),
                "includedEventTypes" to config.get("includedEventTypes"),
                "connectionTimeout" to config.get("connectionTimeout"),
                "maxReconnects" to config.get("maxReconnects"),
                "reconnectWait" to config.get("reconnectWait"),
                "noEcho" to config.get("noEcho"),
                "maxPingsOut" to config.get("maxPingsOut"),
            )

        defaultConfig = NatsEventListenerConfig.fromConfig(configMap)

        logger.info(
            "Configuration loaded - server: ${defaultConfig.serverUrl}, " +
                "userSubject: ${defaultConfig.userEventSubject}, " +
                "adminSubject: ${defaultConfig.adminEventSubject}",
        )

        // Initialize metrics
        metrics = NatsEventMetrics()
        logger.info("NATS metrics collection enabled")
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        logger.info("Post-initialization of NatsEventListenerProviderFactory completed")
    }

    override fun close() {
        logger.info("Closing NatsEventListenerProviderFactory")

        connectionManagers.values.forEach { manager ->
            try {
                manager.close()
            } catch (e: Exception) {
                logger.error("Error closing NatsConnectionManager", e)
            }
        }
        connectionManagers.clear()

        logger.info("NatsEventListenerProviderFactory closed successfully")
    }

    override fun getId(): String = "nats-event-listener"
}
