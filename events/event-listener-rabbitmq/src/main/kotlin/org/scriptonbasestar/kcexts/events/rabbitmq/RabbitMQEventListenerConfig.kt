package org.scriptonbasestar.kcexts.events.rabbitmq

data class RabbitMQEventListenerConfig(
    // Connection Settings
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val virtualHost: String = "/",
    val useSsl: Boolean = false,
    // Exchange and Queue Settings
    val exchangeName: String,
    val exchangeType: String = "topic",
    val exchangeDurable: Boolean = true,
    val queueDurable: Boolean = true,
    val queueAutoDelete: Boolean = false,
    // Routing Keys
    val userEventRoutingKey: String = "keycloak.events.user",
    val adminEventRoutingKey: String = "keycloak.events.admin",
    // Event Filtering
    val enableUserEvents: Boolean = true,
    val enableAdminEvents: Boolean = true,
    val includedEventTypes: Set<String> = emptySet(),
    // Connection Pool Settings
    val connectionTimeout: Int = 60000,
    val requestedHeartbeat: Int = 60,
    val networkRecoveryInterval: Long = 5000,
    val automaticRecoveryEnabled: Boolean = true,
    // Performance Settings
    val publisherConfirms: Boolean = false,
    val publisherConfirmTimeout: Long = 5000,
) {
    companion object {
        fun fromConfig(config: Map<String, String?>): RabbitMQEventListenerConfig =
            RabbitMQEventListenerConfig(
                host = config["host"] ?: "localhost",
                port = config["port"]?.toIntOrNull() ?: 5672,
                username = config["username"] ?: "guest",
                password = config["password"] ?: "guest",
                virtualHost = config["virtualHost"] ?: "/",
                useSsl = config["useSsl"]?.toBoolean() ?: false,
                exchangeName = config["exchangeName"] ?: "keycloak-events",
                exchangeType = config["exchangeType"] ?: "topic",
                exchangeDurable = config["exchangeDurable"]?.toBoolean() ?: true,
                queueDurable = config["queueDurable"]?.toBoolean() ?: true,
                queueAutoDelete = config["queueAutoDelete"]?.toBoolean() ?: false,
                userEventRoutingKey = config["userEventRoutingKey"] ?: "keycloak.events.user",
                adminEventRoutingKey = config["adminEventRoutingKey"] ?: "keycloak.events.admin",
                enableUserEvents = config["enableUserEvents"]?.toBoolean() ?: true,
                enableAdminEvents = config["enableAdminEvents"]?.toBoolean() ?: true,
                includedEventTypes =
                    config["includedEventTypes"]
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.toSet()
                        ?: emptySet(),
                connectionTimeout = config["connectionTimeout"]?.toIntOrNull() ?: 60000,
                requestedHeartbeat = config["requestedHeartbeat"]?.toIntOrNull() ?: 60,
                networkRecoveryInterval = config["networkRecoveryInterval"]?.toLongOrNull() ?: 5000,
                automaticRecoveryEnabled = config["automaticRecoveryEnabled"]?.toBoolean() ?: true,
                publisherConfirms = config["publisherConfirms"]?.toBoolean() ?: false,
                publisherConfirmTimeout = config["publisherConfirmTimeout"]?.toLongOrNull() ?: 5000,
            )
    }
}
