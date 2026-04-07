package com.ghatana.finance.ai;

import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;

import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Applies circuit-breaker and bulkhead protection to outbound finance fraud inference calls
 * @doc.layer product
 * @doc.pattern Decorator
 */
final class ResilientFraudInferenceTransport implements DefaultFraudModelInferenceService.FraudInferenceTransport {

    private final DefaultFraudModelInferenceService.FraudInferenceTransport delegate;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    ResilientFraudInferenceTransport(
        DefaultFraudModelInferenceService.FraudInferenceTransport delegate,
        CircuitBreaker circuitBreaker,
        Bulkhead bulkhead
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker cannot be null");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead cannot be null");
    }

    @Override
    public FraudModelPrediction predict(
        FraudModelEndpointConfig endpointConfig,
        FraudModelInferenceRequest request
    ) {
        try {
            return bulkhead.tryExecuteBlocking(() ->
                circuitBreaker.executeSync(() -> delegate.predict(endpointConfig, request))
            );
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Fraud inference resilience execution failed", exception);
        }
    }
}