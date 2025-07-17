package org.scriptonbasestar.kcexts.events.kafka.performance

import org.jboss.logging.Logger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Circuit Breaker implementation for Kafka Event Listener
 * Prevents cascading failures and provides fallback mechanisms
 */
class CircuitBreaker(
    private val config: CircuitBreakerConfig
) {
    companion object {
        private val logger = Logger.getLogger(CircuitBreaker::class.java)
    }
    
    private val state = AtomicReference(CircuitBreakerState.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val halfOpenSuccessCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val lastStateChangeTime = AtomicLong(System.currentTimeMillis())
    
    // Sliding window for failure tracking
    private val recentCalls = ConcurrentLinkedQueue<CallRecord>()
    
    /**
     * Execute a call through the circuit breaker
     */
    fun <T> execute(operation: () -> T): T {
        val currentState = state.get()
        
        when (currentState) {
            CircuitBreakerState.CLOSED -> {
                return executeInClosedState(operation)
            }
            CircuitBreakerState.OPEN -> {
                return executeInOpenState(operation)
            }
            CircuitBreakerState.HALF_OPEN -> {
                return executeInHalfOpenState(operation)
            }
        }
    }
    
    /**
     * Execute operation when circuit is closed (normal operation)
     */
    private fun <T> executeInClosedState(operation: () -> T): T {
        return try {
            val result = operation()
            recordSuccess()
            result
        } catch (e: Exception) {
            recordFailure(e)
            throw e
        }
    }
    
    /**
     * Execute operation when circuit is open (failing fast)
     */
    private fun <T> executeInOpenState(operation: () -> T): T {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFailure = currentTime - lastFailureTime.get()
        
        if (timeSinceLastFailure >= config.openTimeoutMs) {
            logger.info("Circuit breaker attempting recovery, moving to HALF_OPEN state")
            transitionToHalfOpen()
            return executeInHalfOpenState(operation)
        } else {
            val timeRemaining = config.openTimeoutMs - timeSinceLastFailure
            logger.debug("Circuit breaker is OPEN, failing fast. Time remaining: ${timeRemaining}ms")
            throw CircuitBreakerOpenException("Circuit breaker is OPEN, time remaining: ${timeRemaining}ms")
        }
    }
    
    /**
     * Execute operation when circuit is half-open (testing recovery)
     */
    private fun <T> executeInHalfOpenState(operation: () -> T): T {
        return try {
            val result = operation()
            recordHalfOpenSuccess()
            result
        } catch (e: Exception) {
            recordHalfOpenFailure(e)
            throw e
        }
    }
    
    /**
     * Record a successful operation
     */
    private fun recordSuccess() {
        cleanupOldRecords()
        recentCalls.offer(CallRecord(System.currentTimeMillis(), true))
        successCount.incrementAndGet()
        
        // Reset failure count on success in closed state
        if (state.get() == CircuitBreakerState.CLOSED) {
            failureCount.set(0)
        }
    }
    
    /**
     * Record a failed operation
     */
    private fun recordFailure(exception: Exception) {
        cleanupOldRecords()
        recentCalls.offer(CallRecord(System.currentTimeMillis(), false))
        failureCount.incrementAndGet()
        lastFailureTime.set(System.currentTimeMillis())
        
        logger.warn("Circuit breaker recorded failure: ${exception.message}")
        
        if (shouldOpenCircuit()) {
            transitionToOpen()
        }
    }
    
    /**
     * Record a successful operation in half-open state
     */
    private fun recordHalfOpenSuccess() {
        halfOpenSuccessCount.incrementAndGet()
        successCount.incrementAndGet()
        
        if (halfOpenSuccessCount.get() >= config.halfOpenSuccessThreshold) {
            logger.info("Circuit breaker recovered, moving to CLOSED state")
            transitionToClosed()
        }
    }
    
    /**
     * Record a failed operation in half-open state
     */
    private fun recordHalfOpenFailure(exception: Exception) {
        logger.warn("Circuit breaker failed during recovery: ${exception.message}")
        lastFailureTime.set(System.currentTimeMillis())
        transitionToOpen()
    }
    
    /**
     * Check if circuit should be opened
     */
    private fun shouldOpenCircuit(): Boolean {
        cleanupOldRecords()
        
        val totalCalls = recentCalls.size
        if (totalCalls < config.minimumCallsThreshold) {
            return false
        }
        
        val failures = recentCalls.count { !it.success }
        val failureRate = failures.toDouble() / totalCalls.toDouble()
        
        return failureRate >= config.failureThreshold
    }
    
    /**
     * Transition to OPEN state
     */
    private fun transitionToOpen() {
        val previousState = state.getAndSet(CircuitBreakerState.OPEN)
        if (previousState != CircuitBreakerState.OPEN) {
            lastStateChangeTime.set(System.currentTimeMillis())
            halfOpenSuccessCount.set(0)
            logger.warn("Circuit breaker OPENED due to failure threshold exceeded")
            
            // Trigger state change event
            config.onStateChange?.invoke(CircuitBreakerState.OPEN, previousState)
        }
    }
    
    /**
     * Transition to HALF_OPEN state
     */
    private fun transitionToHalfOpen() {
        val previousState = state.getAndSet(CircuitBreakerState.HALF_OPEN)
        if (previousState != CircuitBreakerState.HALF_OPEN) {
            lastStateChangeTime.set(System.currentTimeMillis())
            halfOpenSuccessCount.set(0)
            logger.info("Circuit breaker moved to HALF_OPEN state for recovery testing")
            
            config.onStateChange?.invoke(CircuitBreakerState.HALF_OPEN, previousState)
        }
    }
    
    /**
     * Transition to CLOSED state
     */
    private fun transitionToClosed() {
        val previousState = state.getAndSet(CircuitBreakerState.CLOSED)
        if (previousState != CircuitBreakerState.CLOSED) {
            lastStateChangeTime.set(System.currentTimeMillis())
            failureCount.set(0)
            halfOpenSuccessCount.set(0)
            logger.info("Circuit breaker CLOSED - normal operation resumed")
            
            config.onStateChange?.invoke(CircuitBreakerState.CLOSED, previousState)
        }
    }
    
    /**
     * Clean up old call records outside the sliding window
     */
    private fun cleanupOldRecords() {
        val cutoffTime = System.currentTimeMillis() - config.slidingWindowMs
        while (true) {
            val record = recentCalls.peek()
            if (record == null || record.timestamp >= cutoffTime) {
                break
            }
            recentCalls.poll()
        }
    }
    
    /**
     * Get current circuit breaker statistics
     */
    fun getStatistics(): CircuitBreakerStatistics {
        cleanupOldRecords()
        
        val totalCalls = recentCalls.size
        val failures = recentCalls.count { !it.success }
        val successes = totalCalls - failures
        val failureRate = if (totalCalls > 0) (failures.toDouble() / totalCalls.toDouble()) * 100 else 0.0
        
        return CircuitBreakerStatistics(
            state = state.get(),
            totalSuccesses = successCount.get().toLong(),
            totalFailures = failureCount.get().toLong(),
            recentSuccesses = successes,
            recentFailures = failures,
            recentFailureRate = failureRate,
            lastFailureTime = lastFailureTime.get(),
            lastStateChangeTime = lastStateChangeTime.get(),
            halfOpenSuccessCount = halfOpenSuccessCount.get()
        )
    }
    
    /**
     * Get current state
     */
    fun getCurrentState(): CircuitBreakerState = state.get()
    
    /**
     * Check if circuit breaker is allowing calls
     */
    fun isCallAllowed(): Boolean {
        return when (state.get()) {
            CircuitBreakerState.CLOSED -> true
            CircuitBreakerState.HALF_OPEN -> true
            CircuitBreakerState.OPEN -> {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastFailure = currentTime - lastFailureTime.get()
                timeSinceLastFailure >= config.openTimeoutMs
            }
        }
    }
    
    /**
     * Force circuit breaker to specific state (for testing)
     */
    fun forceState(newState: CircuitBreakerState) {
        val oldState = state.getAndSet(newState)
        lastStateChangeTime.set(System.currentTimeMillis())
        logger.info("Circuit breaker state forced from $oldState to $newState")
    }
    
    /**
     * Reset circuit breaker to initial state
     */
    fun reset() {
        state.set(CircuitBreakerState.CLOSED)
        failureCount.set(0)
        successCount.set(0)
        halfOpenSuccessCount.set(0)
        lastFailureTime.set(0)
        lastStateChangeTime.set(System.currentTimeMillis())
        recentCalls.clear()
        logger.info("Circuit breaker reset to initial state")
    }
}

/**
 * Circuit breaker configuration
 */
data class CircuitBreakerConfig(
    val failureThreshold: Double = 0.5, // 50% failure rate
    val minimumCallsThreshold: Int = 10, // Minimum calls before opening
    val openTimeoutMs: Long = 60000L, // 1 minute timeout in open state
    val halfOpenSuccessThreshold: Int = 3, // Successes needed to close from half-open
    val slidingWindowMs: Long = 60000L, // 1 minute sliding window
    val onStateChange: ((newState: CircuitBreakerState, oldState: CircuitBreakerState) -> Unit)? = null
)

/**
 * Circuit breaker states
 */
enum class CircuitBreakerState {
    CLOSED,     // Normal operation
    OPEN,       // Failing fast
    HALF_OPEN   // Testing recovery
}

/**
 * Circuit breaker statistics
 */
data class CircuitBreakerStatistics(
    val state: CircuitBreakerState,
    val totalSuccesses: Long,
    val totalFailures: Long,
    val recentSuccesses: Int,
    val recentFailures: Int,
    val recentFailureRate: Double,
    val lastFailureTime: Long,
    val lastStateChangeTime: Long,
    val halfOpenSuccessCount: Int
)

/**
 * Call record for sliding window
 */
private data class CallRecord(
    val timestamp: Long,
    val success: Boolean
)

/**
 * Exception thrown when circuit breaker is open
 */
class CircuitBreakerOpenException(message: String) : RuntimeException(message)