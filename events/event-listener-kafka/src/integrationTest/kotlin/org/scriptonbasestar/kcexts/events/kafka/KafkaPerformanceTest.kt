package org.scriptonbasestar.kcexts.events.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.scriptonbasestar.kcexts.events.kafka.testcontainers.BaseIntegrationTest
import org.scriptonbasestar.kcexts.events.kafka.testcontainers.KafkaTestContainer
import org.slf4j.LoggerFactory
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KafkaPerformanceTest : BaseIntegrationTest() {
    companion object {
        private val logger = LoggerFactory.getLogger(KafkaPerformanceTest::class.java)

        @Container
        @JvmStatic
        val kafkaContainer = KafkaTestContainer()

        @JvmStatic
        @BeforeAll
        fun setupPerformanceTest() {
            logger.info("Starting performance test setup...")
            kafkaContainer.start()
            logger.info("Performance test setup completed")
        }

        @JvmStatic
        @AfterAll
        fun teardownPerformanceTest() {
            logger.info("Cleaning up performance test...")
            kafkaContainer.stop()
            logger.info("Performance test cleanup completed")
        }
    }

    @Test
    @Order(1)
    fun `should handle high frequency event processing`() {
        val messageCount = 1000
        val producer = kafkaContainer.createProducer()
        val startTime = System.currentTimeMillis()

        logger.info("Sending $messageCount messages for high frequency test...")

        repeat(messageCount) { i ->
            val eventData = createTestEventData(i)
            producer.send(
                ProducerRecord(
                    KafkaTestContainer.USER_EVENTS_TOPIC,
                    "high-freq-$i",
                    eventData,
                ),
            )
        }

        producer.flush()
        producer.close()

        val totalTime = System.currentTimeMillis() - startTime
        val throughput = (messageCount * 1000.0) / totalTime

        logger.info("High frequency test: $messageCount messages in ${totalTime}ms")
        logger.info("Throughput: ${throughput.toInt()} messages/second")

        // 초당 500개 이상 처리 가능해야 함
        assertTrue(throughput >= 500, "Should handle at least 500 messages per second")
    }

    @Test
    @Order(2)
    fun `should handle concurrent producers`() {
        val concurrentProducers = 10
        val messagesPerProducer = 100
        val totalMessages = concurrentProducers * messagesPerProducer

        val executor = Executors.newFixedThreadPool(concurrentProducers)
        val completedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        logger.info(
            "Starting concurrent producer test: $concurrentProducers producers, $messagesPerProducer messages each",
        )

        val futures =
            (1..concurrentProducers).map { producerId ->
                CompletableFuture.runAsync({
                    val producer = kafkaContainer.createProducer()

                    repeat(messagesPerProducer) { messageId ->
                        val eventData = createTestEventData(messageId, "producer-$producerId")
                        producer.send(
                            ProducerRecord(
                                KafkaTestContainer.USER_EVENTS_TOPIC,
                                "concurrent-$producerId-$messageId",
                                eventData,
                            ),
                        )
                    }

                    producer.flush()
                    producer.close()

                    val completed = completedCount.incrementAndGet()
                    logger.debug("Producer $producerId completed ($completed/$concurrentProducers)")
                }, executor)
            }

        // 모든 Producer 완료 대기 (최대 30초)
        CompletableFuture.allOf(*futures.toTypedArray()).get(30, TimeUnit.SECONDS)

        val totalTime = System.currentTimeMillis() - startTime
        val throughput = (totalMessages * 1000.0) / totalTime

        logger.info("Concurrent producer test: $totalMessages messages in ${totalTime}ms")
        logger.info("Concurrent throughput: ${throughput.toInt()} messages/second")

        executor.shutdown()

        // 동시성 환경에서도 초당 200개 이상 처리 가능해야 함
        assertTrue(throughput >= 200, "Should handle at least 200 messages/second with concurrent producers")
    }

    @Test
    @Order(3)
    fun `should handle large message payloads`() {
        val messageCount = 100
        val producer = kafkaContainer.createProducer()
        val startTime = System.currentTimeMillis()

        logger.info("Testing large message payloads: $messageCount messages")

        repeat(messageCount) { i ->
            val largeEventData = createLargeTestEventData(i)
            producer.send(
                ProducerRecord(
                    KafkaTestContainer.USER_EVENTS_TOPIC,
                    "large-$i",
                    largeEventData,
                ),
            )
        }

        producer.flush()
        producer.close()

        val totalTime = System.currentTimeMillis() - startTime
        val throughput = (messageCount * 1000.0) / totalTime

        logger.info("Large payload test: $messageCount messages in ${totalTime}ms")
        logger.info("Large payload throughput: ${throughput.toInt()} messages/second")

        // 큰 메시지도 초당 50개 이상 처리 가능해야 함
        assertTrue(throughput >= 50, "Should handle at least 50 large messages per second")
    }

    @Test
    @Order(4)
    fun `should measure consumer lag`() {
        val messageCount = 500
        val producer = kafkaContainer.createProducer()

        logger.info("Testing consumer lag with $messageCount messages")

        // 메시지 발송
        val sendStartTime = System.currentTimeMillis()
        repeat(messageCount) { i ->
            val eventData = createTestEventData(i, timestamp = System.currentTimeMillis())
            producer.send(
                ProducerRecord(
                    KafkaTestContainer.USER_EVENTS_TOPIC,
                    "lag-test-$i",
                    eventData,
                ),
            )
        }
        producer.flush()
        producer.close()

        val sendEndTime = System.currentTimeMillis()
        val sendDuration = sendEndTime - sendStartTime

        // 메시지 수신
        val consumeStartTime = System.currentTimeMillis()
        val messages =
            kafkaContainer.consumeMessages(
                KafkaTestContainer.USER_EVENTS_TOPIC,
                Duration.ofSeconds(30),
            )
        val consumeEndTime = System.currentTimeMillis()

        val consumeDuration = consumeEndTime - consumeStartTime
        val lag = consumeStartTime - sendEndTime

        logger.info("Send duration: ${sendDuration}ms")
        logger.info("Consume duration: ${consumeDuration}ms")
        logger.info("Consumer lag: ${lag}ms")
        logger.info("Received ${messages.size}/$messageCount messages")

        // Consumer lag는 5초 이내여야 함
        assertTrue(lag <= 5000, "Consumer lag should be less than 5 seconds")

        // 대부분의 메시지를 수신해야 함 (최소 90%)
        val receivedRatio = messages.size.toDouble() / messageCount
        assertTrue(receivedRatio >= 0.9, "Should receive at least 90% of messages")
    }

    @Test
    @Order(5)
    fun `should handle burst traffic`() {
        val burstSize = 200
        val burstCount = 5
        val burstInterval = 1000L // 1초

        logger.info("Testing burst traffic: $burstCount bursts of $burstSize messages")

        val producer = kafkaContainer.createProducer()
        val startTime = System.currentTimeMillis()

        repeat(burstCount) { burstIndex ->
            val burstStartTime = System.currentTimeMillis()

            // 각 버스트 내에서 빠르게 메시지 발송
            repeat(burstSize) { messageIndex ->
                val eventData = createTestEventData(messageIndex, "burst-$burstIndex")
                producer.send(
                    ProducerRecord(
                        KafkaTestContainer.USER_EVENTS_TOPIC,
                        "burst-$burstIndex-$messageIndex",
                        eventData,
                    ),
                )
            }
            producer.flush()

            val burstDuration = System.currentTimeMillis() - burstStartTime
            logger.info("Burst $burstIndex completed in ${burstDuration}ms")

            // 마지막 버스트가 아니면 잠시 대기
            if (burstIndex < burstCount - 1) {
                Thread.sleep(burstInterval)
            }
        }

        producer.close()

        val totalTime = System.currentTimeMillis() - startTime
        val totalMessages = burstSize * burstCount
        val avgThroughput = (totalMessages * 1000.0) / totalTime

        logger.info("Burst test completed: $totalMessages messages in ${totalTime}ms")
        logger.info("Average throughput: ${avgThroughput.toInt()} messages/second")

        // 버스트 트래픽도 평균적으로 초당 100개 이상 처리 가능해야 함
        assertTrue(avgThroughput >= 100, "Should handle burst traffic at 100+ messages/second average")
    }

    private fun createTestEventData(
        index: Int,
        prefix: String = "test",
        timestamp: Long = System.currentTimeMillis(),
    ): String =
        """
        {
            "id": "$prefix-event-$index",
            "time": $timestamp,
            "type": "LOGIN",
            "realmId": "test-realm",
            "clientId": "test-client",
            "userId": "user-$index",
            "sessionId": "session-$index",
            "ipAddress": "192.168.1.${ (index % 254) + 1}",
            "details": {
                "username": "user$index",
                "auth_method": "openid-connect",
                "code_id": "code-$index"
            }
        }
        """.trimIndent()

    private fun createLargeTestEventData(index: Int): String {
        val largeDetails = (1..100).associate { "detail_$it" to "value_${it}_$index" }
        val detailsJson = largeDetails.entries.joinToString(",") { """"${it.key}": "${it.value}"""" }

        return """
            {
                "id": "large-event-$index",
                "time": ${System.currentTimeMillis()},
                "type": "LOGIN",
                "realmId": "test-realm-with-very-long-name-$index",
                "clientId": "test-client-with-very-long-name-$index",
                "userId": "user-with-very-long-identifier-$index",
                "sessionId": "session-with-very-long-identifier-$index",
                "ipAddress": "192.168.1.${(index % 254) + 1}",
                "details": {
                    $detailsJson
                }
            }
            """.trimIndent()
    }
}
