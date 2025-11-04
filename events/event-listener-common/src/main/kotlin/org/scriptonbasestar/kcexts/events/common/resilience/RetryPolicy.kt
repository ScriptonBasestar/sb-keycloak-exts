package org.scriptonbasestar.kcexts.events.common.resilience

import org.jboss.logging.Logger
import java.time.Duration
import kotlin.math.min
import kotlin.math.pow

/**
 * Retry policy for event listeners with configurable backoff strategies
 */
class RetryPolicy(
    private val maxAttempts: Int = 3,
    private val initialDelay: Duration = Duration.ofMillis(100),
    private val maxDelay: Duration = Duration.ofSeconds(10),
    private val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
    private val multiplier: Double = 2.0,
    private val retryableExceptions: Set<Class<out Exception>> = setOf(Exception::class.java),
) {
    companion object {
        private val logger = Logger.getLogger(RetryPolicy::class.java)
    }

    enum class BackoffStrategy {
        FIXED,
        LINEAR,
        EXPONENTIAL,
        EXPONENTIAL_JITTER,
    }

    /**
     * Execute operation with retry logic
     */
    fun <T> execute(
        operation: () -> T,
        onRetry: ((attempt: Int, exception: Exception, delay: Duration) -> Unit)? = null,
    ): T {
        var lastException: Exception? = null
        var attempt = 0

        while (attempt < maxAttempts) {
            attempt++
            try {
                logger.trace("Executing operation (attempt $attempt/$maxAttempts)")
                return operation()
            } catch (e: Exception) {
                lastException = e

                if (!isRetryable(e)) {
                    logger.warn("Non-retryable exception encountered: ${e.javaClass.simpleName}")
                    throw e
                }

                if (attempt >= maxAttempts) {
                    logger.warn("Max retry attempts ($maxAttempts) reached")
                    break
                }

                val delay = calculateDelay(attempt)
                logger.info(
                    "Operation failed (attempt $attempt/$maxAttempts), " +
                        "retrying in ${delay.toMillis()}ms: ${e.message}",
                )

                onRetry?.invoke(attempt, e, delay)

                try {
                    Thread.sleep(delay.toMillis())
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    logger.warn("Retry interrupted")
                    throw ie
                }
            }
        }

        throw RetryExhaustedException(
            "Operation failed after $maxAttempts attempts",
            lastException,
        )
    }

    /**
     * Execute operation with retry logic (suspend function version)
     */
    suspend fun <T> executeSuspend(
        operation: suspend () -> T,
        onRetry: (suspend (attempt: Int, exception: Exception, delay: Duration) -> Unit)? = null,
    ): T {
        var lastException: Exception? = null
        var attempt = 0

        while (attempt < maxAttempts) {
            attempt++
            try {
                logger.trace("Executing operation (attempt $attempt/$maxAttempts)")
                return operation()
            } catch (e: Exception) {
                lastException = e

                if (!isRetryable(e)) {
                    logger.warn("Non-retryable exception encountered: ${e.javaClass.simpleName}")
                    throw e
                }

                if (attempt >= maxAttempts) {
                    logger.warn("Max retry attempts ($maxAttempts) reached")
                    break
                }

                val delay = calculateDelay(attempt)
                logger.info(
                    "Operation failed (attempt $attempt/$maxAttempts), " +
                        "retrying in ${delay.toMillis()}ms: ${e.message}",
                )

                onRetry?.invoke(attempt, e, delay)

                kotlinx.coroutines.delay(delay.toMillis())
            }
        }

        throw RetryExhaustedException(
            "Operation failed after $maxAttempts attempts",
            lastException,
        )
    }

    /**
     * Check if exception is retryable
     */
    private fun isRetryable(exception: Exception): Boolean =
        retryableExceptions.any { it.isInstance(exception) }

    /**
     * Calculate delay based on backoff strategy
     */
    private fun calculateDelay(attempt: Int): Duration {
        val delay =
            when (backoffStrategy) {
                BackoffStrategy.FIXED -> initialDelay

                BackoffStrategy.LINEAR -> {
                    val millis = initialDelay.toMillis() * attempt
                    Duration.ofMillis(millis)
                }

                BackoffStrategy.EXPONENTIAL -> {
                    val millis = initialDelay.toMillis() * multiplier.pow(attempt - 1).toLong()
                    Duration.ofMillis(millis)
                }

                BackoffStrategy.EXPONENTIAL_JITTER -> {
                    val baseDelay = initialDelay.toMillis() * multiplier.pow(attempt - 1).toLong()
                    val jitter = (Math.random() * baseDelay * 0.1).toLong() // 10% jitter
                    Duration.ofMillis(baseDelay + jitter)
                }
            }

        // Cap at maxDelay
        return if (delay > maxDelay) maxDelay else delay
    }

    /**
     * Get retry policy configuration
     */
    fun getConfig(): RetryConfig =
        RetryConfig(
            maxAttempts = maxAttempts,
            initialDelay = initialDelay,
            maxDelay = maxDelay,
            backoffStrategy = backoffStrategy,
            multiplier = multiplier,
            retryableExceptions = retryableExceptions,
        )
}

/**
 * Exception thrown when all retry attempts are exhausted
 */
class RetryExhaustedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Retry policy configuration data class
 */
data class RetryConfig(
    val maxAttempts: Int,
    val initialDelay: Duration,
    val maxDelay: Duration,
    val backoffStrategy: RetryPolicy.BackoffStrategy,
    val multiplier: Double,
    val retryableExceptions: Set<Class<out Exception>>,
)

/**
 * Builder for RetryPolicy
 */
class RetryPolicyBuilder {
    private var maxAttempts: Int = 3
    private var initialDelay: Duration = Duration.ofMillis(100)
    private var maxDelay: Duration = Duration.ofSeconds(10)
    private var backoffStrategy: RetryPolicy.BackoffStrategy = RetryPolicy.BackoffStrategy.EXPONENTIAL
    private var multiplier: Double = 2.0
    private var retryableExceptions: MutableSet<Class<out Exception>> = mutableSetOf(Exception::class.java)

    fun maxAttempts(attempts: Int) =
        apply {
            require(attempts > 0) { "maxAttempts must be positive" }
            this.maxAttempts = attempts
        }

    fun initialDelay(delay: Duration) =
        apply {
            require(!delay.isNegative) { "initialDelay must be non-negative" }
            this.initialDelay = delay
        }

    fun maxDelay(delay: Duration) =
        apply {
            require(!delay.isNegative) { "maxDelay must be non-negative" }
            this.maxDelay = delay
        }

    fun backoffStrategy(strategy: RetryPolicy.BackoffStrategy) =
        apply {
            this.backoffStrategy = strategy
        }

    fun multiplier(mult: Double) =
        apply {
            require(mult >= 1.0) { "multiplier must be >= 1.0" }
            this.multiplier = mult
        }

    fun retryOn(vararg exceptions: Class<out Exception>) =
        apply {
            this.retryableExceptions = exceptions.toMutableSet()
        }

    fun retryOn(exceptions: Collection<Class<out Exception>>) =
        apply {
            this.retryableExceptions = exceptions.toMutableSet()
        }

    fun build(): RetryPolicy =
        RetryPolicy(
            maxAttempts = maxAttempts,
            initialDelay = initialDelay,
            maxDelay = maxDelay,
            backoffStrategy = backoffStrategy,
            multiplier = multiplier,
            retryableExceptions = retryableExceptions,
        )
}
