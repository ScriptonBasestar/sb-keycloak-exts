package org.scriptonbasestar.kcexts.events.kafka.testcontainers

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.representations.idm.*
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

class KeycloakTestContainer {
    private val logger = LoggerFactory.getLogger(KeycloakTestContainer::class.java)

    companion object {
        private const val KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.3.1"
        private const val ADMIN_USERNAME = "admin"
        private const val ADMIN_PASSWORD = "admin"
        private const val HTTP_PORT = 8080

        const val TEST_REALM = "test-realm"
        const val TEST_CLIENT_ID = "test-client"
        const val TEST_USERNAME = "testuser"
        const val TEST_PASSWORD = "testpass"
    }

    val container: GenericContainer<*> =
        GenericContainer(DockerImageName.parse(KEYCLOAK_IMAGE))
            .withExposedPorts(HTTP_PORT)
            .withEnv("KEYCLOAK_ADMIN", ADMIN_USERNAME)
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN_USERNAME)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withCommand("start-dev")
            .waitingFor(Wait.forHttp("/realms/master").withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(true)

    private var keycloakAdmin: Keycloak? = null

    fun start() {
        if (!container.isRunning) {
            logger.info("Starting Keycloak TestContainer...")
            container.start()
            logger.info("Keycloak TestContainer started on: ${getAuthServerUrl()}")

            setupKeycloakAdmin()
            setupTestRealm()
        }
    }

    fun stop() {
        logger.info("Stopping Keycloak TestContainer...")
        keycloakAdmin?.close()
        container.stop()
        logger.info("Keycloak TestContainer stopped")
    }

    fun getAuthServerUrl(): String = "http://${container.host}:${container.getMappedPort(HTTP_PORT)}"

    fun getRealmUrl(): String = "${getAuthServerUrl()}/realms/$TEST_REALM"

    private fun setupKeycloakAdmin() {
        logger.info("Setting up Keycloak admin client...")

        keycloakAdmin =
            KeycloakBuilder
                .builder()
                .serverUrl(getAuthServerUrl())
                .realm("master")
                .username(ADMIN_USERNAME)
                .password(ADMIN_PASSWORD)
                .clientId("admin-cli")
                .build()

        logger.info("Keycloak admin client configured")
    }

    private fun setupTestRealm() {
        logger.info("Setting up test realm: $TEST_REALM")

        val realm =
            RealmRepresentation().apply {
                realm = TEST_REALM
                isEnabled = true
                isEventsEnabled = true
                eventsListeners = listOf("kafka-event-listener")
                isAdminEventsEnabled = true
                isAdminEventsDetailsEnabled = true
            }

        try {
            keycloakAdmin!!.realms().create(realm)
            logger.info("Created realm: $TEST_REALM")
        } catch (e: Exception) {
            logger.warn("Realm may already exist: ${e.message}")
        }

        setupTestClient()
        setupTestUser()
        configureKafkaEventListener()
    }

    private fun setupTestClient() {
        logger.info("Setting up test client: $TEST_CLIENT_ID")

        val client =
            ClientRepresentation().apply {
                clientId = TEST_CLIENT_ID
                isEnabled = true
                isPublicClient = false
                isDirectAccessGrantsEnabled = true
                secret = "test-secret"
                redirectUris = listOf("*")
            }

        try {
            keycloakAdmin!!.realm(TEST_REALM).clients().create(client)
            logger.info("Created client: $TEST_CLIENT_ID")
        } catch (e: Exception) {
            logger.warn("Client may already exist: ${e.message}")
        }
    }

    private fun setupTestUser() {
        logger.info("Setting up test user: $TEST_USERNAME")

        val user =
            UserRepresentation().apply {
                username = TEST_USERNAME
                email = "test@example.com"
                firstName = "Test"
                lastName = "User"
                isEnabled = true
                isEmailVerified = true
            }

        try {
            val response = keycloakAdmin!!.realm(TEST_REALM).users().create(user)
            val userId = response.location.path.substringAfterLast("/")

            // 패스워드 설정
            val passwordCredential =
                CredentialRepresentation().apply {
                    type = CredentialRepresentation.PASSWORD
                    value = TEST_PASSWORD
                    isTemporary = false
                }

            keycloakAdmin!!
                .realm(TEST_REALM)
                .users()
                .get(userId)
                .resetPassword(passwordCredential)

            logger.info("Created user: $TEST_USERNAME")
        } catch (e: Exception) {
            logger.warn("User may already exist: ${e.message}")
        }
    }

    private fun configureKafkaEventListener(kafkaBootstrapServers: String = "localhost:9092") {
        logger.info("Configuring Kafka Event Listener for realm: $TEST_REALM")

        val realmResource = keycloakAdmin!!.realm(TEST_REALM)
        val realm = realmResource.toRepresentation()

        // Realm attributes에 Kafka 설정 추가
        val attributes = realm.attributes ?: mutableMapOf()
        attributes.apply {
            put("kafka.bootstrap.servers", kafkaBootstrapServers)
            put("kafka.event.topic", KafkaTestContainer.USER_EVENTS_TOPIC)
            put("kafka.admin.event.topic", KafkaTestContainer.ADMIN_EVENTS_TOPIC)
            put("kafka.client.id", "keycloak-test-client")
            put("kafka.enable.user.events", "true")
            put("kafka.enable.admin.events", "true")
        }
        realm.attributes = attributes

        try {
            realmResource.update(realm)
            logger.info("Updated realm with Kafka configuration")
        } catch (e: Exception) {
            logger.error("Failed to update realm configuration", e)
        }
    }

    fun updateKafkaConfiguration(kafkaBootstrapServers: String) {
        configureKafkaEventListener(kafkaBootstrapServers)
    }

    fun createUserSession(): String {
        // 사용자 로그인을 시뮬레이션하여 이벤트 생성
        val tokenUrl = "${getRealmUrl()}/protocol/openid-connect/token"

        // 실제 구현에서는 HTTP 클라이언트로 토큰 요청
        logger.info("Would create user session via: $tokenUrl")
        return "test-session-id"
    }

    fun getAdminClient(): Keycloak = keycloakAdmin!!
}
