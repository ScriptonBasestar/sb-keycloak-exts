package org.scriptonbasestar.kcexts.events.azure.config

import org.keycloak.Config
import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.config.ConfigLoader

/**
 * Configuration for Azure Service Bus Event Listener
 */
class AzureEventListenerConfig(
    session: KeycloakSession,
    configScope: Config.Scope?,
) {
    companion object {
        private const val PREFIX = "azure"
    }

    private val configLoader = ConfigLoader.forRuntime(session, configScope, PREFIX)

    // Azure 기본 설정
    val connectionString: String = configLoader.getString("servicebus.connection.string", "") ?: ""
    val useManagedIdentity: Boolean = configLoader.getBoolean("use.managed.identity", false)
    val fullyQualifiedNamespace: String = configLoader.getString("servicebus.namespace", "") ?: ""
    val managedIdentityClientId: String? = configLoader.getString("managed.identity.client.id")

    // Queue 설정
    val useQueue: Boolean = configLoader.getBoolean("use.queue", true)
    val userEventsQueueName: String = configLoader.getString("queue.user.events", "keycloak-user-events") ?: "keycloak-user-events"
    val adminEventsQueueName: String = configLoader.getString("queue.admin.events", "keycloak-admin-events") ?: "keycloak-admin-events"

    // Topic 설정
    val useTopic: Boolean = configLoader.getBoolean("use.topic", false)
    val userEventsTopicName: String = configLoader.getString("topic.user.events", "keycloak-user-events") ?: "keycloak-user-events"
    val adminEventsTopicName: String = configLoader.getString("topic.admin.events", "keycloak-admin-events") ?: "keycloak-admin-events"

    // 이벤트 필터링
    val includedEventTypes: Set<EventType> =
        configLoader
            .getString("included.event.types", "")
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { typeName ->
                try {
                    EventType.valueOf(typeName.trim().uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }
            }?.toSet()
            ?: EventType.values().toSet()

    val enableAdminEvents: Boolean = configLoader.getBoolean("enable.admin.events", true)
    val enableUserEvents: Boolean = configLoader.getBoolean("enable.user.events", true)
}
