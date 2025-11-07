package org.scriptonbasestar.kcexts.events.rabbitmq.testcontainers

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import org.slf4j.LoggerFactory
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

class RabbitMQTestContainer :
    RabbitMQContainer(
        DockerImageName
            .parse(RABBITMQ_IMAGE)
            .asCompatibleSubstituteFor("rabbitmq"),
    ) {
    private val logger = LoggerFactory.getLogger(RabbitMQTestContainer::class.java)

    companion object {
        private const val RABBITMQ_IMAGE = "rabbitmq:3.12-management-alpine"
        const val USER_EVENTS_EXCHANGE = "test.keycloak.events"
        const val ADMIN_EVENTS_EXCHANGE = "test.keycloak.admin.events"
        const val USER_EVENTS_QUEUE = "test.keycloak.events.queue"
        const val ADMIN_EVENTS_QUEUE = "test.keycloak.admin.events.queue"
        const val ROUTING_KEY = "keycloak.*"
    }

    init {
        withReuse(true)
    }

    val container: RabbitMQContainer get() = this

    private var connection: Connection? = null

    override fun start() {
        super.start()
        logger.info("RabbitMQ TestContainer started on: ${super.getAmqpUrl()}")
        setupExchangesAndQueues()
    }

    override fun stop() {
        logger.info("Stopping RabbitMQ TestContainer...")
        connection?.close()
        super.stop()
        logger.info("RabbitMQ TestContainer stopped")
    }

    fun getTestAmqpUrl(): String = super.getAmqpUrl()

    val httpApiUrl: String
        get() = "http://$host:${getMappedPort(15672)}"

    private fun setupExchangesAndQueues() {
        logger.info("Setting up RabbitMQ exchanges and queues...")

        val factory = createConnectionFactory()
        connection = factory.newConnection()
        val channel = connection!!.createChannel()

        try {
            // Create topic exchanges
            channel.exchangeDeclare(USER_EVENTS_EXCHANGE, "topic", true, false, null)
            channel.exchangeDeclare(ADMIN_EVENTS_EXCHANGE, "topic", true, false, null)
            logger.info("Created exchanges: $USER_EVENTS_EXCHANGE, $ADMIN_EVENTS_EXCHANGE")

            // Create queues
            channel.queueDeclare(USER_EVENTS_QUEUE, true, false, false, null)
            channel.queueDeclare(ADMIN_EVENTS_QUEUE, true, false, false, null)
            logger.info("Created queues: $USER_EVENTS_QUEUE, $ADMIN_EVENTS_QUEUE")

            // Bind queues to exchanges
            channel.queueBind(USER_EVENTS_QUEUE, USER_EVENTS_EXCHANGE, ROUTING_KEY)
            channel.queueBind(ADMIN_EVENTS_QUEUE, ADMIN_EVENTS_EXCHANGE, ROUTING_KEY)
            logger.info("Bound queues to exchanges with routing key: $ROUTING_KEY")
        } catch (e: Exception) {
            logger.warn("Failed to setup exchanges and queues (may already exist): ${e.message}")
        } finally {
            channel.close()
        }
    }

    fun createConnectionFactory(): ConnectionFactory =
        ConnectionFactory().apply {
            host = this@RabbitMQTestContainer.host
            port = amqpPort
            username = adminUsername
            password = adminPassword
            virtualHost = "/"
        }

    fun createChannel(): Channel {
        if (connection == null || !connection!!.isOpen) {
            connection = createConnectionFactory().newConnection()
        }
        return connection!!.createChannel()
    }

    fun consumeMessages(
        queueName: String,
        maxMessages: Int = 10,
        timeoutMs: Long = 5000,
    ): List<String> {
        val messages = mutableListOf<String>()
        val channel = createChannel()

        try {
            val startTime = System.currentTimeMillis()

            while (messages.size < maxMessages && System.currentTimeMillis() - startTime < timeoutMs) {
                val response = channel.basicGet(queueName, true)
                if (response != null) {
                    val message = String(response.body, Charsets.UTF_8)
                    messages.add(message)
                    logger.debug("Consumed message from {}: {}", queueName, message)
                } else {
                    Thread.sleep(100)
                }
            }
        } finally {
            channel.close()
        }

        logger.info("Consumed {} messages from queue {}", messages.size, queueName)
        return messages
    }

    fun publishMessage(
        exchange: String,
        routingKey: String,
        message: String,
    ) {
        val channel = createChannel()
        try {
            channel.basicPublish(exchange, routingKey, null, message.toByteArray(Charsets.UTF_8))
            logger.debug("Published message to exchange {} with routing key {}", exchange, routingKey)
        } finally {
            channel.close()
        }
    }

    fun getQueueMessageCount(queueName: String): Long {
        val channel = createChannel()
        return try {
            channel.messageCount(queueName)
        } finally {
            channel.close()
        }
    }

    fun purgeQueue(queueName: String) {
        val channel = createChannel()
        try {
            channel.queuePurge(queueName)
            logger.info("Purged queue: $queueName")
        } finally {
            channel.close()
        }
    }
}
