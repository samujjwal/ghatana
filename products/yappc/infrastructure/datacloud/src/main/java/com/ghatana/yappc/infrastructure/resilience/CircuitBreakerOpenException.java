package com.ghatana.yappc.infrastructure.resilience;

import java.time.Duration;

/**
 * Exception thrown when circuit breaker is in OPEN state.
 *
 * <p><b>Purpose</b><br>
 * Signals that a service call was rejected because the circuit breaker
 * detected excessive failures and is temporarily preventing requests.
 *
 * @doc.type class
 * @doc.purpose Circuit breaker rejection exception
 * @doc.layer infrastructure
 * @doc.pattern Exception
 */
public class CircuitBreakerOpenException extends RuntimeException {

    private final Duration remainingTimeout;

    public CircuitBreakerOpenException(String message, Duration remainingTimeout) {
        super(message + " (retry after: " + remainingTimeout.getSeconds() + "s)");
        this.remainingTimeout = remainingTimeout;
    }

    public CircuitBreakerOpenException(String message, Throwable cause) {
        super(message, cause);
        this.remainingTimeout = Duration.ZERO;
    }

    /**
     * Returns the remaining time before circuit may allow requests again.
     *
     * @return remaining timeout duration
     */
    public Duration getRemainingTimeout() {
        return remainingTimeout;
    }
}
