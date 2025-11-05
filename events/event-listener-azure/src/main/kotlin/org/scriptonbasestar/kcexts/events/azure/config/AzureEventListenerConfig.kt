package org.scriptonbasestar.kcexts.events.azure.config

import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession

/**
 * Configuration for Azure Service Bus Event Listener
 */
class AzureEventListenerConfig(
    session: KeycloakSession,
) {
    // Azure 기본 설정
    val connectionString: String
    val useManagedIdentity: Boolean
    val fullyQualifiedNamespace: String
    val managedIdentityClientId: String?

    // Queue 설정
    val useQueue: Boolean
    val userEventsQueueName: String
    val adminEventsQueueName: String

    // Topic 설정
    val useTopic: Boolean
    val userEventsTopicName: String
    val adminEventsTopicName: String

    // 이벤트 필터링
    val includedEventTypes: Set<EventType>
    val enableAdminEvents: Boolean
    val enableUserEvents: Boolean

    init {
        val realmModel = session.context.realm
        val attributes = realmModel?.attributes ?: emptyMap()

        // Azure 기본 설정
        connectionString =
            attributes["azure.servicebus.connection.string"]
                ?: System.getProperty("azure.servicebus.connection.string", "")

        useManagedIdentity =
            (
                attributes["azure.use.managed.identity"]
                    ?: System.getProperty("azure.use.managed.identity", "false")
            ).toBoolean()

        fullyQualifiedNamespace =
            attributes["azure.servicebus.namespace"]
                ?: System.getProperty("azure.servicebus.namespace", "")

        managedIdentityClientId =
            attributes["azure.managed.identity.client.id"]
                ?: System.getProperty("azure.managed.identity.client.id")

        // Queue 설정
        useQueue =
            (
                attributes["azure.use.queue"]
                    ?: System.getProperty("azure.use.queue", "true")
            ).toBoolean()

        userEventsQueueName =
            attributes["azure.queue.user.events"]
                ?: System.getProperty("azure.queue.user.events", "keycloak-user-events")

        adminEventsQueueName =
            attributes["azure.queue.admin.events"]
                ?: System.getProperty("azure.queue.admin.events", "keycloak-admin-events")

        // Topic 설정
        useTopic =
            (
                attributes["azure.use.topic"]
                    ?: System.getProperty("azure.use.topic", "false")
            ).toBoolean()

        userEventsTopicName =
            attributes["azure.topic.user.events"]
                ?: System.getProperty("azure.topic.user.events", "keycloak-user-events")

        adminEventsTopicName =
            attributes["azure.topic.admin.events"]
                ?: System.getProperty("azure.topic.admin.events", "keycloak-admin-events")

        // 이벤트 필터링
        val includedTypesStr =
            attributes["azure.included.event.types"]
                ?: System.getProperty("azure.included.event.types", "")

        includedEventTypes =
            if (includedTypesStr.isBlank()) {
                EventType.values().toSet()
            } else {
                includedTypesStr
                    .split(",")
                    .mapNotNull { typeName ->
                        try {
                            EventType.valueOf(typeName.trim().uppercase())
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }.toSet()
            }

        enableAdminEvents =
            attributes["azure.enable.admin.events"]?.toBoolean()
                ?: System.getProperty("azure.enable.admin.events", "true").toBoolean()

        enableUserEvents =
            attributes["azure.enable.user.events"]?.toBoolean()
                ?: System.getProperty("azure.enable.user.events", "true").toBoolean()
    }
}
