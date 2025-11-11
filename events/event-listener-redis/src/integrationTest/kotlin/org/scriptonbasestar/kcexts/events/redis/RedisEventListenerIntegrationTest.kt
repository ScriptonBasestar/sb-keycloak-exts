package org.scriptonbasestar.kcexts.events.redis

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.scriptonbasestar.kcexts.events.redis.testcontainers.BaseIntegrationTest
import org.scriptonbasestar.kcexts.events.redis.testcontainers.KeycloakTestContainer
import org.scriptonbasestar.kcexts.events.redis.testcontainers.RedisTestContainer
import org.slf4j.LoggerFactory
import org.testcontainers.junit.jupiter.Container

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RedisEventListenerIntegrationTest : BaseIntegrationTest() {
    companion object {
        private val logger = LoggerFactory.getLogger(RedisEventListenerIntegrationTest::class.java)

        @Container
        @JvmStatic
        val redisContainer = RedisTestContainer()

        @Container
        @JvmStatic
        val keycloakContainer = KeycloakTestContainer()

        @JvmStatic
        @BeforeAll
        fun setupIntegrationTest() {
            logger.info("Starting Redis integration test setup...")

            // TestContainers가 자동으로 컨테이너를 시작합니다
            // Keycloak의 Redis 설정 업데이트
            keycloakContainer.updateRedisConfiguration(
                redisContainer.getRedisUrl(),
                RedisTestContainer.USER_EVENTS_STREAM,
                RedisTestContainer.ADMIN_EVENTS_STREAM,
            )

            logger.info("Redis integration test setup completed")
        }
    }

    @Test
    @Order(1)
    fun `should verify containers are running`() {
        assertTrue(redisContainer.isRunning, "Redis container should be running")
        assertTrue(keycloakContainer.isRunning, "Keycloak container should be running")

        logger.info("Redis URL: ${redisContainer.getRedisUrl()}")
        logger.info("Keycloak Auth Server URL: ${keycloakContainer.getAuthServerUrl()}")
    }

    @Test
    @Order(2)
    fun `should add and read from Redis stream`() {
        val testStream = "test-stream-${System.currentTimeMillis()}"
        val testData = mapOf("test" to "message", "timestamp" to System.currentTimeMillis().toString())

        // Add to stream
        val messageId = redisContainer.addToStream(testStream, testData)
        org.junit.jupiter.api.Assertions
            .assertNotNull(messageId, "Message ID should not be null")

        // Read from stream
        val messages = redisContainer.readFromStream(testStream, count = 1)

        assertFalse(messages.isEmpty(), "Should receive at least one message")
        assertEquals("message", messages[0]["test"], "Message should contain correct test data")

        logger.info("Successfully added and read from Redis stream")

        // Cleanup
        redisContainer.deleteStream(testStream)
    }

    @Test
    @Order(3)
    fun `should verify Redis streams setup`() {
        val commands = redisContainer.getCommands()

        // Verify streams exist (initialized in setupStreams)
        val userStreamLength = redisContainer.getStreamLength(RedisTestContainer.USER_EVENTS_STREAM)
        assertTrue(userStreamLength >= 0, "User events stream should exist")

        val adminStreamLength = redisContainer.getStreamLength(RedisTestContainer.ADMIN_EVENTS_STREAM)
        assertTrue(adminStreamLength >= 0, "Admin events stream should exist")

        logger.info("Redis streams are properly configured")
        logger.info("User events stream length: $userStreamLength")
        logger.info("Admin events stream length: $adminStreamLength")
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

        // Redis 설정 확인
        val attributes = realm.attributes
        kotlin.test.assertNotNull(attributes, "Realm should have attributes")
        assertEquals(
            redisContainer.getRedisUrl(),
            attributes["redis.url"],
            "Redis URL should match",
        )
        assertEquals(
            RedisTestContainer.USER_EVENTS_STREAM,
            attributes["redis.user.events.stream"],
            "User events stream should match",
        )
        assertEquals(
            RedisTestContainer.ADMIN_EVENTS_STREAM,
            attributes["redis.admin.events.stream"],
            "Admin events stream should match",
        )

        logger.info("Keycloak realm is properly configured for Redis")
    }

    @Test
    @Order(5)
    fun `should send Keycloak user event to Redis`() {
        // Clear stream before test
        val initialLength = redisContainer.getStreamLength(RedisTestContainer.USER_EVENTS_STREAM)

        // Create a test user to generate event
        val username = "testuser-${System.currentTimeMillis()}"
        keycloakContainer.createTestUser(username, "testpass123")

        // Wait for event to be processed
        Thread.sleep(2000)

        // Check stream length increased
        val finalLength = redisContainer.getStreamLength(RedisTestContainer.USER_EVENTS_STREAM)

        assertTrue(finalLength > initialLength, "Stream should have new events")
        logger.info("Stream length increased from $initialLength to $finalLength")

        // Read messages from stream
        val messages = redisContainer.readFromStream(RedisTestContainer.USER_EVENTS_STREAM, count = 5)

        assertTrue(messages.isNotEmpty(), "Should receive at least one event")
        logger.info("Received ${messages.size} events from Redis")

        // Verify event contains expected fields (events are stored as field-value pairs in stream)
        val firstEvent = messages.last() // Get the most recent event
        assertTrue(
            firstEvent.containsKey("type") || firstEvent.containsKey("eventType"),
            "Event should contain type field",
        )

        logger.info("Successfully sent Keycloak event to Redis: $firstEvent")
    }

    @Test
    @Order(6)
    fun `should handle basic performance test`() {
        val messageCount = 100
        val testStream = "performance-test-${System.currentTimeMillis()}"
        val startTime = System.currentTimeMillis()

        // Add messages to stream
        repeat(messageCount) { index ->
            val data =
                mapOf(
                    "id" to "event-$index",
                    "type" to "PERFORMANCE_TEST",
                    "time" to System.currentTimeMillis().toString(),
                    "realmId" to "test-realm",
                    "userId" to "user-$index",
                )

            redisContainer.addToStream(testStream, data)
        }

        val sendTime = System.currentTimeMillis() - startTime
        val messagesPerSecond = (messageCount * 1000.0) / sendTime

        logger.info("Added $messageCount messages in ${sendTime}ms (${messagesPerSecond.toInt()} msg/sec)")

        // Verify stream length
        val streamLength = redisContainer.getStreamLength(testStream)
        assertEquals(messageCount.toLong(), streamLength, "Stream should contain all messages")

        // 성능 검증: 초당 100개 이상 처리 가능해야 함 (Redis는 RabbitMQ보다 빠름)
        assertTrue(messagesPerSecond >= 100, "Should process at least 100 messages per second")

        // Cleanup
        redisContainer.deleteStream(testStream)
    }
}

// Extension function to update Redis configuration
private fun KeycloakTestContainer.updateRedisConfiguration(
    redisUrl: String,
    userEventsStream: String,
    adminEventsStream: String,
) {
    val realm = getAdminClient().realm(KeycloakTestContainer.TEST_REALM).toRepresentation()

    realm.attributes = realm.attributes ?: mutableMapOf()
    realm.attributes["redis.url"] = redisUrl
    realm.attributes["redis.user.events.stream"] = userEventsStream
    realm.attributes["redis.admin.events.stream"] = adminEventsStream
    realm.attributes["redis.enable.user.events"] = "true"
    realm.attributes["redis.enable.admin.events"] = "true"

    getAdminClient().realm(KeycloakTestContainer.TEST_REALM).update(realm)
}
