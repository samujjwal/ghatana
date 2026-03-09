package com.ghatana.datacloud.backpressure;

/**
 * Exception thrown when backpressure limits are exceeded.
 *
 * <p><b>Purpose</b><br>
 * Indicates that a request was rejected due to system overload.
 * Contains information for proper error handling and retries.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * try {
 *     result = backpressureManager.executeSync(operation);
 * } catch (BackpressureException e) {
 *     // Handle backpressure - retry with exponential backoff
 *     Thread.sleep(e.getSuggestedRetryMs());
 *     // Or return 503 Service Unavailable
 * }
 * }</pre>
 *
 * @see BackpressureManager
 * @doc.type class
 * @doc.purpose Exception for backpressure violations
 * @doc.layer infrastructure
 * @doc.pattern Exception
 */
public class BackpressureException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    private static final long DEFAULT_RETRY_MS = 1000;
    
    private final long suggestedRetryMs;
    private final BackpressureReason reason;
    
    /**
     * Create exception with message.
     *
     * @param message the error message
     */
    public BackpressureException(String message) {
        super(message);
        this.suggestedRetryMs = DEFAULT_RETRY_MS;
        this.reason = BackpressureReason.SYSTEM_OVERLOADED;
    }
    
    /**
     * Create exception with message and retry time.
     *
     * @param message the error message
     * @param suggestedRetryMs suggested retry time in milliseconds
     */
    public BackpressureException(String message, long suggestedRetryMs) {
        super(message);
        this.suggestedRetryMs = suggestedRetryMs;
        this.reason = BackpressureReason.SYSTEM_OVERLOADED;
    }
    
    /**
     * Create exception with message, retry time, and reason.
     *
     * @param message the error message
     * @param suggestedRetryMs suggested retry time in milliseconds
     * @param reason the backpressure reason
     */
    public BackpressureException(String message, long suggestedRetryMs, BackpressureReason reason) {
        super(message);
        this.suggestedRetryMs = suggestedRetryMs;
        this.reason = reason;
    }
    
    /**
     * Create exception with message and cause.
     *
     * @param message the error message
     * @param cause the cause
     */
    public BackpressureException(String message, Throwable cause) {
        super(message, cause);
        this.suggestedRetryMs = DEFAULT_RETRY_MS;
        this.reason = BackpressureReason.SYSTEM_OVERLOADED;
    }
    
    /**
     * Get suggested retry time in milliseconds.
     *
     * @return suggested retry time
     */
    public long getSuggestedRetryMs() {
        return suggestedRetryMs;
    }
    
    /**
     * Get backpressure reason.
     *
     * @return the reason
     */
    public BackpressureReason getReason() {
        return reason;
    }
    
    /**
     * Get HTTP status code for this exception.
     *
     * @return 503 (Service Unavailable)
     */
    public int getHttpStatusCode() {
        return 503;
    }
    
    /**
     * Reasons for backpressure.
     */
    public enum BackpressureReason {
        /** System is overloaded */
        SYSTEM_OVERLOADED,
        /** Queue capacity exceeded */
        QUEUE_FULL,
        /** Concurrent limit reached */
        CONCURRENT_LIMIT,
        /** Request timed out waiting for slot */
        TIMEOUT,
        /** Request was interrupted */
        INTERRUPTED,
        /** Adaptive limit triggered */
        ADAPTIVE_LIMIT
    }
}
