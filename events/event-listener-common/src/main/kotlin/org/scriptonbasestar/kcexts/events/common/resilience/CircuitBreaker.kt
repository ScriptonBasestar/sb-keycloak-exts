package org.scriptonbasestar.kcexts.events.common.resilience

import org.jboss.logging.Logger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Circuit Breaker pattern implementation for event listeners
 *
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Failure threshold reached, requests are blocked
 * - HALF_OPEN: Testing if service recovered, limited requests allowed
 */
class CircuitBreaker(
    private val name: String,
    private val failureThreshold: Int = 5,
    private val successThreshold: Int = 2,
    private val openTimeout: Duration = Duration.ofSeconds(60),
    private val halfOpenTimeout: Duration = Duration.ofSeconds(30),
) {
    companion object {
        private val logger = Logger.getLogger(CircuitBreaker::class.java)
    }

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastStateChange = AtomicReference(Instant.now())
    private val lastFailureTime = AtomicReference<Instant?>(null)

    enum class State {
        CLOSED,
        OPEN,
        HALF_OPEN,
    }

    /**
     * Execute operation with circuit breaker protection
     */
    fun <T> execute(operation: () -> T): T {
        when (getCurrentState()) {
            State.OPEN -> {
                logger.debug("Circuit breaker [$name] is OPEN, rejecting request")
                throw CircuitBreakerOpenException("Circuit breaker is OPEN for $name")
            }
            State.HALF_OPEN -> {
                logger.trace("Circuit breaker [$name] is HALF_OPEN, allowing test request")
                return executeInHalfOpen(operation)
            }
            State.CLOSED -> {
                logger.trace("Circuit breaker [$name] is CLOSED, allowing request")
                return executeInClosed(operation)
            }
        }
    }

    /**
     * Execute operation when circuit is closed
     */
    private fun <T> executeInClosed(operation: () -> T): T =
        try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure(e)
            throw e
        }

    /**
     * Execute operation when circuit is half-open
     */
    private fun <T> executeInHalfOpen(operation: () -> T): T =
        try {
            val result = operation()
            onSuccessInHalfOpen()
            result
        } catch (e: Exception) {
            onFailureInHalfOpen(e)
            throw e
        }

    /**
     * Get current state, considering timeout transitions
     */
    private fun getCurrentState(): State {
        val currentState = state.get()
        val now = Instant.now()

        return when (currentState) {
            State.OPEN -> {
                val timeSinceStateChange = Duration.between(lastStateChange.get(), now)
                if (timeSinceStateChange >= openTimeout) {
                    transitionTo(State.HALF_OPEN)
                    State.HALF_OPEN
                } else {
                    State.OPEN
                }
            }
            State.HALF_OPEN -> {
                val timeSinceStateChange = Duration.between(lastStateChange.get(), now)
                if (timeSinceStateChange >= halfOpenTimeout) {
                    logger.warn("Circuit breaker [$name] HALF_OPEN timeout, reopening circuit")
                    transitionTo(State.OPEN)
                    State.OPEN
                } else {
                    State.HALF_OPEN
                }
            }
            State.CLOSED -> State.CLOSED
        }
    }

    /**
     * Handle successful execution in CLOSED state
     */
    private fun onSuccess() {
        failureCount.set(0)
        logger.trace("Circuit breaker [$name] success, failure count reset")
    }

    /**
     * Handle failed execution in CLOSED state
     */
    private fun onFailure(exception: Exception) {
        val failures = failureCount.incrementAndGet()
        lastFailureTime.set(Instant.now())

        logger.warn("Circuit breaker [$name] failure ($failures/$failureThreshold): ${exception.message}")

        if (failures >= failureThreshold) {
            transitionTo(State.OPEN)
        }
    }

    /**
     * Handle successful execution in HALF_OPEN state
     */
    private fun onSuccessInHalfOpen() {
        val successes = successCount.incrementAndGet()
        logger.info("Circuit breaker [$name] success in HALF_OPEN ($successes/$successThreshold)")

        if (successes >= successThreshold) {
            transitionTo(State.CLOSED)
        }
    }

    /**
     * Handle failed execution in HALF_OPEN state
     */
    private fun onFailureInHalfOpen(exception: Exception) {
        logger.warn("Circuit breaker [$name] failure in HALF_OPEN, reopening: ${exception.message}")
        transitionTo(State.OPEN)
    }

    /**
     * Transition to a new state
     */
    private fun transitionTo(newState: State) {
        val oldState = state.getAndSet(newState)
        lastStateChange.set(Instant.now())

        when (newState) {
            State.CLOSED -> {
                failureCount.set(0)
                successCount.set(0)
                logger.info("Circuit breaker [$name] transitioned: $oldState -> CLOSED")
            }
            State.OPEN -> {
                successCount.set(0)
                logger.warn("Circuit breaker [$name] transitioned: $oldState -> OPEN")
            }
            State.HALF_OPEN -> {
                successCount.set(0)
                logger.info("Circuit breaker [$name] transitioned: $oldState -> HALF_OPEN")
            }
        }
    }

    /**
     * Get current circuit breaker state
     */
    fun getState(): State = getCurrentState()

    /**
     * Get current metrics
     */
    fun getMetrics(): CircuitBreakerMetrics =
        CircuitBreakerMetrics(
            name = name,
            state = getCurrentState(),
            failureCount = failureCount.get(),
            successCount = successCount.get(),
            lastFailureTime = lastFailureTime.get(),
            lastStateChange = lastStateChange.get(),
        )

    /**
     * Manually reset circuit breaker to CLOSED state
     */
    fun reset() {
        logger.info("Circuit breaker [$name] manually reset to CLOSED")
        transitionTo(State.CLOSED)
    }

    /**
     * Manually trip circuit breaker to OPEN state
     */
    fun trip() {
        logger.warn("Circuit breaker [$name] manually tripped to OPEN")
        transitionTo(State.OPEN)
    }
}

/**
 * Exception thrown when circuit breaker is OPEN
 */
class CircuitBreakerOpenException(message: String) : RuntimeException(message)

/**
 * Circuit breaker metrics data class
 */
data class CircuitBreakerMetrics(
    val name: String,
    val state: CircuitBreaker.State,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Instant?,
    val lastStateChange: Instant,
)
