package org.scriptonbasestar.kcexts.events.common.test

import org.keycloak.events.Event
import org.keycloak.events.EventType
import org.keycloak.events.admin.AdminEvent
import org.keycloak.events.admin.AuthDetails
import org.keycloak.events.admin.OperationType
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Common test fixtures for creating mock Keycloak events.
 *
 * Provides reusable builders for User Events and Admin Events to reduce
 * boilerplate code in unit tests across all event listener modules.
 *
 * Usage example:
 * ```kotlin
 * val event = KeycloakEventTestFixtures.createUserEvent {
 *     type = EventType.LOGIN
 *     realmId = "my-realm"
 *     userId = "user-123"
 * }
 * ```
 */
object KeycloakEventTestFixtures {
    /**
     * Create a mock User Event with sensible defaults.
     *
     * @param type Event type (default: LOGIN)
     * @param realmId Realm identifier (default: "test-realm")
     * @param clientId Client identifier (default: "test-client")
     * @param userId User identifier (default: "test-user")
     * @param sessionId Session identifier (default: "test-session")
     * @param ipAddress Client IP address (default: "192.168.1.1")
     * @param details Event-specific details (default: empty map)
     * @return Mocked Event instance
     */
    fun createUserEvent(
        type: EventType = EventType.LOGIN,
        realmId: String = "test-realm",
        clientId: String = "test-client",
        userId: String = "test-user",
        sessionId: String = "test-session",
        ipAddress: String = "192.168.1.1",
        details: Map<String, String> = emptyMap(),
    ): Event {
        val event = mock<Event>()
        whenever(event.type).thenReturn(type)
        whenever(event.time).thenReturn(System.currentTimeMillis())
        whenever(event.realmId).thenReturn(realmId)
        whenever(event.clientId).thenReturn(clientId)
        whenever(event.userId).thenReturn(userId)
        whenever(event.sessionId).thenReturn(sessionId)
        whenever(event.ipAddress).thenReturn(ipAddress)
        whenever(event.details).thenReturn(details)
        return event
    }

    /**
     * Create a mock User Event using builder pattern.
     *
     * @param builder Lambda to configure event properties
     * @return Mocked Event instance
     */
    fun createUserEvent(builder: UserEventBuilder.() -> Unit): Event {
        val config = UserEventBuilder().apply(builder)
        return createUserEvent(
            type = config.type,
            realmId = config.realmId,
            clientId = config.clientId,
            userId = config.userId,
            sessionId = config.sessionId,
            ipAddress = config.ipAddress,
            details = config.details,
        )
    }

    /**
     * Create a mock Admin Event with sensible defaults.
     *
     * @param operationType Operation type (default: CREATE)
     * @param realmId Realm identifier (default: "test-realm")
     * @param resourcePath Resource path (default: "users/test-user-id")
     * @param representation JSON representation (default: null)
     * @param authRealmId Auth realm identifier (default: same as realmId)
     * @param authClientId Auth client identifier (default: "admin-cli")
     * @param authUserId Auth user identifier (default: "admin-user")
     * @param authIpAddress Auth IP address (default: "192.168.1.1")
     * @return Mocked AdminEvent instance
     */
    fun createAdminEvent(
        operationType: OperationType = OperationType.CREATE,
        realmId: String = "test-realm",
        resourcePath: String = "users/test-user-id",
        representation: String? = null,
        authRealmId: String = realmId,
        authClientId: String = "admin-cli",
        authUserId: String = "admin-user",
        authIpAddress: String = "192.168.1.1",
    ): AdminEvent {
        val adminEvent = mock<AdminEvent>()
        val authDetails = mock<AuthDetails>()

        whenever(authDetails.realmId).thenReturn(authRealmId)
        whenever(authDetails.clientId).thenReturn(authClientId)
        whenever(authDetails.userId).thenReturn(authUserId)
        whenever(authDetails.ipAddress).thenReturn(authIpAddress)

        whenever(adminEvent.time).thenReturn(System.currentTimeMillis())
        whenever(adminEvent.operationType).thenReturn(operationType)
        whenever(adminEvent.realmId).thenReturn(realmId)
        whenever(adminEvent.authDetails).thenReturn(authDetails)
        whenever(adminEvent.resourcePath).thenReturn(resourcePath)
        whenever(adminEvent.representation).thenReturn(representation)

        return adminEvent
    }

    /**
     * Create a mock Admin Event using builder pattern.
     *
     * @param builder Lambda to configure event properties
     * @return Mocked AdminEvent instance
     */
    fun createAdminEvent(builder: AdminEventBuilder.() -> Unit): AdminEvent {
        val config = AdminEventBuilder().apply(builder)
        return createAdminEvent(
            operationType = config.operationType,
            realmId = config.realmId,
            resourcePath = config.resourcePath,
            representation = config.representation,
            authRealmId = config.authRealmId,
            authClientId = config.authClientId,
            authUserId = config.authUserId,
            authIpAddress = config.authIpAddress,
        )
    }

    /**
     * Builder class for User Event configuration
     */
    data class UserEventBuilder(
        var type: EventType = EventType.LOGIN,
        var realmId: String = "test-realm",
        var clientId: String = "test-client",
        var userId: String = "test-user",
        var sessionId: String = "test-session",
        var ipAddress: String = "192.168.1.1",
        var details: Map<String, String> = emptyMap(),
    )

    /**
     * Builder class for Admin Event configuration
     */
    data class AdminEventBuilder(
        var operationType: OperationType = OperationType.CREATE,
        var realmId: String = "test-realm",
        var resourcePath: String = "users/test-user-id",
        var representation: String? = null,
        var authRealmId: String = realmId,
        var authClientId: String = "admin-cli",
        var authUserId: String = "admin-user",
        var authIpAddress: String = "192.168.1.1",
    )

    /**
     * Common event types for testing
     */
    object CommonEventTypes {
        val USER_EVENTS =
            listOf(
                EventType.LOGIN,
                EventType.LOGOUT,
                EventType.REGISTER,
                EventType.LOGIN_ERROR,
                EventType.UPDATE_PROFILE,
                EventType.UPDATE_PASSWORD,
            )

        val ADMIN_EVENTS =
            listOf(
                OperationType.CREATE,
                OperationType.UPDATE,
                OperationType.DELETE,
                OperationType.ACTION,
            )
    }
}
