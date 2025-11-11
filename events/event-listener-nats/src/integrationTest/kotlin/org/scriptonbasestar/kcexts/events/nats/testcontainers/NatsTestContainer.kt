package org.scriptonbasestar.kcexts.events.nats.testcontainers

import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.client.impl.Headers
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

class NatsTestContainer : GenericContainer<NatsTestContainer>(DockerImageName.parse(NATS_IMAGE)) {
    private val logger = LoggerFactory.getLogger(NatsTestContainer::class.java)

    companion object {
        private const val NATS_IMAGE = "nats:2.10-alpine"
        private const val NATS_CLIENT_PORT = 4222
        private const val NATS_MONITORING_PORT = 8222
        const val USER_EVENTS_SUBJECT = "test.keycloak.events"
        const val ADMIN_EVENTS_SUBJECT = "test.keycloak.admin.events"
    }

    init {
        withExposedPorts(NATS_CLIENT_PORT, NATS_MONITORING_PORT)
        withCommand("-js", "-m", NATS_MONITORING_PORT.toString()) // Enable JetStream and monitoring
        withReuse(true)
        withStartupTimeout(Duration.ofMinutes(2))
        waitingFor(
            org.testcontainers.containers.wait.strategy.Wait
                .forHttp("/varz")
                .forPort(NATS_MONITORING_PORT)
                .withStartupTimeout(Duration.ofSeconds(60)),
        )
    }

    val container: GenericContainer<*> get() = this

    private var natsConnection: Connection? = null

    override fun start() {
        super.start()
        logger.info("NATS TestContainer started on: ${getNatsUrl()}")
    }

    override fun stop() {
        logger.info("Stopping NATS TestContainer...")
        natsConnection?.close()
        super.stop()
        logger.info("NATS TestContainer stopped")
    }

    fun getNatsUrl(): String = "nats://$host:${getMappedPort(NATS_CLIENT_PORT)}"

    fun getMonitoringUrl(): String = "http://$host:${getMappedPort(NATS_MONITORING_PORT)}"

    fun createConnection(): Connection {
        if (natsConnection == null || natsConnection!!.status != Connection.Status.CONNECTED) {
            val options =
                Options
                    .Builder()
                    .server(getNatsUrl())
                    .connectionTimeout(Duration.ofSeconds(10))
                    .build()

            natsConnection = Nats.connect(options)
            logger.info("Created NATS connection to: ${getNatsUrl()}")
        }
        return natsConnection!!
    }

    fun getConnection(): Connection = createConnection()

    fun publishMessage(
        subject: String,
        message: String,
    ) {
        val connection = getConnection()
        connection.publish(subject, message.toByteArray(Charsets.UTF_8))
        connection.flush(Duration.ofSeconds(1))
        logger.debug("Published message to subject {}: {}", subject, message)
    }

    fun publishMessageWithHeaders(
        subject: String,
        message: String,
        headers: Map<String, String>,
    ) {
        val connection = getConnection()
        val natsHeaders = Headers()
        headers.forEach { (key, value) -> natsHeaders.add(key, value) }

        connection.publish(subject, natsHeaders, message.toByteArray(Charsets.UTF_8))
        connection.flush(Duration.ofSeconds(1))
        logger.debug("Published message with headers to subject {}", subject)
    }

    fun subscribeAndReceive(
        subject: String,
        count: Int = 1,
        timeoutMs: Long = 5000,
    ): List<String> {
        val connection = getConnection()
        val subscription = connection.subscribe(subject)
        val messages = mutableListOf<String>()

        try {
            val startTime = System.currentTimeMillis()

            while (messages.size < count && System.currentTimeMillis() - startTime < timeoutMs) {
                val message = subscription.nextMessage(Duration.ofMillis(100))
                if (message != null) {
                    val data = String(message.data, Charsets.UTF_8)
                    messages.add(data)
                    logger.debug("Received message from {}: {}", subject, data)
                }
            }
        } finally {
            subscription.unsubscribe()
        }

        logger.info("Received {} messages from subject {}", messages.size, subject)
        return messages
    }

    fun getServerInfo(): Map<String, Any> {
        val connection = getConnection()
        val serverInfo = connection.serverInfo

        return mapOf(
            "serverId" to (serverInfo?.serverId ?: "unknown"),
            "serverName" to (serverInfo?.serverName ?: "unknown"),
            "version" to (serverInfo?.version ?: "unknown"),
            "host" to (serverInfo?.host ?: "unknown"),
            "port" to (serverInfo?.port ?: 0),
        )
    }

    fun isConnected(): Boolean = natsConnection?.status == Connection.Status.CONNECTED
}
