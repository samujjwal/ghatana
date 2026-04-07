package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance fraud inference retries transient failures and avoids retrying non-retryable errors
 * @doc.layer product
 * @doc.pattern Test
 */
class RetryingFraudInferenceTransportTest {

    @Test
    void retriesTransientFailuresUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        RetryingFraudInferenceTransport transport = new RetryingFraudInferenceTransport(
            (endpointConfig, request) -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("temporary outage");
                }
                return new FraudModelPrediction(0.81, "HIGH", true, 0.95, 0.93, "remote-anomaly", "REMOTE", "v1", 12L);
            },
            3
        );

        FraudModelPrediction prediction = transport.predict(endpointConfig(), request());

        assertEquals(3, attempts.get());
        assertEquals("REMOTE", prediction.getInferenceSource());
        assertEquals("HIGH", prediction.getRiskLevel());
    }

    @Test
    void doesNotRetryInvalidPayloadErrors() {
        AtomicInteger attempts = new AtomicInteger();
        RetryingFraudInferenceTransport transport = new RetryingFraudInferenceTransport(
            (endpointConfig, request) -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("fraudScore is required");
            },
            3
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> transport.predict(endpointConfig(), request())
        );

        assertEquals("fraudScore is required", exception.getMessage());
        assertEquals(1, attempts.get());
    }

    private static FraudModelEndpointConfig endpointConfig() {
        return new FraudModelEndpointConfig("http://fraud-model.internal/predict", Duration.ofSeconds(1), "v1");
    }

    private static FraudModelInferenceRequest request() {
        return new FraudModelInferenceRequest("fraud-detection-v2", Map.of("amount", 1000.0));
    }
}