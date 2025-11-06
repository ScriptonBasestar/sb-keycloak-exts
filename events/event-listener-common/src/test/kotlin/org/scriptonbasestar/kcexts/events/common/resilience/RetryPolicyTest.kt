package org.scriptonbasestar.kcexts.events.common.resilience

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetryPolicyTest {
    @Test
    fun `should succeed on first attempt`() {
        val policy = RetryPolicy(maxAttempts = 3)
        var callCount = 0

        val result =
            policy.execute<String>(
                operation = {
                    callCount++
                    "success"
                },
            )

        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `should retry on failure and succeed`() {
        val policy = RetryPolicy(maxAttempts = 3, initialDelay = Duration.ofMillis(10))
        var callCount = 0

        val result =
            policy.execute<String>(
                operation = {
                    callCount++
                    if (callCount < 3) throw RuntimeException("temporary failure")
                    "success"
                },
            )

        assertEquals("success", result)
        assertEquals(3, callCount)
    }

    @Test
    fun `should throw RetryExhaustedException after max attempts`() {
        val policy = RetryPolicy(maxAttempts = 3, initialDelay = Duration.ofMillis(10))
        var callCount = 0

        val exception =
            assertThrows<RetryExhaustedException> {
                policy.execute<String>(
                    operation = {
                        callCount++
                        throw RuntimeException("persistent failure")
                    },
                )
            }

        assertEquals(3, callCount)
        assertTrue(exception.message!!.contains("3 attempts"))
        assertTrue(exception.cause is RuntimeException)
    }

    @Test
    fun `should not retry non-retryable exceptions`() {
        val policy =
            RetryPolicy(
                maxAttempts = 3,
                retryableExceptions = setOf(IOException::class.java),
            )
        var callCount = 0

        assertThrows<IllegalArgumentException> {
            policy.execute<String>(
                operation = {
                    callCount++
                    throw IllegalArgumentException("non-retryable")
                },
            )
        }

        assertEquals(1, callCount) // Should not retry
    }

    @Test
    fun `should retry only retryable exceptions`() {
        val policy =
            RetryPolicy(
                maxAttempts = 3,
                initialDelay = Duration.ofMillis(10),
                retryableExceptions = setOf(IOException::class.java),
            )
        var callCount = 0

        val result =
            policy.execute<String>(
                operation = {
                    callCount++
                    when (callCount) {
                        1, 2 -> throw IOException("retryable failure")
                        else -> "success"
                    }
                },
            )

        assertEquals("success", result)
        assertEquals(3, callCount)
    }

    @Test
    fun `should use fixed backoff strategy`() {
        val policy =
            RetryPolicy(
                maxAttempts = 3,
                initialDelay = Duration.ofMillis(100),
                backoffStrategy = RetryPolicy.BackoffStrategy.FIXED,
            )

        val delays = mutableListOf<Long>()

        assertThrows<RetryExhaustedException> {
            policy.execute<String>(
                operation = { throw RuntimeException("test") },
                onRetry = { _, _, delay ->
                    delays.add(delay.toMillis())
                },
            )
        }

        // All delays should be ~100ms (fixed)
        assertEquals(2, delays.size) // 2 retries for 3 attempts
        delays.forEach { delay ->
            assertEquals(100L, delay)
        }
    }

    @Test
    fun `should use linear backoff strategy`() {
        val policy =
            RetryPolicy(
                maxAttempts = 4,
                initialDelay = Duration.ofMillis(100),
                backoffStrategy = RetryPolicy.BackoffStrategy.LINEAR,
            )

        val delays = mutableListOf<Long>()

        assertThrows<RetryExhaustedException> {
            policy.execute<String>(
                operation = { throw RuntimeException("test") },
                onRetry = { _, _, delay ->
                    delays.add(delay.toMillis())
                },
            )
        }

        // Delays should be 100, 200, 300 (linear)
        assertEquals(listOf(100L, 200L, 300L), delays)
    }

    @Test
    fun `should use exponential backoff strategy`() {
        val policy =
            RetryPolicy(
                maxAttempts = 4,
                initialDelay = Duration.ofMillis(100),
                backoffStrategy = RetryPolicy.BackoffStrategy.EXPONENTIAL,
                multiplier = 2.0,
            )

        val delays = mutableListOf<Long>()

        assertThrows<RetryExhaustedException> {
            policy.execute<String>(
                operation = { throw RuntimeException("test") },
                onRetry = { _, _, delay ->
                    delays.add(delay.toMillis())
                },
            )
        }

        // Delays should be 100, 200, 400 (exponential with multiplier 2.0)
        assertEquals(listOf(100L, 200L, 400L), delays)
    }

    @Test
    fun `should cap delay at maxDelay`() {
        val policy =
            RetryPolicy(
                maxAttempts = 5,
                initialDelay = Duration.ofMillis(100),
                maxDelay = Duration.ofMillis(500),
                backoffStrategy = RetryPolicy.BackoffStrategy.EXPONENTIAL,
                multiplier = 2.0,
            )

        val delays = mutableListOf<Long>()

        assertThrows<RetryExhaustedException> {
            policy.execute<String>(
                operation = { throw RuntimeException("test") },
                onRetry = { _, _, delay ->
                    delays.add(delay.toMillis())
                },
            )
        }

        // Delays: 100, 200, 400, 500 (capped), not 800
        assertEquals(listOf(100L, 200L, 400L, 500L), delays)
        assertTrue(delays.all { it <= 500L })
    }

    @Test
    fun `should invoke onRetry callback`() {
        val policy = RetryPolicy(maxAttempts = 3, initialDelay = Duration.ofMillis(10))

        val retryAttempts = mutableListOf<Int>()
        val retryExceptions = mutableListOf<String>()

        assertThrows<RetryExhaustedException> {
            policy.execute<String>(
                operation = { throw RuntimeException("test failure") },
                onRetry = { attempt, exception, _ ->
                    retryAttempts.add(attempt)
                    retryExceptions.add(exception.message ?: "")
                },
            )
        }

        assertEquals(listOf(1, 2), retryAttempts) // 2 retries for 3 attempts
        assertEquals(listOf("test failure", "test failure"), retryExceptions)
    }

    @Test
    fun `should build retry policy with builder`() {
        val policy =
            RetryPolicyBuilder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(50))
                .maxDelay(Duration.ofSeconds(5))
                .backoffStrategy(RetryPolicy.BackoffStrategy.EXPONENTIAL_JITTER)
                .multiplier(3.0)
                .retryOn(IOException::class.java, RuntimeException::class.java)
                .build()

        val config = policy.getConfig()
        assertEquals(5, config.maxAttempts)
        assertEquals(Duration.ofMillis(50), config.initialDelay)
        assertEquals(Duration.ofSeconds(5), config.maxDelay)
        assertEquals(RetryPolicy.BackoffStrategy.EXPONENTIAL_JITTER, config.backoffStrategy)
        assertEquals(3.0, config.multiplier)
        assertEquals(2, config.retryableExceptions.size)
    }

    @Test
    fun `should validate builder parameters`() {
        assertThrows<IllegalArgumentException> {
            RetryPolicyBuilder().maxAttempts(0).build()
        }

        assertThrows<IllegalArgumentException> {
            RetryPolicyBuilder().initialDelay(Duration.ofMillis(-1)).build()
        }

        assertThrows<IllegalArgumentException> {
            RetryPolicyBuilder().multiplier(0.5).build()
        }
    }

    @Test
    fun `should handle interrupted exception`() {
        val policy = RetryPolicy(maxAttempts = 3, initialDelay = Duration.ofSeconds(10))
        var callCount = 0

        // Interrupt the thread
        Thread.currentThread().interrupt()

        assertThrows<InterruptedException> {
            policy.execute<String>(
                operation = {
                    callCount++
                    throw RuntimeException("test")
                },
            )
        }

        assertEquals(1, callCount) // Should fail on first retry sleep
        assertTrue(Thread.interrupted()) // Clear interrupted flag
    }
}
