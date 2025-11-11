package org.scriptonbasestar.kcexts.events.redis.testcontainers

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

class RedisTestContainer : GenericContainer<RedisTestContainer>(DockerImageName.parse(REDIS_IMAGE)) {
    private val logger = LoggerFactory.getLogger(RedisTestContainer::class.java)

    companion object {
        private const val REDIS_IMAGE = "redis:7-alpine"
        private const val REDIS_PORT = 6379
        const val USER_EVENTS_STREAM = "test.keycloak.events"
        const val ADMIN_EVENTS_STREAM = "test.keycloak.admin.events"
        const val CONSUMER_GROUP = "test-consumer-group"
    }

    init {
        withExposedPorts(REDIS_PORT)
        withReuse(true)
        withStartupTimeout(Duration.ofMinutes(2))
    }

    val container: GenericContainer<*> get() = this

    private var redisClient: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null

    override fun start() {
        super.start()
        logger.info("Redis TestContainer started on: ${getRedisUrl()}")
        setupStreams()
    }

    override fun stop() {
        logger.info("Stopping Redis TestContainer...")
        connection?.close()
        redisClient?.shutdown()
        super.stop()
        logger.info("Redis TestContainer stopped")
    }

    fun getRedisUrl(): String = "redis://$host:${getMappedPort(REDIS_PORT)}"

    private fun setupStreams() {
        logger.info("Setting up Redis streams...")

        val client = createRedisClient()
        val conn = client.connect()
        val commands = conn.sync()

        try {
            // Create consumer groups for streams
            // Note: XGROUP CREATE will fail if stream doesn't exist, so we create a dummy entry first
            try {
                commands.xadd(USER_EVENTS_STREAM, mapOf("init" to "true"))
                commands.xgroupCreate(
                    io.lettuce.core.XReadArgs.StreamOffset
                        .from(USER_EVENTS_STREAM, "0"),
                    CONSUMER_GROUP,
                )
                logger.info("Created consumer group for $USER_EVENTS_STREAM")
            } catch (e: Exception) {
                logger.warn("Consumer group may already exist for $USER_EVENTS_STREAM: ${e.message}")
            }

            try {
                commands.xadd(ADMIN_EVENTS_STREAM, mapOf("init" to "true"))
                commands.xgroupCreate(
                    io.lettuce.core.XReadArgs.StreamOffset
                        .from(ADMIN_EVENTS_STREAM, "0"),
                    CONSUMER_GROUP,
                )
                logger.info("Created consumer group for $ADMIN_EVENTS_STREAM")
            } catch (e: Exception) {
                logger.warn("Consumer group may already exist for $ADMIN_EVENTS_STREAM: ${e.message}")
            }
        } finally {
            conn.close()
        }
    }

    fun createRedisClient(): RedisClient {
        if (redisClient == null) {
            val redisUri =
                RedisURI
                    .Builder
                    .redis(host, getMappedPort(REDIS_PORT))
                    .build()
            redisClient = RedisClient.create(redisUri)
        }
        return redisClient!!
    }

    fun getConnection(): StatefulRedisConnection<String, String> {
        if (connection == null || !connection!!.isOpen) {
            connection = createRedisClient().connect()
        }
        return connection!!
    }

    fun getCommands(): RedisCommands<String, String> = getConnection().sync()

    fun addToStream(
        streamKey: String,
        data: Map<String, String>,
    ): String {
        val commands = getCommands()
        val messageId = commands.xadd(streamKey, data)
        logger.debug("Added message to stream {}: {}", streamKey, messageId)
        return messageId
    }

    fun readFromStream(
        streamKey: String,
        count: Long = 10,
    ): List<Map<String, String>> {
        val commands = getCommands()
        val messages = mutableListOf<Map<String, String>>()

        try {
            val streamMessages =
                commands.xrange(
                    streamKey,
                    io.lettuce.core.Range
                        .unbounded(),
                    io.lettuce.core.Limit
                        .from(count),
                )

            streamMessages.forEach { message ->
                messages.add(message.body)
                logger.debug("Read message from {}: {}", streamKey, message.body)
            }
        } catch (e: Exception) {
            logger.warn("Failed to read from stream {}: {}", streamKey, e.message)
        }

        logger.info("Read {} messages from stream {}", messages.size, streamKey)
        return messages
    }

    fun getStreamLength(streamKey: String): Long {
        val commands = getCommands()
        return try {
            commands.xlen(streamKey)
        } catch (e: Exception) {
            logger.warn("Failed to get stream length for {}: {}", streamKey, e.message)
            0L
        }
    }

    fun deleteStream(streamKey: String) {
        val commands = getCommands()
        try {
            commands.del(streamKey)
            logger.info("Deleted stream: $streamKey")
        } catch (e: Exception) {
            logger.warn("Failed to delete stream {}: {}", streamKey, e.message)
        }
    }

    fun flushAll() {
        val commands = getCommands()
        commands.flushall()
        logger.info("Flushed all Redis data")
    }
}
