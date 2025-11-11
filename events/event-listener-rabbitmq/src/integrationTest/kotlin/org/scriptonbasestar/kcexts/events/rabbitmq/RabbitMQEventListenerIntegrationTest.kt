package org.scriptonbasestar.kcexts.events.rabbitmq

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.scriptonbasestar.kcexts.events.rabbitmq.testcontainers.BaseIntegrationTest
import org.scriptonbasestar.kcexts.events.rabbitmq.testcontainers.KeycloakTestContainer
import org.scriptonbasestar.kcexts.events.rabbitmq.testcontainers.RabbitMQTestContainer
import org.slf4j.LoggerFactory
import org.testcontainers.junit.jupiter.Container

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RabbitMQEventListenerIntegrationTest : BaseIntegrationTest() {
    companion object {
        private val logger = LoggerFactory.getLogger(RabbitMQEventListenerIntegrationTest::class.java)

        @Container
        @JvmStatic
        val rabbitmqContainer = RabbitMQTestContainer()

        @Container
        @JvmStatic
        val keycloakContainer = KeycloakTestContainer()

        @JvmStatic
        @BeforeAll
        fun setupIntegrationTest() {
            logger.info("Starting RabbitMQ integration test setup...")

            // TestContainers가 자동으로 컨테이너를 시작합니다
            // Keycloak의 RabbitMQ 설정 업데이트
            keycloakContainer.updateRabbitMQConfiguration(
                rabbitmqContainer.getTestAmqpUrl(),
                RabbitMQTestContainer.USER_EVENTS_EXCHANGE,
                RabbitMQTestContainer.ADMIN_EVENTS_EXCHANGE,
            )

            logger.info("RabbitMQ integration test setup completed")
        }
    }

    @Test
    @Order(1)
    fun `should verify containers are running`() {
        assertTrue(rabbitmqContainer.isRunning, "RabbitMQ container should be running")
        assertTrue(keycloakContainer.isRunning, "Keycloak container should be running")

        logger.info("RabbitMQ AMQP URL: ${rabbitmqContainer.getTestAmqpUrl()}")
        logger.info("RabbitMQ HTTP API URL: ${rabbitmqContainer.httpApiUrl}")
        logger.info("Keycloak Auth Server URL: ${keycloakContainer.getAuthServerUrl()}")
    }

    @Test
    @Order(2)
    fun `should send and receive basic RabbitMQ messages`() {
        val testMessage = """{"test": "message-${System.currentTimeMillis()}"}"""

        // Publish message
        rabbitmqContainer.publishMessage(
            RabbitMQTestContainer.USER_EVENTS_EXCHANGE,
            RabbitMQTestContainer.ROUTING_KEY,
            testMessage,
        )

        // Consume message
        val messages = rabbitmqContainer.consumeMessages(RabbitMQTestContainer.USER_EVENTS_QUEUE, maxMessages = 1)

        assertFalse(messages.isEmpty(), "Should receive at least one message")
        assertTrue(messages[0].contains("test"), "Message should contain 'test' field")

        logger.info("Successfully sent and received RabbitMQ message")
    }

    @Test
    @Order(3)
    fun `should verify RabbitMQ exchanges and queues setup`() {
        val channel = rabbitmqContainer.createChannel()

        try {
            // Verify exchanges exist
            val userEventsExchangeDeclared =
                try {
                    channel.exchangeDeclarePassive(RabbitMQTestContainer.USER_EVENTS_EXCHANGE)
                    true
                } catch (e: Exception) {
                    false
                }
            assertTrue(userEventsExchangeDeclared, "User events exchange should exist")

            val adminEventsExchangeDeclared =
                try {
                    channel.exchangeDeclarePassive(RabbitMQTestContainer.ADMIN_EVENTS_EXCHANGE)
                    true
                } catch (e: Exception) {
                    false
                }
            assertTrue(adminEventsExchangeDeclared, "Admin events exchange should exist")

            // Verify queues exist
            val userQueueInfo = channel.queueDeclarePassive(RabbitMQTestContainer.USER_EVENTS_QUEUE)
            org.junit.jupiter.api.Assertions
                .assertNotNull(userQueueInfo, "User events queue should exist")

            val adminQueueInfo = channel.queueDeclarePassive(RabbitMQTestContainer.ADMIN_EVENTS_QUEUE)
            org.junit.jupiter.api.Assertions
                .assertNotNull(adminQueueInfo, "Admin events queue should exist")

            logger.info("RabbitMQ exchanges and queues are properly configured")
        } finally {
            channel.close()
        }
    }

    @Test
    @Order(4)
    fun `should verify Keycloak realm configuration`() {
        val realm = keycloakContainer.getAdminClient().realm(KeycloakTestContainer.TEST_REALM).toRepresentation()

        org.junit.jupiter.api.Assertions
            .assertNotNull(realm, "Test realm should exist")
        assertEquals(KeycloakTestContainer.TEST_REALM, realm.realm, "Realm name should match")

        // Event Listener 설정 확인
        assertTrue(realm.isEventsEnabled, "Events should be enabled")
        assertTrue(realm.isAdminEventsEnabled, "Admin events should be enabled")

        // RabbitMQ 설정 확인
        val attributes = realm.attributes
        kotlin.test.assertNotNull(attributes, "Realm should have attributes")
        assertEquals(
            rabbitmqContainer.getTestAmqpUrl(),
            attributes["rabbitmq.url"],
            "RabbitMQ URL should match",
        )
        assertEquals(
            RabbitMQTestContainer.USER_EVENTS_EXCHANGE,
            attributes["rabbitmq.user.events.exchange"],
            "User events exchange should match",
        )
        assertEquals(
            RabbitMQTestContainer.ADMIN_EVENTS_EXCHANGE,
            attributes["rabbitmq.admin.events.exchange"],
            "Admin events exchange should match",
        )

        logger.info("Keycloak realm is properly configured for RabbitMQ")
    }

    @Test
    @Order(5)
    fun `should send Keycloak user event to RabbitMQ`() {
        // Purge queue before test
        rabbitmqContainer.purgeQueue(RabbitMQTestContainer.USER_EVENTS_QUEUE)

        // Create a test user to generate LOGIN event
        val username = "testuser-${System.currentTimeMillis()}"
        keycloakContainer.createTestUser(username, "testpass123")

        // Wait for event to be processed
        Thread.sleep(2000)

        // Consume messages from RabbitMQ
        val messages = rabbitmqContainer.consumeMessages(RabbitMQTestContainer.USER_EVENTS_QUEUE, maxMessages = 5)

        assertTrue(messages.isNotEmpty(), "Should receive at least one event")
        logger.info("Received ${messages.size} events from RabbitMQ")

        // Verify event contains expected fields
        val firstEvent = messages[0]
        assertTrue(firstEvent.contains("\"type\""), "Event should contain 'type' field")
        assertTrue(firstEvent.contains("\"realmId\""), "Event should contain 'realmId' field")

        logger.info("Successfully sent Keycloak event to RabbitMQ: $firstEvent")
    }

    @Test
    @Order(6)
    fun `should handle basic performance test`() {
        val messageCount = 100
        val startTime = System.currentTimeMillis()

        // Publish messages
        repeat(messageCount) { index ->
            val message =
                """
                {
                    "id": "event-$index",
                    "type": "PERFORMANCE_TEST",
                    "time": ${System.currentTimeMillis()},
                    "realmId": "test-realm",
                    "userId": "user-$index"
                }
                """.trimIndent()

            rabbitmqContainer.publishMessage(
                RabbitMQTestContainer.USER_EVENTS_EXCHANGE,
                RabbitMQTestContainer.ROUTING_KEY,
                message,
            )
        }

        val sendTime = System.currentTimeMillis() - startTime
        val messagesPerSecond = (messageCount * 1000.0) / sendTime

        logger.info("Sent $messageCount messages in ${sendTime}ms (${messagesPerSecond.toInt()} msg/sec)")

        // 성능 검증: 초당 50개 이상 처리 가능해야 함
        assertTrue(messagesPerSecond >= 50, "Should process at least 50 messages per second")
    }
}

// Extension function to update RabbitMQ configuration
private fun KeycloakTestContainer.updateRabbitMQConfiguration(
    amqpUrl: String,
    userEventsExchange: String,
    adminEventsExchange: String,
) {
    val realm = getAdminClient().realm(KeycloakTestContainer.TEST_REALM).toRepresentation()

    realm.attributes = realm.attributes ?: mutableMapOf()
    realm.attributes["rabbitmq.url"] = amqpUrl
    realm.attributes["rabbitmq.user.events.exchange"] = userEventsExchange
    realm.attributes["rabbitmq.admin.events.exchange"] = adminEventsExchange
    realm.attributes["rabbitmq.enable.user.events"] = "true"
    realm.attributes["rabbitmq.enable.admin.events"] = "true"

    getAdminClient().realm(KeycloakTestContainer.TEST_REALM).update(realm)
}
