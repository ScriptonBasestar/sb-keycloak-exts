package org.scriptonbasestar.kcexts.events.common.batch

import org.jboss.logging.Logger
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Batch processor for event listeners
 *
 * Accumulates events and processes them in batches based on:
 * - Batch size threshold
 * - Time window
 */
class BatchProcessor<T>(
    private val batchSize: Int = 100,
    private val flushInterval: Duration = Duration.ofSeconds(5),
    private val processBatch: (List<T>) -> Unit,
    private val onError: ((List<T>, Exception) -> Unit)? = null,
) {
    companion object {
        private val logger = Logger.getLogger(BatchProcessor::class.java)
    }

    private val buffer = ConcurrentLinkedQueue<T>()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val isRunning = AtomicBoolean(false)
    private val processedCount = AtomicLong(0)
    private val failedCount = AtomicLong(0)

    init {
        require(batchSize > 0) { "batchSize must be positive" }
        require(!flushInterval.isNegative && !flushInterval.isZero) {
            "flushInterval must be positive"
        }
    }

    /**
     * Start the batch processor
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(
                { flushIfNeeded() },
                flushInterval.toMillis(),
                flushInterval.toMillis(),
                TimeUnit.MILLISECONDS,
            )
            logger.info(
                "BatchProcessor started: batchSize=$batchSize, flushInterval=${flushInterval.toMillis()}ms",
            )
        }
    }

    /**
     * Stop the batch processor
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            scheduler.shutdown()
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow()
                }
            } catch (e: InterruptedException) {
                scheduler.shutdownNow()
                Thread.currentThread().interrupt()
            }

            // Flush remaining items
            flush()

            logger.info("BatchProcessor stopped")
        }
    }

    /**
     * Add item to batch
     */
    fun add(item: T) {
        buffer.add(item)
        logger.trace("Item added to batch buffer (size: ${buffer.size})")

        // Check if we should flush based on size
        if (buffer.size >= batchSize) {
            flush()
        }
    }

    /**
     * Add multiple items to batch
     */
    fun addAll(items: Collection<T>) {
        buffer.addAll(items)
        logger.trace("${items.size} items added to batch buffer (size: ${buffer.size})")

        // Check if we should flush based on size
        if (buffer.size >= batchSize) {
            flush()
        }
    }

    /**
     * Flush buffer if needed (called by scheduler)
     */
    private fun flushIfNeeded() {
        if (buffer.isNotEmpty()) {
            logger.debug("Time-based flush triggered (size: ${buffer.size})")
            flush()
        }
    }

    /**
     * Force flush current batch
     */
    fun flush() {
        val batch = mutableListOf<T>()

        // Drain buffer
        while (true) {
            val item = buffer.poll() ?: break
            batch.add(item)
        }

        if (batch.isEmpty()) {
            return
        }

        logger.debug("Flushing batch of ${batch.size} items")

        try {
            processBatch(batch)
            processedCount.addAndGet(batch.size.toLong())
            logger.info("Successfully processed batch of ${batch.size} items")
        } catch (e: Exception) {
            failedCount.addAndGet(batch.size.toLong())
            logger.error("Failed to process batch of ${batch.size} items", e)
            onError?.invoke(batch, e)
        }
    }

    /**
     * Get current buffer size
     */
    fun getBufferSize(): Int = buffer.size

    /**
     * Get statistics
     */
    fun getStats(): BatchProcessorStats =
        BatchProcessorStats(
            bufferSize = buffer.size,
            processedCount = processedCount.get(),
            failedCount = failedCount.get(),
            isRunning = isRunning.get(),
            batchSize = batchSize,
            flushIntervalMs = flushInterval.toMillis(),
        )

    /**
     * Check if processor is running
     */
    fun isRunning(): Boolean = isRunning.get()
}

/**
 * Batch processor statistics
 */
data class BatchProcessorStats(
    val bufferSize: Int,
    val processedCount: Long,
    val failedCount: Long,
    val isRunning: Boolean,
    val batchSize: Int,
    val flushIntervalMs: Long,
)
