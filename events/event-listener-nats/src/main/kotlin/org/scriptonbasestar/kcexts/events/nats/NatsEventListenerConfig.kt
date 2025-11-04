package org.scriptonbasestar.kcexts.events.nats

data class NatsEventListenerConfig(
    // Connection Settings
    val serverUrl: String,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val useTls: Boolean = false,
    // Subject Settings
    val userEventSubject: String = "keycloak.events.user",
    val adminEventSubject: String = "keycloak.events.admin",
    // Event Filtering
    val enableUserEvents: Boolean = true,
    val enableAdminEvents: Boolean = true,
    val includedEventTypes: Set<String> = emptySet(),
    // Connection Pool Settings
    val connectionTimeout: Int = 60000,
    val maxReconnects: Int = 60,
    val reconnectWait: Long = 2000,
    // Performance Settings
    val noEcho: Boolean = false,
    val maxPingsOut: Int = 2,
) {
    companion object {
        fun fromConfig(config: Map<String, String?>): NatsEventListenerConfig =
            NatsEventListenerConfig(
                serverUrl = config["serverUrl"] ?: "nats://localhost:4222",
                username = config["username"],
                password = config["password"],
                token = config["token"],
                useTls = config["useTls"]?.toBoolean() ?: false,
                userEventSubject = config["userEventSubject"] ?: "keycloak.events.user",
                adminEventSubject = config["adminEventSubject"] ?: "keycloak.events.admin",
                enableUserEvents = config["enableUserEvents"]?.toBoolean() ?: true,
                enableAdminEvents = config["enableAdminEvents"]?.toBoolean() ?: true,
                includedEventTypes =
                    config["includedEventTypes"]
                        ?.let { types ->
                            if (types.isBlank()) {
                                emptySet()
                            } else {
                                types.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                            }
                        }
                        ?: emptySet(),
                connectionTimeout = config["connectionTimeout"]?.toIntOrNull() ?: 60000,
                maxReconnects = config["maxReconnects"]?.toIntOrNull() ?: 60,
                reconnectWait = config["reconnectWait"]?.toLongOrNull() ?: 2000,
                noEcho = config["noEcho"]?.toBoolean() ?: false,
                maxPingsOut = config["maxPingsOut"]?.toIntOrNull() ?: 2,
            )
    }
}
