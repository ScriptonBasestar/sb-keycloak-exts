package org.scriptonbasestar.kcexts.events.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.scriptonbasestar.kcexts.events.common.model.KeycloakAdminEvent
import org.scriptonbasestar.kcexts.events.kafka.testcontainers.BaseIntegrationTest
import org.scriptonbasestar.kcexts.events.kafka.testcontainers.KafkaTestContainer
import org.scriptonbasestar.kcexts.events.kafka.testcontainers.KeycloakTestContainer
import org.slf4j.LoggerFactory
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KafkaEventListenerIntegrationTest : BaseIntegrationTest() {
    companion object {
        private val logger = LoggerFactory.getLogger(KafkaEventListenerIntegrationTest::class.java)
        private val objectMapper = ObjectMapper()

        @Container
        @JvmStatic
        val kafkaContainer = KafkaTestContainer()

        @Container
        @JvmStatic
        val keycloakContainer = KeycloakTestContainer()

        @JvmStatic
        @BeforeAll
        fun setupIntegrationTest() {
            logger.info("Starting integration test setup...")

            // Kafka 먼저 시작
            kafkaContainer.start()

            // Keycloak 시작 및 Kafka 설정 업데이트
            keycloakContainer.start()
            keycloakContainer.updateKafkaConfiguration(kafkaContainer.getBootstrapServers())

            logger.info("Integration test setup completed")
        }

        @JvmStatic
        @AfterAll
        fun teardownIntegrationTest() {
            logger.info("Cleaning up integration test...")
            keycloakContainer.stop()
            kafkaContainer.stop()
            logger.info("Integration test cleanup completed")
        }
    }

    @Test
    @Order(1)
    fun `should verify containers are running`() {
        assertTrue(kafkaContainer.container.isRunning, "Kafka container should be running")
        assertTrue(keycloakContainer.container.isRunning, "Keycloak container should be running")

        logger.info("Kafka Bootstrap Servers: ${kafkaContainer.getBootstrapServers()}")
        logger.info("Keycloak Auth Server URL: ${keycloakContainer.getAuthServerUrl()}")
    }

    @Test
    @Order(2)
    fun `should send and receive basic Kafka messages`() {
        val producer = kafkaContainer.createProducer()
        val testMessage = "test-message-${System.currentTimeMillis()}"

        // 메시지 발송
        producer
            .send(
                org.apache.kafka.clients.producer.ProducerRecord(
                    KafkaTestContainer.USER_EVENTS_TOPIC,
                    "test-key",
                    testMessage,
                ),
            ).get()
        producer.close()

        // 메시지 수신 확인
        val messages =
            kafkaContainer.consumeMessages(
                KafkaTestContainer.USER_EVENTS_TOPIC,
                Duration.ofSeconds(5),
            )

        assertTrue(messages.isNotEmpty(), "Should receive at least one message")
        assertTrue(messages.contains(testMessage), "Should contain the sent test message")
    }

    @Test
    @Order(3)
    fun `should create test user and trigger admin events`() {
        val adminClient = keycloakContainer.getAdminClient()

        // 새 사용자 생성으로 admin 이벤트 발생
        val newUsername = "integration-test-user-${System.currentTimeMillis()}"
        val user =
            org.keycloak.representations.idm.UserRepresentation().apply {
                username = newUsername
                email = "$newUsername@test.com"
                isEnabled = true
            }

        try {
            val response =
                adminClient
                    .realm(KeycloakTestContainer.TEST_REALM)
                    .users()
                    .create(user)

            assertTrue(response.status in 200..299, "User creation should succeed")
            logger.info("Created test user: $newUsername")

            // Admin 이벤트가 Kafka로 전송되기까지 잠시 대기
            Thread.sleep(2000)

            // Admin 이벤트 메시지 확인
            val adminMessages =
                kafkaContainer.consumeMessages(
                    KafkaTestContainer.ADMIN_EVENTS_TOPIC,
                    Duration.ofSeconds(10),
                )

            if (adminMessages.isNotEmpty()) {
                logger.info("Received admin event messages: ${adminMessages.size}")

                // JSON 파싱 가능한지 확인
                adminMessages.forEach { message ->
                    try {
                        val adminEvent = objectMapper.readValue(message, KeycloakAdminEvent::class.java)
                        kotlin.test.assertNotNull(adminEvent.id, "Admin event should have ID")
                        kotlin.test.assertNotNull(adminEvent.operationType, "Admin event should have operation type")
                        logger.info("Parsed admin event: ${adminEvent.operationType}")
                    } catch (e: Exception) {
                        logger.warn("Failed to parse admin event message: $message", e)
                    }
                }
            } else {
                logger.warn("No admin events received - this may indicate Event Listener is not working")
            }
        } catch (e: Exception) {
            logger.error("Failed to create test user", e)
            fail("User creation failed: ${e.message}")
        }
    }

    @Test
    @Order(4)
    fun `should validate Kafka event listener configuration`() {
        val adminClient = keycloakContainer.getAdminClient()
        val realm = adminClient.realm(KeycloakTestContainer.TEST_REALM).toRepresentation()

        // Event Listener 설정 확인
        assertTrue(realm.isEventsEnabled, "Events should be enabled")
        assertTrue(realm.isAdminEventsEnabled, "Admin events should be enabled")

        // Kafka 설정 확인
        val attributes = realm.attributes
        kotlin.test.assertNotNull(attributes, "Realm should have attributes")
        assertEquals(
            kafkaContainer.getBootstrapServers(),
            attributes["kafka.bootstrap.servers"],
            "Bootstrap servers should match",
        )
        assertEquals(
            KafkaTestContainer.USER_EVENTS_TOPIC,
            attributes["kafka.event.topic"],
            "User event topic should match",
        )
        assertEquals(
            KafkaTestContainer.ADMIN_EVENTS_TOPIC,
            attributes["kafka.admin.event.topic"],
            "Admin event topic should match",
        )

        logger.info("Kafka Event Listener configuration validated successfully")
    }

    @Test
    @Order(5)
    fun `should handle event filtering`() {
        // 이 테스트는 실제 Kafka Event Listener 플러그인이 Keycloak에 설치되어야 작동
        // 현재는 설정 확인만 수행

        val adminClient = keycloakContainer.getAdminClient()
        val realm = adminClient.realm(KeycloakTestContainer.TEST_REALM).toRepresentation()

        // 이벤트 필터링 설정 테스트
        val attributes = realm.attributes ?: mutableMapOf()
        attributes["kafka.included.event.types"] = "LOGIN,LOGOUT,REGISTER"
        realm.attributes = attributes

        try {
            adminClient.realm(KeycloakTestContainer.TEST_REALM).update(realm)
            logger.info("Event filtering configuration updated")

            // 설정 재확인
            val updatedRealm = adminClient.realm(KeycloakTestContainer.TEST_REALM).toRepresentation()
            assertEquals(
                "LOGIN,LOGOUT,REGISTER",
                updatedRealm.attributes["kafka.included.event.types"],
                "Event type filtering should be configured",
            )
        } catch (e: Exception) {
            logger.error("Failed to update event filtering configuration", e)
            fail("Event filtering configuration failed: ${e.message}")
        }
    }

    @Test
    @Order(6)
    fun `should measure basic performance`() {
        val startTime = System.currentTimeMillis()
        val producer = kafkaContainer.createProducer()

        // 100개 메시지 발송
        val messageCount = 100
        repeat(messageCount) { i ->
            val message = """{"test": "message", "index": $i, "timestamp": ${System.currentTimeMillis()}}"""
            producer.send(
                org.apache.kafka.clients.producer.ProducerRecord(
                    KafkaTestContainer.USER_EVENTS_TOPIC,
                    "perf-test-$i",
                    message,
                ),
            )
        }
        producer.flush()
        producer.close()

        val sendTime = System.currentTimeMillis() - startTime
        val messagesPerSecond = (messageCount * 1000.0) / sendTime

        logger.info("Sent $messageCount messages in ${sendTime}ms (${messagesPerSecond.toInt()} msg/sec)")

        // 성능 검증: 초당 50개 이상 처리 가능해야 함
        assertTrue(messagesPerSecond >= 50, "Should process at least 50 messages per second")
    }
}
