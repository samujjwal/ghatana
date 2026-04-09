package com.ghatana.finance.ai;

import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;

import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Retries transient outbound finance fraud inference failures before surfacing fallback behavior
 * @doc.layer product
 * @doc.pattern Decorator
 */
final class RetryingFraudInferenceTransport implements DefaultFraudModelInferenceService.FraudInferenceTransport {

    private final DefaultFraudModelInferenceService.FraudInferenceTransport delegate;
    private final int maxAttempts;

    RetryingFraudInferenceTransport(
        DefaultFraudModelInferenceService.FraudInferenceTransport delegate,
        int maxAttempts
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public FraudModelPrediction predict(
        FraudModelEndpointConfig endpointConfig,
        FraudModelInferenceRequest request
    ) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.predict(endpointConfig, request);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt >= maxAttempts || !isRetryable(exception)) {
                    throw exception;
                }
            }
        }

        throw Objects.requireNonNull(lastFailure, "lastFailure cannot be null");
    }

    private static boolean isRetryable(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) {
            return false;
        }

        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof CircuitBreaker.CircuitBreakerOpenException
                || cause instanceof Bulkhead.BulkheadFullException
                || cause instanceof InterruptedException) {
                return false;
            }
            cause = cause.getCause();
        }

        return true;
    }
}
