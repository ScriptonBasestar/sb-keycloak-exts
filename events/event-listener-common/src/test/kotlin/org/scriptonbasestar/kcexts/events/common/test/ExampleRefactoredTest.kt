package org.scriptonbasestar.kcexts.events.common.test

import org.junit.jupiter.api.Test
import org.keycloak.events.EventType
import org.keycloak.events.admin.OperationType
import org.mockito.kotlin.times

/**
 * Example test demonstrating the usage of common test utilities.
 *
 * This class shows how to refactor existing tests using:
 * - KeycloakEventTestFixtures
 * - MockConnectionManagerFactory
 * - TestConfigurationBuilders
 * - MetricsAssertions
 *
 * Compare this with NatsEventListenerProviderTest or RabbitMQEventListenerProviderTest
 * to see the reduction in boilerplate code.
 */
class ExampleRefactoredTest {
    @Test
    fun `example - create user event with defaults`() {
        // Before: 10 lines of mock setup
        // After: 1 line
        val event = KeycloakEventTestFixtures.createUserEvent()

        assert(event.type == EventType.LOGIN)
        assert(event.realmId == "test-realm")
    }

    @Test
    fun `example - create user event with custom values`() {
        // Before: 10+ lines with whenever() calls
        // After: Builder pattern
        val event =
            KeycloakEventTestFixtures.createUserEvent {
                type = EventType.REGISTER
                realmId = "my-custom-realm"
                userId = "user-123"
                details = mapOf("email" to "test@example.com")
            }

        assert(event.type == EventType.REGISTER)
        assert(event.realmId == "my-custom-realm")
        assert(event.userId == "user-123")
        assert(event.details["email"] == "test@example.com")
    }

    @Test
    fun `example - create admin event with defaults`() {
        // Before: 15+ lines of mock setup with AuthDetails
        // After: 1 line
        val adminEvent = KeycloakEventTestFixtures.createAdminEvent()

        assert(adminEvent.operationType == OperationType.CREATE)
        assert(adminEvent.realmId == "test-realm")
    }

    @Test
    fun `example - create admin event with custom values`() {
        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                operationType = OperationType.UPDATE
                realmId = "production-realm"
                resourcePath = "clients/my-client-id"
                representation = """{"clientId":"my-client"}"""
            }

        assert(adminEvent.operationType == OperationType.UPDATE)
        assert(adminEvent.representation != null)
    }

    @Test
    fun `example - create successful connection manager`() {
        // Before: Multiple whenever() calls
        // After: 1 line
        val manager = MockConnectionManagerFactory.createSuccessful()

        val result = manager.send("test-destination", "test-message")
        assert(result)
        assert(manager.isConnected())
    }

    @Test
    fun `example - create failing connection manager`() {
        val manager = MockConnectionManagerFactory.createFailing("Network timeout")

        try {
            manager.send("test-destination", "test-message")
            assert(false) { "Should have thrown exception" }
        } catch (e: RuntimeException) {
            assert(e.message == "Network timeout")
        }
    }

    @Test
    fun `example - capture sent messages`() {
        val capturedMessages = mutableListOf<Pair<String, String>>()
        val manager = MockConnectionManagerFactory.createCapturing(capturedMessages)

        manager.send("dest1", "message1")
        manager.send("dest2", "message2")

        assert(capturedMessages.size == 2)
        assert(capturedMessages[0].first == "dest1")
        assert(capturedMessages[1].second == "message2")
    }

    @Test
    fun `example - create test environment`() {
        // Before: 30+ lines creating CircuitBreaker, RetryPolicy, DeadLetterQueue, BatchProcessor
        // After: 1 line
        val env = TestConfigurationBuilders.createTestEnvironment()

        assert(env.circuitBreaker != null)
        assert(env.retryPolicy != null)
        assert(env.deadLetterQueue != null)
    }

    @Test
    fun `example - verify metrics`() {
        // Simulate metrics summary
        data class MetricsSummary(
            val totalSent: Long,
            val totalFailed: Long,
        )

        val summary = MetricsSummary(totalSent = 10, totalFailed = 0)

        // Before: Multiple assert calls
        // After: Single assertion helper
        MetricsAssertions.assertSuccessfulMetrics(summary.totalSent, summary.totalFailed, minSuccessCount = 10)
    }

    @Test
    fun `example - common event types iteration`() {
        // Test all common user event types
        KeycloakEventTestFixtures.CommonEventTypes.USER_EVENTS.forEach { eventType ->
            val event = KeycloakEventTestFixtures.createUserEvent(type = eventType)
            assert(event.type == eventType)
        }

        // Test all common admin event types
        KeycloakEventTestFixtures.CommonEventTypes.ADMIN_EVENTS.forEach { opType ->
            val adminEvent = KeycloakEventTestFixtures.createAdminEvent(operationType = opType)
            assert(adminEvent.operationType == opType)
        }
    }

    @Test
    fun `example - complete test scenario`() {
        // Setup: Create test environment
        val env = TestConfigurationBuilders.createTestEnvironment()
        val capturedMessages = mutableListOf<Pair<String, String>>()
        val connectionManager = MockConnectionManagerFactory.createCapturing(capturedMessages)

        // Create events
        val userEvent =
            KeycloakEventTestFixtures.createUserEvent {
                type = EventType.LOGIN
                userId = "test-user"
            }

        val adminEvent =
            KeycloakEventTestFixtures.createAdminEvent {
                operationType = OperationType.CREATE
                resourcePath = "users/test-user"
            }

        // Execute: Send events
        connectionManager.send("user-events", "user-event-message")
        connectionManager.send("admin-events", "admin-event-message")

        // Verify: Check captured messages
        assert(capturedMessages.size == 2)
        assert(capturedMessages[0].first == "user-events")
        assert(capturedMessages[1].first == "admin-events")
    }
}
