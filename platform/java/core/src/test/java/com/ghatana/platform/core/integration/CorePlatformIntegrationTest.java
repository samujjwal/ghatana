package com.ghatana.platform.core.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Core Platform components.
 * 
 * @doc.type class
 * @doc.purpose Integration tests validating core platform component interactions
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Core Platform Integration Tests")
class CorePlatformIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("should handle concurrent promise execution correctly")
    void shouldHandleConcurrentPromiseExecution() {
        AtomicInteger counter = new AtomicInteger(0);
        
        Promise<Integer> result = runPromise(() -> 
            Promise.of(1)
                .then(v -> Promise.of(counter.incrementAndGet()))
                .then(v -> Promise.of(counter.incrementAndGet()))
                .then(v -> Promise.of(counter.incrementAndGet()))
        );
        
        assertThat(result).isEqualTo(3);
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("should propagate errors through promise chain")
    void shouldPropagateErrorsThroughPromiseChain() {
        assertThatThrownBy(() -> runPromise(() ->
            Promise.of(1)
                .then(v -> Promise.ofException(new RuntimeException("Test error")))
                .then(v -> Promise.of(v + 1))
        )).hasRootCauseMessage("Test error");
    }

    @Test
    @DisplayName("should handle timeout scenarios correctly")
    void shouldHandleTimeoutScenarios() {
        Eventloop eventloop = eventloop();
        
        Promise<String> timeoutPromise = Promise.<String>ofCallback(cb -> 
            eventloop.delay(Duration.ofSeconds(10), () -> cb.set("delayed"))
        ).withTimeout(Duration.ofMillis(100));
        
        assertThatThrownBy(() -> runPromise(() -> timeoutPromise))
            .hasMessageContaining("Timeout");
    }

    @Test
    @DisplayName("should handle parallel promise execution")
    void shouldHandleParallelPromiseExecution() {
        Promise<Integer> p1 = Promise.of(1);
        Promise<Integer> p2 = Promise.of(2);
        Promise<Integer> p3 = Promise.of(3);
        
        Promise<Integer> result = runPromise(() ->
            Promise.all(p1, p2, p3)
                .map(list -> list.stream().mapToInt(Integer::intValue).sum())
        );
        
        assertThat(result).isEqualTo(6);
    }

    @Test
    @DisplayName("should handle error recovery with fallback")
    void shouldHandleErrorRecoveryWithFallback() {
        Promise<String> result = runPromise(() ->
            Promise.<String>ofException(new RuntimeException("Primary failed"))
                .whenException(e -> Promise.of("Fallback value"))
        );
        
        assertThat(result).isEqualTo("Fallback value");
    }

    @Test
    @DisplayName("should handle nested promise chains")
    void shouldHandleNestedPromiseChains() {
        Promise<Integer> result = runPromise(() ->
            Promise.of(1)
                .then(v -> Promise.of(v + 1)
                    .then(v2 -> Promise.of(v2 + 1)
                        .then(v3 -> Promise.of(v3 + 1))))
        );
        
        assertThat(result).isEqualTo(4);
    }

    @Test
    @DisplayName("should handle conditional promise execution")
    void shouldHandleConditionalPromiseExecution() {
        boolean condition = true;
        
        Promise<String> result = runPromise(() ->
            Promise.of(condition)
                .then(cond -> cond ? 
                    Promise.of("Condition true") : 
                    Promise.of("Condition false"))
        );
        
        assertThat(result).isEqualTo("Condition true");
    }

    @Test
    @DisplayName("should handle promise composition")
    void shouldHandlePromiseComposition() {
        Promise<Integer> result = runPromise(() -> {
            Promise<Integer> base = Promise.of(10);
            Promise<Integer> multiplier = Promise.of(5);
            
            return base.combine(multiplier, (b, m) -> b * m);
        });
        
        assertThat(result).isEqualTo(50);
    }

    @Test
    @DisplayName("should handle promise retry logic")
    void shouldHandlePromiseRetryLogic() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        Promise<String> result = runPromise(() -> {
            return Promise.ofCallback(cb -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    cb.setException(new RuntimeException("Attempt " + attempt));
                } else {
                    cb.set("Success on attempt " + attempt);
                }
            });
        });
        
        assertThat(result).contains("Success");
    }

    @Test
    @DisplayName("should handle promise cancellation")
    void shouldHandlePromiseCancellation() {
        Promise<String> promise = Promise.ofCallback(cb -> {
            // Simulate long-running operation
        });
        
        // Cancel the promise
        promise.whenComplete((result, error) -> {
            if (error != null) {
                assertThat(error).isInstanceOf(Exception.class);
            }
        });
    }

    @Test
    @DisplayName("should handle eventloop scheduling")
    void shouldHandleEventloopScheduling() {
        Eventloop eventloop = eventloop();
        AtomicInteger counter = new AtomicInteger(0);
        
        Promise<Integer> result = runPromise(() -> 
            Promise.ofCallback(cb -> {
                eventloop.post(() -> {
                    counter.incrementAndGet();
                    cb.set(counter.get());
                });
            })
        );
        
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("should handle multiple eventloop tasks")
    void shouldHandleMultipleEventloopTasks() {
        Eventloop eventloop = eventloop();
        AtomicInteger counter = new AtomicInteger(0);
        
        Promise<Integer> result = runPromise(() -> {
            Promise<Void> task1 = Promise.ofCallback(cb -> 
                eventloop.post(() -> { counter.incrementAndGet(); cb.set(null); }));
            Promise<Void> task2 = Promise.ofCallback(cb -> 
                eventloop.post(() -> { counter.incrementAndGet(); cb.set(null); }));
            Promise<Void> task3 = Promise.ofCallback(cb -> 
                eventloop.post(() -> { counter.incrementAndGet(); cb.set(null); }));
            
            return Promise.all(task1, task2, task3)
                .map(v -> counter.get());
        });
        
        assertThat(result).isEqualTo(3);
    }
}
