package com.ghatana.platform.database.cache.exceptions;

/**
 * Base exception for cache operation failures.
 *
 * <p>This unchecked exception wraps all cache-related errors including:
 * - Redis connectivity failures
 * - JSON serialization/deserialization errors
 * - Invalid operation states
 * - Timeout errors
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * try {
 *     cache.get("key", String.class).getResult();
 * } catch (CacheOperationException e) {
 *     logger.error("Cache operation failed: {}", e.getMessage(), e);
 *     // Fallback to database
 *     return loadFromDatabase();
 * }
 * }</pre>
 *
 * <h2>Error Categories:</h2>
 * - Connection: "Redis operation failed: {operation}"
 * - Serialization: "Failed to serialize value to JSON"
 * - Deserialization: "Failed to deserialize value from JSON"
 *
 * @since 1.0.0
 * @doc.type exception
 * @doc.purpose Cache operation failures (connectivity, serialization, timeouts)
 * @doc.layer core
 * @doc.pattern Exception
 */
public class CacheOperationException extends RuntimeException {
    /**
     * Create exception with message only.
     *
     * @param message error description
     */
    public CacheOperationException(String message) {
        super(message);
    }

    /**
     * Create exception with message and underlying cause.
     *
     * @param message error description
     * @param cause   underlying exception
     */
    public CacheOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
