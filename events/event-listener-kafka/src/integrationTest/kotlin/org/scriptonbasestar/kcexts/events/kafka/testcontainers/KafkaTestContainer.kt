package org.scriptonbasestar.kcexts.events.kafka.testcontainers

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.*

class KafkaTestContainer :
    KafkaContainer(DockerImageName.parse(KAFKA_IMAGE).asCompatibleSubstituteFor("confluentinc/cp-kafka")) {
    private val logger = LoggerFactory.getLogger(KafkaTestContainer::class.java)

    companion object {
        private const val KAFKA_IMAGE = "confluentinc/cp-kafka:7.5.0"
        const val USER_EVENTS_TOPIC = "test.keycloak.events"
        const val ADMIN_EVENTS_TOPIC = "test.keycloak.admin.events"
    }

    init {
        withReuse(true)
    }

    val container: KafkaContainer get() = this

    private var adminClient: AdminClient? = null

    override fun start() {
        super.start()
        logger.info("Kafka TestContainer started on: $bootstrapServers")
        setupTopics()
    }

    override fun stop() {
        logger.info("Stopping Kafka TestContainer...")
        adminClient?.close()
        super.stop()
        logger.info("Kafka TestContainer stopped")
    }

    // bootstrapServers 속성은 이미 KafkaContainer에서 제공됨

    private fun setupTopics() {
        logger.info("Setting up Kafka topics...")

        adminClient =
            AdminClient.create(
                mapOf(
                    AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ),
            )

        val topics =
            listOf(
                NewTopic(USER_EVENTS_TOPIC, 3, 1),
                NewTopic(ADMIN_EVENTS_TOPIC, 3, 1),
            )

        try {
            adminClient!!.createTopics(topics).all().get()
            logger.info("Created topics: ${topics.map { it.name() }}")
        } catch (e: Exception) {
            logger.warn("Failed to create topics (may already exist): ${e.message}")
        }
    }

    fun createProducer(): KafkaProducer<String, String> {
        val props =
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.ACKS_CONFIG, "all")
                put(ProducerConfig.RETRIES_CONFIG, 3)
            }
        return KafkaProducer(props)
    }

    fun createConsumer(groupId: String = "test-group"): KafkaConsumer<String, String> {
        val props =
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            }
        return KafkaConsumer(props)
    }

    fun consumeMessages(
        topic: String,
        timeout: Duration = Duration.ofSeconds(10),
    ): List<String> {
        val consumer = createConsumer()
        consumer.subscribe(listOf(topic))

        val messages = mutableListOf<String>()
        val endTime = System.currentTimeMillis() + timeout.toMillis()

        try {
            while (System.currentTimeMillis() < endTime) {
                val records = consumer.poll(Duration.ofMillis(100))
                records.forEach { record ->
                    messages.add(record.value())
                    logger.debug("Consumed message from {}: {}", topic, record.value())
                }

                if (messages.isNotEmpty()) {
                    break
                }
            }
        } finally {
            consumer.close()
        }

        logger.info("Consumed {} messages from topic {}", messages.size, topic)
        return messages
    }
}
