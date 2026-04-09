package com.ghatana.phr.kernel.service;

import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import com.ghatana.platform.resilience.RetryPolicy;
import java.time.Duration;

final class PhrNotificationDispatchResilienceUtils {

    private PhrNotificationDispatchResilienceUtils() {}

    static CircuitBreaker createCircuitBreaker(String name) {
        return CircuitBreakerProfiles.standardBuilder(name)
            .failureThreshold(3)
            .successThreshold(1)
            .resetTimeout(Duration.ofSeconds(30))
            .build();
    }

    static RetryPolicy createRetryPolicy() {
        return RetryPolicy.builder()
            .maxRetries(2)
            .initialDelay(Duration.ofMillis(50))
            .maxDelay(Duration.ofMillis(250))
            .build();
    }
}
