package com.ghatana.phr.kernel.service;

import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import com.ghatana.platform.resilience.RetryPolicy;

import java.time.Duration;

/**
 * @doc.type class
 * @doc.purpose Shared retry and circuit-breaker configuration for emergency review workflow ports
 * @doc.layer product
 * @doc.pattern Utils
 */
final class EmergencyAccessWorkflowResilienceUtils {

    private EmergencyAccessWorkflowResilienceUtils() {
    }

    static CircuitBreaker createCircuitBreaker(String name) {
        return CircuitBreakerProfiles.standardBuilder(name)
            .failureThreshold(3)
            .successThreshold(1)
            .resetTimeout(Duration.ofSeconds(10))
            .build();
    }

    static RetryPolicy createRetryPolicy() {
        return RetryPolicy.builder()
            .maxRetries(2)
            .initialDelay(Duration.ofMillis(25))
            .maxDelay(Duration.ofMillis(100))
            .multiplier(2.0)
            .retryIf(error -> !(error instanceof IllegalArgumentException))
            .build();
    }
}
