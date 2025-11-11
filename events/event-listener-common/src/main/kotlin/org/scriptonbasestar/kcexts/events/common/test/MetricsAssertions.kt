package org.scriptonbasestar.kcexts.events.common.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Common assertions for event metrics testing.
 *
 * Provides reusable assertion methods for validating metrics behavior
 * across different event listener implementations.
 */
object MetricsAssertions {
    /**
     * Assert that metrics show successful event processing.
     *
     * @param totalSent Total events sent
     * @param totalFailed Total events failed
     * @param minSuccessCount Minimum expected successful events (default: 1)
     */
    fun assertSuccessfulMetrics(
        totalSent: Long,
        totalFailed: Long,
        minSuccessCount: Long = 1,
    ) {
        assertTrue(totalSent >= minSuccessCount, "Expected at least $minSuccessCount sent events, got $totalSent")
        assertEquals(0L, totalFailed, "Expected no failed events, got $totalFailed")
    }

    /**
     * Assert that metrics show failed event processing.
     *
     * @param totalSent Total events sent
     * @param totalFailed Total events failed
     * @param minFailureCount Minimum expected failed events (default: 1)
     */
    fun assertFailedMetrics(
        totalSent: Long,
        totalFailed: Long,
        minFailureCount: Long = 1,
    ) {
        assertTrue(totalFailed >= minFailureCount, "Expected at least $minFailureCount failed events, got $totalFailed")
    }

    /**
     * Assert that metrics summary contains expected values.
     *
     * @param summary Metrics summary object
     * @param expectedSent Expected sent count
     * @param expectedFailed Expected failed count
     */
    fun assertMetricsSummary(
        summary: Any,
        expectedSent: Long? = null,
        expectedFailed: Long? = null,
    ) {
        val summaryClass = summary::class.java

        expectedSent?.let {
            val totalSent =
                try {
                    summaryClass.getMethod("getTotalSent").invoke(summary) as Long
                } catch (e: NoSuchMethodException) {
                    summaryClass.getDeclaredField("totalSent").apply { isAccessible = true }.getLong(summary)
                }
            assertEquals(it, totalSent, "Total sent events mismatch")
        }

        expectedFailed?.let {
            val totalFailed =
                try {
                    summaryClass.getMethod("getTotalFailed").invoke(summary) as Long
                } catch (e: NoSuchMethodException) {
                    summaryClass.getDeclaredField("totalFailed").apply { isAccessible = true }.getLong(summary)
                }
            assertEquals(it, totalFailed, "Total failed events mismatch")
        }
    }

    /**
     * Assert that average latency is within acceptable range.
     *
     * @param averageLatencyMs Average latency in milliseconds
     * @param maxAcceptableMs Maximum acceptable latency (default: 1000ms)
     */
    fun assertLatencyWithinRange(
        averageLatencyMs: Double,
        maxAcceptableMs: Long = 1000,
    ) {
        assertTrue(
            averageLatencyMs <= maxAcceptableMs,
            "Average latency $averageLatencyMs ms exceeds maximum $maxAcceptableMs ms",
        )
    }

    /**
     * Assert that event rate is within expected range.
     *
     * @param eventsPerSecond Events processed per second
     * @param minExpectedRate Minimum expected rate (default: 1)
     * @param maxExpectedRate Maximum expected rate (default: 10000)
     */
    fun assertEventRateWithinRange(
        eventsPerSecond: Double,
        minExpectedRate: Double = 1.0,
        maxExpectedRate: Double = 10000.0,
    ) {
        assertTrue(
            eventsPerSecond >= minExpectedRate,
            "Event rate $eventsPerSecond/s below minimum $minExpectedRate/s",
        )
        assertTrue(
            eventsPerSecond <= maxExpectedRate,
            "Event rate $eventsPerSecond/s exceeds maximum $maxExpectedRate/s",
        )
    }
}
