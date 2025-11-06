package org.scriptonbasestar.kcexts.events.common.test

import org.scriptonbasestar.kcexts.events.common.batch.BatchProcessor
import org.scriptonbasestar.kcexts.events.common.dlq.DeadLetterQueue
import org.scriptonbasestar.kcexts.events.common.resilience.CircuitBreaker
import org.scriptonbasestar.kcexts.events.common.resilience.RetryPolicy
import java.time.Duration

/**
 * Builders for creating test configurations with sensible defaults.
 *
 * Reduces boilerplate in test setup by providing pre-configured instances
 * of common components like CircuitBreaker, RetryPolicy, etc.
 */
object TestConfigurationBuilders {
    /**
     * Create a CircuitBreaker with test-friendly defaults.
     *
     * @param name Circuit breaker name (default: "test-circuit-breaker")
     * @param failureThreshold Failure threshold (default: 5)
     * @param successThreshold Success threshold (default: 1)
     * @param openTimeout Timeout for OPEN state (default: 30 seconds)
     * @return Configured CircuitBreaker instance
     */
    fun createCircuitBreaker(
        name: String = "test-circuit-breaker",
        failureThreshold: Int = 5,
        successThreshold: Int = 1,
        openTimeout: Duration = Duration.ofSeconds(30),
    ): CircuitBreaker =
        CircuitBreaker(
            name = name,
            failureThreshold = failureThreshold,
            successThreshold = successThreshold,
            openTimeout = openTimeout,
        )

    /**
     * Create a RetryPolicy with test-friendly defaults (no retries).
     *
     * @param maxAttempts Maximum retry attempts (default: 1 = no retries)
     * @param initialDelay Initial delay (default: 0ms)
     * @param maxDelay Maximum delay (default: 10ms)
     * @param backoffStrategy Backoff strategy (default: FIXED)
     * @return Configured RetryPolicy instance
     */
    fun createRetryPolicy(
        maxAttempts: Int = 1,
        initialDelay: Duration = Duration.ZERO,
        maxDelay: Duration = Duration.ofMillis(10),
        backoffStrategy: RetryPolicy.BackoffStrategy = RetryPolicy.BackoffStrategy.FIXED,
    ): RetryPolicy =
        RetryPolicy(
            maxAttempts = maxAttempts,
            initialDelay = initialDelay,
            maxDelay = maxDelay,
            backoffStrategy = backoffStrategy,
        )

    /**
     * Create a DeadLetterQueue with test-friendly defaults.
     *
     * @param maxSize Maximum queue size (default: 10)
     * @param persistToFile Whether to persist to file (default: false)
     * @param persistencePath File path for persistence (default: "./build/tmp/test-dlq")
     * @return Configured DeadLetterQueue instance
     */
    fun createDeadLetterQueue(
        maxSize: Int = 10,
        persistToFile: Boolean = false,
        persistencePath: String = "./build/tmp/test-dlq",
    ): DeadLetterQueue =
        DeadLetterQueue(
            maxSize = maxSize,
            persistToFile = persistToFile,
            persistencePath = persistencePath,
        )

    /**
     * Create a BatchProcessor with test-friendly defaults (no-op processing).
     *
     * @param batchSize Batch size (default: 10)
     * @param flushInterval Flush interval (default: 5 seconds)
     * @param processBatch Processing function (default: no-op)
     * @param onError Error handler (default: no-op)
     * @return Configured BatchProcessor instance
     */
    fun <T> createBatchProcessor(
        batchSize: Int = 10,
        flushInterval: Duration = Duration.ofSeconds(5),
        processBatch: (List<T>) -> Unit = { },
        onError: (List<T>, Exception) -> Unit = { _, _ -> },
    ): BatchProcessor<T> =
        BatchProcessor(
            batchSize = batchSize,
            flushInterval = flushInterval,
            processBatch = processBatch,
            onError = onError,
        )

    /**
     * Create a complete test environment with all resilience components.
     *
     * @return TestEnvironment with pre-configured components
     */
    fun createTestEnvironment(): TestEnvironment =
        TestEnvironment(
            circuitBreaker = createCircuitBreaker(),
            retryPolicy = createRetryPolicy(),
            deadLetterQueue = createDeadLetterQueue(),
        )

    /**
     * Container for test environment components
     */
    data class TestEnvironment(
        val circuitBreaker: CircuitBreaker,
        val retryPolicy: RetryPolicy,
        val deadLetterQueue: DeadLetterQueue,
    )
}
