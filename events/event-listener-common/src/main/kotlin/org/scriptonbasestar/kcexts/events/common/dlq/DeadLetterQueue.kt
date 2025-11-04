package org.scriptonbasestar.kcexts.events.common.dlq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jboss.logging.Logger
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Dead Letter Queue for failed events
 *
 * Stores events that failed processing after all retry attempts
 * Supports both in-memory and file-based persistence
 */
class DeadLetterQueue(
    private val maxSize: Int = 10000,
    private val persistToFile: Boolean = false,
    private val persistencePath: String = "./dlq",
    private val objectMapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
) {
    companion object {
        private val logger = Logger.getLogger(DeadLetterQueue::class.java)
        private val DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }

    private val queue = ConcurrentLinkedQueue<DeadLetterEvent>()
    private val droppedCount = AtomicLong(0)

    init {
        if (persistToFile) {
            ensurePersistenceDirectory()
        }
    }

    /**
     * Add event to dead letter queue
     */
    fun add(
        eventType: String,
        eventData: String,
        realm: String,
        destination: String,
        failureReason: String,
        attemptCount: Int,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val dlEvent =
            DeadLetterEvent(
                id = generateId(),
                timestamp = Instant.now(),
                eventType = eventType,
                eventData = eventData,
                realm = realm,
                destination = destination,
                failureReason = failureReason,
                attemptCount = attemptCount,
                metadata = metadata,
            )

        if (queue.size >= maxSize) {
            val oldest = queue.poll()
            if (oldest != null) {
                droppedCount.incrementAndGet()
                logger.warn("DLQ at max capacity ($maxSize), dropped oldest event: ${oldest.id}")
            }
        }

        queue.add(dlEvent)
        logger.info(
            "Event added to DLQ: id=${dlEvent.id}, type=$eventType, " +
                "realm=$realm, reason=$failureReason, attempts=$attemptCount",
        )

        if (persistToFile) {
            persistEvent(dlEvent)
        }
    }

    /**
     * Get all events in the queue
     */
    fun getAll(): List<DeadLetterEvent> = queue.toList()

    /**
     * Get events by type
     */
    fun getByType(eventType: String): List<DeadLetterEvent> = queue.filter { it.eventType == eventType }

    /**
     * Get events by realm
     */
    fun getByRealm(realm: String): List<DeadLetterEvent> = queue.filter { it.realm == realm }

    /**
     * Get event by ID
     */
    fun getById(id: String): DeadLetterEvent? = queue.find { it.id == id }

    /**
     * Remove event from queue
     */
    fun remove(id: String): Boolean {
        val removed = queue.removeIf { it.id == id }
        if (removed) {
            logger.info("Event removed from DLQ: id=$id")
        }
        return removed
    }

    /**
     * Retry event (remove from DLQ for reprocessing)
     */
    fun retry(id: String): DeadLetterEvent? {
        val event = queue.find { it.id == id }
        if (event != null && queue.remove(event)) {
            logger.info("Event marked for retry from DLQ: id=$id")
            return event
        }
        return null
    }

    /**
     * Clear all events
     */
    fun clear() {
        val size = queue.size
        queue.clear()
        logger.info("DLQ cleared: $size events removed")
    }

    /**
     * Get queue statistics
     */
    fun getStats(): DeadLetterQueueStats =
        DeadLetterQueueStats(
            currentSize = queue.size,
            maxSize = maxSize,
            droppedCount = droppedCount.get(),
            eventsByType = queue.groupingBy { it.eventType }.eachCount(),
            eventsByRealm = queue.groupingBy { it.realm }.eachCount(),
        )

    /**
     * Persist event to file
     */
    private fun persistEvent(event: DeadLetterEvent) {
        try {
            val fileName = "${event.id}.json"
            val file = File(persistencePath, fileName)
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, event)
            logger.debug("Event persisted to file: ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to persist event to file: ${event.id}", e)
        }
    }

    /**
     * Ensure persistence directory exists
     */
    private fun ensurePersistenceDirectory() {
        try {
            val dir = File(persistencePath)
            if (!dir.exists()) {
                dir.mkdirs()
                logger.info("Created DLQ persistence directory: ${dir.absolutePath}")
            }
        } catch (e: Exception) {
            logger.error("Failed to create DLQ persistence directory: $persistencePath", e)
        }
    }

    /**
     * Load events from persistence directory
     */
    fun loadFromPersistence(): Int {
        if (!persistToFile) {
            logger.warn("Persistence is not enabled")
            return 0
        }

        try {
            val dir = File(persistencePath)
            if (!dir.exists() || !dir.isDirectory) {
                logger.warn("Persistence directory does not exist: $persistencePath")
                return 0
            }

            val files = dir.listFiles { file -> file.extension == "json" } ?: emptyArray()
            var loadedCount = 0

            files.forEach { file ->
                try {
                    val event = objectMapper.readValue(file, DeadLetterEvent::class.java)
                    if (queue.size < maxSize) {
                        queue.add(event)
                        loadedCount++
                    } else {
                        logger.warn("DLQ at max capacity, skipping file: ${file.name}")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load event from file: ${file.name}", e)
                }
            }

            logger.info("Loaded $loadedCount events from persistence")
            return loadedCount
        } catch (e: Exception) {
            logger.error("Failed to load events from persistence", e)
            return 0
        }
    }

    /**
     * Generate unique event ID
     */
    private fun generateId(): String = "dlq-${System.currentTimeMillis()}-${System.nanoTime()}"
}

/**
 * Dead letter event data class
 */
data class DeadLetterEvent(
    val id: String,
    val timestamp: Instant,
    val eventType: String,
    val eventData: String,
    val realm: String,
    val destination: String,
    val failureReason: String,
    val attemptCount: Int,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Dead letter queue statistics
 */
data class DeadLetterQueueStats(
    val currentSize: Int,
    val maxSize: Int,
    val droppedCount: Long,
    val eventsByType: Map<String, Int>,
    val eventsByRealm: Map<String, Int>,
)
