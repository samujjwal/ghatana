package com.ghatana.finance.ai;

import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance fraud inference transport enforces circuit-breaker and bulkhead protection around remote inference
 * @doc.layer product
 * @doc.pattern ResilienceTest
 */
class ResilientFraudInferenceTransportTest {

    @Test
    void opensCircuitAfterRepeatedTransportFailures() {
        CircuitBreaker breaker = CircuitBreaker.builder("fraud-inference-test")
            .failureThreshold(2)
            .successThreshold(1)
            .resetTimeout(Duration.ofSeconds(5))
            .build();
        AtomicInteger attempts = new AtomicInteger();
        ResilientFraudInferenceTransport transport = new ResilientFraudInferenceTransport(
            (endpointConfig, request) -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("remote outage");
            },
            breaker,
            Bulkhead.of("fraud-inference-test", 2)
        );

        assertThrows(IllegalStateException.class, () -> transport.predict(endpointConfig(), request()));
        assertThrows(IllegalStateException.class, () -> transport.predict(endpointConfig(), request()));

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> transport.predict(endpointConfig(), request())
        );

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertEquals(2, attempts.get());
        assertTrue(hasCause(exception, CircuitBreaker.CircuitBreakerOpenException.class));
    }

    @Test
    void rejectsWhenBulkheadIsSaturated() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ResilientFraudInferenceTransport transport = new ResilientFraudInferenceTransport(
            (endpointConfig, request) -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", exception);
                }
                return new FraudModelPrediction(0.31, "LOW", false, 0.82, 0.85, null, "REMOTE", "v1", 4L);
            },
            CircuitBreaker.builder("fraud-inference-bulkhead").failureThreshold(3).build(),
            Bulkhead.of("fraud-inference-bulkhead", 1)
        );

        Thread blockingCall = new Thread(() -> transport.predict(endpointConfig(), request()));
        blockingCall.start();
        entered.await();

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> transport.predict(endpointConfig(), request())
        );

        release.countDown();
        blockingCall.join();

        assertTrue(hasCause(exception, Bulkhead.BulkheadFullException.class));
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static FraudModelEndpointConfig endpointConfig() {
        return new FraudModelEndpointConfig("http://fraud-model.internal/predict", Duration.ofSeconds(1), "v1");
    }

    private static FraudModelInferenceRequest request() {
        return new FraudModelInferenceRequest("fraud-detection-v2", Map.of("amount", 1000.0));
    }
}