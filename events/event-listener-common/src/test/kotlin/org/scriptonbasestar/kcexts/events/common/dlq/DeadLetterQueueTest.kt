package org.scriptonbasestar.kcexts.events.common.dlq

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeadLetterQueueTest {
    @Test
    fun `should add event to queue`() {
        val dlq = DeadLetterQueue(maxSize = 10)

        dlq.add(
            eventType = "LOGIN",
            eventData = """{"userId": "123"}""",
            realm = "master",
            destination = "events",
            failureReason = "Connection timeout",
            attemptCount = 3,
        )

        val stats = dlq.getStats()
        assertEquals(1, stats.currentSize)
        assertEquals(0L, stats.droppedCount)
    }

    @Test
    fun `should drop oldest event when at max capacity`() {
        val dlq = DeadLetterQueue(maxSize = 3)

        // Add 4 events (one more than max)
        repeat(4) { i ->
            dlq.add(
                eventType = "LOGIN",
                eventData = """{"userId": "$i"}""",
                realm = "master",
                destination = "events",
                failureReason = "Test failure",
                attemptCount = 3,
            )
        }

        val stats = dlq.getStats()
        assertEquals(3, stats.currentSize) // Should still be at max
        assertEquals(1L, stats.droppedCount) // One should be dropped
    }

    @Test
    fun `should get all events`() {
        val dlq = DeadLetterQueue()

        dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        dlq.add("LOGOUT", "{}", "master", "events", "Error", 2)

        val events = dlq.getAll()
        assertEquals(2, events.size)
    }

    @Test
    fun `should get events by type`() {
        val dlq = DeadLetterQueue()

        dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        dlq.add("LOGOUT", "{}", "master", "events", "Error", 2)
        dlq.add("LOGIN", "{}", "master", "events", "Error", 1)

        val loginEvents = dlq.getByType("LOGIN")
        assertEquals(2, loginEvents.size)
        assertTrue(loginEvents.all { it.eventType == "LOGIN" })
    }

    @Test
    fun `should get events by realm`() {
        val dlq = DeadLetterQueue()

        dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        dlq.add("LOGOUT", "{}", "test-realm", "events", "Error", 2)
        dlq.add("LOGIN", "{}", "master", "events", "Error", 1)

        val masterEvents = dlq.getByRealm("master")
        assertEquals(2, masterEvents.size)
        assertTrue(masterEvents.all { it.realm == "master" })
    }

    @Test
    fun `should get event by ID`() {
        val dlq = DeadLetterQueue()

        dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        val events = dlq.getAll()
        val firstEvent = events.first()

        val found = dlq.getById(firstEvent.id)
        assertNotNull(found)
        assertEquals(firstEvent.id, found.id)
    }

    @Test
    fun `should remove event by ID`() {
        val dlq = DeadLetterQueue()

        dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        val events = dlq.getAll()
        val eventId = events.first().id

        val removed = dlq.remove(eventId)
        assertTrue(removed)
        assertEquals(0, dlq.getAll().size)
    }

    @Test
    fun `should retry event`() {
        val dlq = DeadLetterQueue()

        dlq.add("LOGIN", """{"user":"test"}""", "master", "events", "Error", 3)
        val events = dlq.getAll()
        val eventId = events.first().id

        val retryEvent = dlq.retry(eventId)
        assertNotNull(retryEvent)
        assertEquals("LOGIN", retryEvent.eventType)
        assertEquals("""{"user":"test"}""", retryEvent.eventData)

        // Event should be removed from queue after retry
        assertEquals(0, dlq.getAll().size)
    }

    @Test
    fun `should return null when retrying non-existent event`() {
        val dlq = DeadLetterQueue()
        val retryEvent = dlq.retry("non-existent-id")
        assertNull(retryEvent)
    }

    @Test
    fun `should clear all events`() {
        val dlq = DeadLetterQueue()

        repeat(5) {
            dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        }

        assertEquals(5, dlq.getAll().size)

        dlq.clear()
        assertEquals(0, dlq.getAll().size)
    }

    @Test
    fun `should track statistics`() {
        val dlq = DeadLetterQueue(maxSize = 5)

        dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        dlq.add("LOGOUT", "{}", "test-realm", "events", "Error", 2)
        dlq.add("REGISTER", "{}", "master", "events", "Error", 1)

        val stats = dlq.getStats()
        assertEquals(4, stats.currentSize)
        assertEquals(5, stats.maxSize)
        assertEquals(0L, stats.droppedCount)
        assertEquals(2, stats.eventsByType["LOGIN"])
        assertEquals(1, stats.eventsByType["LOGOUT"])
        assertEquals(1, stats.eventsByType["REGISTER"])
        assertEquals(3, stats.eventsByRealm["master"])
        assertEquals(1, stats.eventsByRealm["test-realm"])
    }

    @Test
    fun `should persist and load events from file`(
        @TempDir tempDir: File,
    ) {
        val persistencePath = tempDir.absolutePath

        // Create DLQ with persistence
        val dlq1 =
            DeadLetterQueue(
                maxSize = 10,
                persistToFile = true,
                persistencePath = persistencePath,
            )

        // Add events
        dlq1.add("LOGIN", """{"user":"alice"}""", "master", "events", "Connection error", 3)
        dlq1.add("LOGOUT", """{"user":"bob"}""", "test-realm", "events", "Timeout", 2)

        // Create new DLQ instance and load from persistence
        val dlq2 =
            DeadLetterQueue(
                maxSize = 10,
                persistToFile = true,
                persistencePath = persistencePath,
            )

        val loadedCount = dlq2.loadFromPersistence()
        assertEquals(2, loadedCount)

        val events = dlq2.getAll()
        assertEquals(2, events.size)
    }

    @Test
    fun `should handle metadata`() {
        val dlq = DeadLetterQueue()

        dlq.add(
            eventType = "LOGIN",
            eventData = "{}",
            realm = "master",
            destination = "events",
            failureReason = "Error",
            attemptCount = 3,
            metadata = mapOf("source" to "kafka", "partition" to "0"),
        )

        val event = dlq.getAll().first()
        assertEquals("kafka", event.metadata["source"])
        assertEquals("0", event.metadata["partition"])
    }

    @Test
    fun `should generate unique IDs`() {
        val dlq = DeadLetterQueue()

        val ids = mutableSetOf<String>()
        repeat(100) {
            dlq.add("LOGIN", "{}", "master", "events", "Error", 3)
        }

        dlq.getAll().forEach { event ->
            ids.add(event.id)
        }

        // All IDs should be unique
        assertEquals(100, ids.size)
    }
}
