package org.scriptonbasestar.kcexts.events.common.connection

/**
 * Standard interface for managing event message transport connections.
 *
 * All event listener modules should implement this interface for their
 * connection management classes to ensure API consistency across transports.
 *
 * Naming convention: {Transport}ConnectionManager
 * - KafkaConnectionManager
 * - AzureConnectionManager
 * - NatsConnectionManager
 * - RabbitMQConnectionManager
 * - RedisConnectionManager
 * - AwsConnectionManager
 */
interface EventConnectionManager {
    /**
     * Send a message to the specified destination.
     *
     * @param destination The target destination (topic, queue, subject, stream, etc.)
     * @param message The message payload (typically JSON-serialized event)
     * @return true if the message was successfully sent, false otherwise
     * @throws ConnectionException if the connection is closed or unavailable
     */
    fun send(
        destination: String,
        message: String,
    ): Boolean

    /**
     * Check if the connection is currently active and ready to send messages.
     *
     * @return true if connected and ready, false otherwise
     */
    fun isConnected(): Boolean

    /**
     * Close the connection and release all resources.
     *
     * This method should be idempotent and safe to call multiple times.
     * After calling close(), send() should throw ConnectionException.
     */
    fun close()
}

/**
 * Exception thrown when connection operations fail.
 */
class ConnectionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
