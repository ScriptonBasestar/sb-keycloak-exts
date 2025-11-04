package org.scriptonbasestar.kcexts.events.common.batch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchProcessorTest {
    @Test
    fun `should process batch when size threshold reached`() {
        val processedBatches = mutableListOf<List<String>>()
        val latch = CountDownLatch(1)

        val processor =
            BatchProcessor<String>(
                batchSize = 3,
                flushInterval = Duration.ofSeconds(10),
                processBatch = { batch ->
                    processedBatches.add(batch)
                    latch.countDown()
                },
            )

        processor.start()

        // Add 3 items - should trigger batch processing
        processor.add("event1")
        processor.add("event2")
        processor.add("event3")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(1, processedBatches.size)
        assertEquals(listOf("event1", "event2", "event3"), processedBatches[0])

        processor.stop()
    }

    @Test
    fun `should process batch on time interval`() {
        val processedBatches = mutableListOf<List<String>>()
        val latch = CountDownLatch(1)

        val processor =
            BatchProcessor<String>(
                batchSize = 100,
                flushInterval = Duration.ofMillis(200),
                processBatch = { batch ->
                    processedBatches.add(batch)
                    latch.countDown()
                },
            )

        processor.start()

        // Add 2 items (below threshold)
        processor.add("event1")
        processor.add("event2")

        // Wait for time-based flush
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(1, processedBatches.size)
        assertEquals(listOf("event1", "event2"), processedBatches[0])

        processor.stop()
    }

    @Test
    fun `should process multiple batches`() {
        val processedBatches = mutableListOf<List<String>>()
        val latch = CountDownLatch(2)

        val processor =
            BatchProcessor<String>(
                batchSize = 2,
                flushInterval = Duration.ofSeconds(10),
                processBatch = { batch ->
                    processedBatches.add(batch)
                    latch.countDown()
                },
            )

        processor.start()

        // Add 4 items - should trigger 2 batches
        processor.add("event1")
        processor.add("event2")
        processor.add("event3")
        processor.add("event4")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(2, processedBatches.size)

        processor.stop()
    }

    @Test
    fun `should handle batch processing errors`() {
        val failedBatches = mutableListOf<List<String>>()
        val latch = CountDownLatch(1)

        val processor =
            BatchProcessor<String>(
                batchSize = 2,
                flushInterval = Duration.ofSeconds(10),
                processBatch = { _ -> throw RuntimeException("Processing failed") },
                onError = { batch, _ ->
                    failedBatches.add(batch)
                    latch.countDown()
                },
            )

        processor.start()

        processor.add("event1")
        processor.add("event2")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(1, failedBatches.size)

        val stats = processor.getStats()
        assertEquals(2L, stats.failedCount)

        processor.stop()
    }

    @Test
    fun `should flush remaining items on stop`() {
        val processedBatches = mutableListOf<List<String>>()

        val processor =
            BatchProcessor<String>(
                batchSize = 100,
                flushInterval = Duration.ofSeconds(10),
                processBatch = { batch ->
                    processedBatches.add(batch)
                },
            )

        processor.start()

        // Add items below threshold
        processor.add("event1")
        processor.add("event2")

        // Stop should flush remaining items
        processor.stop()

        assertEquals(1, processedBatches.size)
        assertEquals(listOf("event1", "event2"), processedBatches[0])
    }

    @Test
    fun `should add multiple items at once`() {
        val processedBatches = mutableListOf<List<String>>()
        val latch = CountDownLatch(1)

        val processor =
            BatchProcessor<String>(
                batchSize = 3,
                flushInterval = Duration.ofSeconds(10),
                processBatch = { batch ->
                    processedBatches.add(batch)
                    latch.countDown()
                },
            )

        processor.start()

        processor.addAll(listOf("event1", "event2", "event3"))

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(1, processedBatches.size)
        assertEquals(3, processedBatches[0].size)

        processor.stop()
    }

    @Test
    fun `should track statistics`() {
        val latch = CountDownLatch(2)

        val processor =
            BatchProcessor<String>(
                batchSize = 2,
                flushInterval = Duration.ofSeconds(10),
                processBatch = { _ ->
                    latch.countDown()
                },
            )

        processor.start()

        processor.add("event1")
        processor.add("event2")
        processor.add("event3")
        processor.add("event4")

        assertTrue(latch.await(2, TimeUnit.SECONDS))

        val stats = processor.getStats()
        assertEquals(4L, stats.processedCount)
        assertEquals(0L, stats.failedCount)
        assertEquals(2, stats.batchSize)
        assertTrue(stats.isRunning)

        processor.stop()

        val finalStats = processor.getStats()
        assertFalse(finalStats.isRunning)
    }

    @Test
    fun `should handle manual flush`() {
        val processedBatches = mutableListOf<List<String>>()

        val processor =
            BatchProcessor<String>(
                batchSize = 100,
                flushInterval = Duration.ofSeconds(10),
                processBatch = { batch ->
                    processedBatches.add(batch)
                },
            )

        processor.add("event1")
        processor.add("event2")

        assertEquals(2, processor.getBufferSize())

        processor.flush()

        assertEquals(0, processor.getBufferSize())
        assertEquals(1, processedBatches.size)
        assertEquals(2, processedBatches[0].size)

        processor.stop()
    }

    @Test
    fun `should validate constructor parameters`() {
        assertThrows<IllegalArgumentException> {
            BatchProcessor<String>(
                batchSize = 0,
                flushInterval = Duration.ofSeconds(1),
                processBatch = {},
            )
        }

        assertThrows<IllegalArgumentException> {
            BatchProcessor<String>(
                batchSize = 10,
                flushInterval = Duration.ZERO,
                processBatch = {},
            )
        }

        assertThrows<IllegalArgumentException> {
            BatchProcessor<String>(
                batchSize = 10,
                flushInterval = Duration.ofSeconds(-1),
                processBatch = {},
            )
        }
    }

    @Test
    fun `should handle concurrent additions`() {
        val processedItems = mutableListOf<String>()
        val latch = CountDownLatch(1)

        val processor =
            BatchProcessor<String>(
                batchSize = 100,
                flushInterval = Duration.ofMillis(500),
                processBatch = { batch ->
                    synchronized(processedItems) {
                        processedItems.addAll(batch)
                    }
                    latch.countDown()
                },
            )

        processor.start()

        // Add items from multiple threads
        val threads =
            (1..10).map { threadNum ->
                Thread {
                    repeat(10) { i ->
                        processor.add("thread${threadNum}-event$i")
                    }
                }
            }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue(latch.await(2, TimeUnit.SECONDS))

        processor.stop()

        // Should have processed 100 items total
        assertEquals(100, processedItems.size)
    }

    @Test
    fun `should not start twice`() {
        val processor =
            BatchProcessor<String>(
                batchSize = 10,
                flushInterval = Duration.ofSeconds(1),
                processBatch = {},
            )

        processor.start()
        assertTrue(processor.isRunning())

        processor.start() // Should be no-op
        assertTrue(processor.isRunning())

        processor.stop()
        assertFalse(processor.isRunning())
    }
}
