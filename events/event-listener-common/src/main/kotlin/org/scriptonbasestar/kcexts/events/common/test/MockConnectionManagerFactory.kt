package org.scriptonbasestar.kcexts.events.common.test

import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.events.common.connection.EventConnectionManager

/**
 * Factory for creating mock ConnectionManager instances with common behaviors.
 *
 * Simplifies test setup by providing pre-configured mocks for different scenarios.
 *
 * Usage example:
 * ```kotlin
 * val connectionManager = MockConnectionManagerFactory.createSuccessful()
 * val failingManager = MockConnectionManagerFactory.createFailing("Connection timeout")
 * ```
 */
object MockConnectionManagerFactory {
    /**
     * Create a mock ConnectionManager that always succeeds.
     *
     * @return Mock EventConnectionManager that returns true for send()
     */
    fun createSuccessful(): EventConnectionManager {
        val manager = mock<EventConnectionManager>()
        whenever(manager.send(any(), any())).thenReturn(true)
        whenever(manager.isConnected()).thenReturn(true)
        doNothing().whenever(manager).close()
        return manager
    }

    /**
     * Create a mock ConnectionManager that always fails.
     *
     * @param errorMessage Error message for the exception (default: "Connection failed")
     * @return Mock EventConnectionManager that throws RuntimeException
     */
    fun createFailing(errorMessage: String = "Connection failed"): EventConnectionManager {
        val manager = mock<EventConnectionManager>()
        whenever(manager.send(any(), any())).thenThrow(RuntimeException(errorMessage))
        whenever(manager.isConnected()).thenReturn(false)
        doNothing().whenever(manager).close()
        return manager
    }

    /**
     * Create a mock ConnectionManager that fails then succeeds (flaky behavior).
     *
     * @param failureCount Number of failures before success (default: 2)
     * @return Mock EventConnectionManager with flaky behavior
     */
    fun createFlaky(failureCount: Int = 2): EventConnectionManager {
        val manager = mock<EventConnectionManager>()
        var callCount = 0

        whenever(manager.send(any(), any())).then {
            callCount++
            if (callCount <= failureCount) {
                throw RuntimeException("Temporary failure #$callCount")
            } else {
                true
            }
        }

        whenever(manager.isConnected()).then {
            callCount > failureCount
        }

        doNothing().whenever(manager).close()
        return manager
    }

    /**
     * Create a mock ConnectionManager with custom send() behavior.
     *
     * @param sendBehavior Lambda to define send() behavior
     * @return Mock EventConnectionManager with custom behavior
     */
    fun createCustom(sendBehavior: (String, String) -> Boolean): EventConnectionManager {
        val manager = mock<EventConnectionManager>()
        whenever(manager.send(any(), any())).then { invocation ->
            val destination = invocation.getArgument<String>(0)
            val message = invocation.getArgument<String>(1)
            sendBehavior(destination, message)
        }
        whenever(manager.isConnected()).thenReturn(true)
        doNothing().whenever(manager).close()
        return manager
    }

    /**
     * Create a mock ConnectionManager that captures sent messages.
     *
     * @param capturedMessages Mutable list to store sent messages
     * @return Mock EventConnectionManager that captures messages
     */
    fun createCapturing(capturedMessages: MutableList<Pair<String, String>>): EventConnectionManager {
        val manager = mock<EventConnectionManager>()
        whenever(manager.send(any(), any())).then { invocation ->
            val destination = invocation.getArgument<String>(0)
            val message = invocation.getArgument<String>(1)
            capturedMessages.add(destination to message)
            true
        }
        whenever(manager.isConnected()).thenReturn(true)
        doNothing().whenever(manager).close()
        return manager
    }

    /**
     * Create a mock ConnectionManager that simulates slow operations.
     *
     * @param delayMs Delay in milliseconds for each send() call (default: 100ms)
     * @return Mock EventConnectionManager with simulated latency
     */
    fun createSlow(delayMs: Long = 100): EventConnectionManager {
        val manager = mock<EventConnectionManager>()
        whenever(manager.send(any(), any())).then {
            Thread.sleep(delayMs)
            true
        }
        whenever(manager.isConnected()).thenReturn(true)
        doNothing().whenever(manager).close()
        return manager
    }
}
