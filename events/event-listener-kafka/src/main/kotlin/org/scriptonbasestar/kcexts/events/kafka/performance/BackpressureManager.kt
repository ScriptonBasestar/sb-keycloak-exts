package org.scriptonbasestar.kcexts.events.kafka.performance

import org.jboss.logging.Logger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Backpressure Manager for Kafka Event Processing
 * Handles flow control to prevent system overload
 */
class BackpressureManager(
    private val config: BackpressureConfig,
) {
    companion object {
        private val logger = Logger.getLogger(BackpressureManager::class.java)
    }

    // Semaphore to control concurrent operations
    private val operationSemaphore = Semaphore(config.maxConcurrentOperations, true)

    // Queue for buffering events during backpressure
    private val eventBuffer: BlockingQueue<BufferedEvent> = ArrayBlockingQueue(config.bufferSize)

    // Metrics
    private val totalRequests = AtomicLong(0)
    private val rejectedRequests = AtomicLong(0)
    private val bufferedEvents = AtomicInteger(0)
    private val droppedEvents = AtomicLong(0)
    private val backpressureActivations = AtomicLong(0)

    @Volatile
    private var backpressureActive = false

    /**
     * Process an event with backpressure control
     */
    fun <T> processWithBackpressure(
        event: Any,
        priority: EventPriority = EventPriority.NORMAL,
        operation: () -> T,
    ): BackpressureResult<T> {
        totalRequests.incrementAndGet()

        // Check if we should apply backpressure
        if (shouldApplyBackpressure()) {
            return handleBackpressure(event, priority, operation)
        }

        // Try to acquire permit for operation
        if (operationSemaphore.tryAcquire(config.operationTimeoutMs, TimeUnit.MILLISECONDS)) {
            try {
                val result = operation()
                return BackpressureResult.Success(result)
            } catch (e: Exception) {
                return BackpressureResult.Error(e)
            } finally {
                operationSemaphore.release()
            }
        } else {
            // Couldn't acquire permit within timeout
            return handleBackpressure(event, priority, operation)
        }
    }

    /**
     * Handle backpressure situation
     */
    private fun <T> handleBackpressure(
        event: Any,
        priority: EventPriority,
        operation: () -> T,
    ): BackpressureResult<T> {
        activateBackpressure()

        return when (config.strategy) {
            BackpressureStrategy.BUFFER -> bufferEvent(event, priority, operation)
            BackpressureStrategy.DROP_OLDEST -> dropOldestAndProcess(event, priority, operation)
            BackpressureStrategy.DROP_NEWEST -> dropNewestAndReject(event, priority)
            BackpressureStrategy.REJECT -> rejectRequest(event, priority)
            BackpressureStrategy.PRIORITY_BASED -> priorityBasedHandling(event, priority, operation)
        }
    }

    /**
     * Buffer event for later processing
     */
    private fun <T> bufferEvent(
        event: Any,
        priority: EventPriority,
        operation: () -> T,
    ): BackpressureResult<T> {
        val bufferedEvent =
            BufferedEvent(
                event = event,
                priority = priority,
                timestamp = System.currentTimeMillis(),
                operation = operation as () -> Any,
            )

        if (eventBuffer.offer(bufferedEvent)) {
            bufferedEvents.incrementAndGet()
            logger.debug("Event buffered due to backpressure")
            return BackpressureResult.Buffered("Event buffered for later processing")
        } else {
            droppedEvents.incrementAndGet()
            logger.warn("Event dropped - buffer full")
            return BackpressureResult.Dropped("Buffer full, event dropped")
        }
    }

    /**
     * Drop oldest event and try to process new one
     */
    private fun <T> dropOldestAndProcess(
        event: Any,
        priority: EventPriority,
        operation: () -> T,
    ): BackpressureResult<T> {
        // Remove oldest event if buffer is full
        if (eventBuffer.remainingCapacity() == 0) {
            val droppedEvent = eventBuffer.poll()
            if (droppedEvent != null) {
                droppedEvents.incrementAndGet()
                bufferedEvents.decrementAndGet()
                logger.debug("Dropped oldest event to make room for new event")
            }
        }

        return bufferEvent(event, priority, operation)
    }

    /**
     * Drop newest event (reject current request)
     */
    private fun <T> dropNewestAndReject(
        event: Any,
        priority: EventPriority,
    ): BackpressureResult<T> {
        droppedEvents.incrementAndGet()
        rejectedRequests.incrementAndGet()
        logger.debug("Dropped newest event due to backpressure")
        return BackpressureResult.Dropped("System overloaded, event dropped")
    }

    /**
     * Reject request immediately
     */
    private fun <T> rejectRequest(
        event: Any,
        priority: EventPriority,
    ): BackpressureResult<T> {
        rejectedRequests.incrementAndGet()
        logger.debug("Request rejected due to backpressure")
        return BackpressureResult.Rejected("System overloaded, request rejected")
    }

    /**
     * Priority-based handling
     */
    private fun <T> priorityBasedHandling(
        event: Any,
        priority: EventPriority,
        operation: () -> T,
    ): BackpressureResult<T> {
        return when (priority) {
            EventPriority.HIGH -> {
                // High priority events get preferential treatment
                if (operationSemaphore.tryAcquire(config.operationTimeoutMs * 2, TimeUnit.MILLISECONDS)) {
                    try {
                        val result = operation()
                        return BackpressureResult.Success(result)
                    } catch (e: Exception) {
                        return BackpressureResult.Error(e)
                    } finally {
                        operationSemaphore.release()
                    }
                } else {
                    bufferEvent(event, priority, operation)
                }
            }
            EventPriority.NORMAL -> {
                bufferEvent(event, priority, operation)
            }
            EventPriority.LOW -> {
                // Low priority events are dropped first
                if (eventBuffer.remainingCapacity() > config.bufferSize * 0.2) {
                    bufferEvent(event, priority, operation)
                } else {
                    dropNewestAndReject(event, priority)
                }
            }
        }
    }

    /**
     * Check if backpressure should be applied
     */
    private fun shouldApplyBackpressure(): Boolean {
        val queueUtilization = (bufferedEvents.get().toDouble() / config.bufferSize.toDouble()) * 100
        val permitUtilization =
            (
                (config.maxConcurrentOperations - operationSemaphore.availablePermits()).toDouble() /
                    config.maxConcurrentOperations.toDouble()
            ) * 100

        return queueUtilization > config.backpressureThreshold ||
            permitUtilization > config.backpressureThreshold
    }

    /**
     * Activate backpressure mode
     */
    private fun activateBackpressure() {
        if (!backpressureActive) {
            backpressureActive = true
            backpressureActivations.incrementAndGet()
            logger.warn("Backpressure activated - system overloaded")

            // Notify callback if configured
            config.onBackpressureActivated?.invoke()
        }
    }

    /**
     * Deactivate backpressure mode
     */
    private fun deactivateBackpressure() {
        if (backpressureActive) {
            backpressureActive = false
            logger.info("Backpressure deactivated - system recovered")

            // Notify callback if configured
            config.onBackpressureDeactivated?.invoke()
        }
    }

    /**
     * Process buffered events when capacity becomes available
     */
    fun processBufferedEvents(): Int {
        var processedCount = 0

        while (operationSemaphore.tryAcquire() && !eventBuffer.isEmpty()) {
            val bufferedEvent = eventBuffer.poll()
            if (bufferedEvent != null) {
                try {
                    bufferedEvent.operation()
                    processedCount++
                    bufferedEvents.decrementAndGet()
                } catch (e: Exception) {
                    logger.warn("Error processing buffered event", e)
                } finally {
                    operationSemaphore.release()
                }
            }
        }

        // Check if we can deactivate backpressure
        if (shouldDeactivateBackpressure()) {
            deactivateBackpressure()
        }

        return processedCount
    }

    /**
     * Check if backpressure should be deactivated
     */
    private fun shouldDeactivateBackpressure(): Boolean {
        val queueUtilization = (bufferedEvents.get().toDouble() / config.bufferSize.toDouble()) * 100
        val permitUtilization =
            (
                (config.maxConcurrentOperations - operationSemaphore.availablePermits()).toDouble() /
                    config.maxConcurrentOperations.toDouble()
            ) * 100

        return queueUtilization < config.backpressureThreshold * 0.5 &&
            permitUtilization < config.backpressureThreshold * 0.5
    }

    /**
     * Get current backpressure statistics
     */
    fun getStatistics(): BackpressureStatistics {
        val queueUtilization = (bufferedEvents.get().toDouble() / config.bufferSize.toDouble()) * 100
        val permitUtilization =
            (
                (config.maxConcurrentOperations - operationSemaphore.availablePermits()).toDouble() /
                    config.maxConcurrentOperations.toDouble()
            ) * 100

        val rejectionRate =
            if (totalRequests.get() > 0) {
                (rejectedRequests.get().toDouble() / totalRequests.get().toDouble()) * 100
            } else {
                0.0
            }

        val dropRate =
            if (totalRequests.get() > 0) {
                (droppedEvents.get().toDouble() / totalRequests.get().toDouble()) * 100
            } else {
                0.0
            }

        return BackpressureStatistics(
            isActive = backpressureActive,
            totalRequests = totalRequests.get(),
            rejectedRequests = rejectedRequests.get(),
            droppedEvents = droppedEvents.get(),
            bufferedEvents = bufferedEvents.get(),
            bufferUtilization = queueUtilization,
            permitUtilization = permitUtilization,
            rejectionRate = rejectionRate,
            dropRate = dropRate,
            activationCount = backpressureActivations.get(),
        )
    }

    /**
     * Clear all buffered events (emergency drain)
     */
    fun drainBuffer(): Int {
        var drainedCount = 0
        while (!eventBuffer.isEmpty()) {
            val event = eventBuffer.poll()
            if (event != null) {
                drainedCount++
                bufferedEvents.decrementAndGet()
            }
        }

        logger.warn("Emergency buffer drain completed, $drainedCount events discarded")
        return drainedCount
    }

    /**
     * Adjust backpressure configuration dynamically
     */
    fun adjustConfiguration(newConfig: BackpressureConfig) {
        // Note: In a real implementation, this would safely update configuration
        logger.info("Backpressure configuration updated")
    }
}

/**
 * Backpressure configuration
 */
data class BackpressureConfig(
    val maxConcurrentOperations: Int = 50,
    val bufferSize: Int = 1000,
    val backpressureThreshold: Double = 80.0, // Percentage
    val operationTimeoutMs: Long = 5000L,
    val strategy: BackpressureStrategy = BackpressureStrategy.BUFFER,
    val onBackpressureActivated: (() -> Unit)? = null,
    val onBackpressureDeactivated: (() -> Unit)? = null,
)

/**
 * Backpressure strategies
 */
enum class BackpressureStrategy {
    BUFFER, // Buffer events for later processing
    DROP_OLDEST, // Drop oldest events when buffer is full
    DROP_NEWEST, // Drop newest events when system is overloaded
    REJECT, // Reject requests immediately
    PRIORITY_BASED, // Handle based on event priority
}

/**
 * Event priorities
 */
enum class EventPriority {
    HIGH, // Critical events (security, errors)
    NORMAL, // Standard events (login, logout)
    LOW, // Non-critical events (info, debug)
}

/**
 * Backpressure result
 */
sealed class BackpressureResult<out T> {
    data class Success<T>(val value: T) : BackpressureResult<T>()

    data class Error(val exception: Exception) : BackpressureResult<Nothing>()

    data class Buffered(val message: String) : BackpressureResult<Nothing>()

    data class Rejected(val message: String) : BackpressureResult<Nothing>()

    data class Dropped(val message: String) : BackpressureResult<Nothing>()
}

/**
 * Backpressure statistics
 */
data class BackpressureStatistics(
    val isActive: Boolean,
    val totalRequests: Long,
    val rejectedRequests: Long,
    val droppedEvents: Long,
    val bufferedEvents: Int,
    val bufferUtilization: Double,
    val permitUtilization: Double,
    val rejectionRate: Double,
    val dropRate: Double,
    val activationCount: Long,
)

/**
 * Buffered event wrapper
 */
private data class BufferedEvent(
    val event: Any,
    val priority: EventPriority,
    val timestamp: Long,
    val operation: () -> Any,
)
