package org.scriptonbasestar.kcexts.events.nats

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.scriptonbasestar.kcexts.events.nats.testcontainers.BaseIntegrationTest
import org.scriptonbasestar.kcexts.events.nats.testcontainers.KeycloakTestContainer
import org.scriptonbasestar.kcexts.events.nats.testcontainers.NatsTestContainer
import org.slf4j.LoggerFactory
import org.testcontainers.junit.jupiter.Container

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NatsEventListenerIntegrationTest : BaseIntegrationTest() {
    companion object {
        private val logger = LoggerFactory.getLogger(NatsEventListenerIntegrationTest::class.java)

        @Container
        @JvmStatic
        val natsContainer = NatsTestContainer()

        @Container
        @JvmStatic
        val keycloakContainer = KeycloakTestContainer()

        @JvmStatic
        @BeforeAll
        fun setupIntegrationTest() {
            logger.info("Starting NATS integration test setup...")

            // TestContainers가 자동으로 컨테이너를 시작합니다
            // Keycloak의 NATS 설정 업데이트
            keycloakContainer.updateNatsConfiguration(
                natsContainer.getNatsUrl(),
                NatsTestContainer.USER_EVENTS_SUBJECT,
                NatsTestContainer.ADMIN_EVENTS_SUBJECT,
            )

            logger.info("NATS integration test setup completed")
        }
    }

    @Test
    @Order(1)
    fun `should verify containers are running`() {
        assertTrue(natsContainer.isRunning, "NATS container should be running")
        assertTrue(keycloakContainer.isRunning, "Keycloak container should be running")

        // Create connection to verify connectivity
        val connection = natsContainer.createConnection()
        assertTrue(
            connection.status == io.nats.client.Connection.Status.CONNECTED,
            "NATS connection should be established",
        )

        logger.info("NATS URL: ${natsContainer.getNatsUrl()}")
        logger.info("NATS Monitoring URL: ${natsContainer.getMonitoringUrl()}")
        logger.info("Keycloak Auth Server URL: ${keycloakContainer.getAuthServerUrl()}")
    }

    @Test
    @Order(2)
    fun `should publish and subscribe to NATS subject`() {
        val testSubject = "test.subject.${System.currentTimeMillis()}"
        val testMessage = """{"test": "message", "timestamp": ${System.currentTimeMillis()}}"""

        // Start subscription in a separate thread
        val messages = mutableListOf<String>()
        val subscriptionThread =
            Thread {
                val received = natsContainer.subscribeAndReceive(testSubject, count = 1, timeoutMs = 3000)
                messages.addAll(received)
            }
        subscriptionThread.start()

        // Give subscription time to be ready
        Thread.sleep(500)

        // Publish message
        natsContainer.publishMessage(testSubject, testMessage)

        // Wait for subscription to complete
        subscriptionThread.join(5000)

        assertFalse(messages.isEmpty(), "Should receive at least one message")
        assertTrue(messages[0].contains("test"), "Message should contain 'test' field")

        logger.info("Successfully published and received NATS message")
    }

    @Test
    @Order(3)
    fun `should verify NATS server info`() {
        val serverInfo = natsContainer.getServerInfo()

        org.junit.jupiter.api.Assertions
            .assertNotNull(serverInfo, "Server info should not be null")
        assertTrue(serverInfo.containsKey("version"), "Should have version info")
        assertTrue(serverInfo.containsKey("serverId"), "Should have server ID")

        logger.info("NATS server info: $serverInfo")
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

        // NATS 설정 확인
        val attributes = realm.attributes
        kotlin.test.assertNotNull(attributes, "Realm should have attributes")
        assertEquals(
            natsContainer.getNatsUrl(),
            attributes["nats.url"],
            "NATS URL should match",
        )
        assertEquals(
            NatsTestContainer.USER_EVENTS_SUBJECT,
            attributes["nats.user.events.subject"],
            "User events subject should match",
        )
        assertEquals(
            NatsTestContainer.ADMIN_EVENTS_SUBJECT,
            attributes["nats.admin.events.subject"],
            "Admin events subject should match",
        )

        logger.info("Keycloak realm is properly configured for NATS")
    }

    @Test
    @Order(5)
    fun `should handle basic performance test`() {
        val messageCount = 100
        val testSubject = "performance.test.${System.currentTimeMillis()}"
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

            natsContainer.publishMessage(testSubject, message)
        }

        val sendTime = System.currentTimeMillis() - startTime
        val messagesPerSecond = (messageCount * 1000.0) / sendTime

        logger.info("Published $messageCount messages in ${sendTime}ms (${messagesPerSecond.toInt()} msg/sec)")

        // 성능 검증: 초당 200개 이상 처리 가능해야 함 (NATS는 매우 빠름)
        assertTrue(messagesPerSecond >= 200, "Should process at least 200 messages per second")
    }
}

// Extension function to update NATS configuration
private fun KeycloakTestContainer.updateNatsConfiguration(
    natsUrl: String,
    userEventsSubject: String,
    adminEventsSubject: String,
) {
    val realm = getAdminClient().realm(KeycloakTestContainer.TEST_REALM).toRepresentation()

    realm.attributes = realm.attributes ?: mutableMapOf()
    realm.attributes["nats.url"] = natsUrl
    realm.attributes["nats.user.events.subject"] = userEventsSubject
    realm.attributes["nats.admin.events.subject"] = adminEventsSubject
    realm.attributes["nats.enable.user.events"] = "true"
    realm.attributes["nats.enable.admin.events"] = "true"

    getAdminClient().realm(KeycloakTestContainer.TEST_REALM).update(realm)
}
