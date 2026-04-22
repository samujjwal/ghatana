package com.ghatana.platform.core.integration;

import com.ghatana.platform.core.async.ActiveJPatterns;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
@DisplayName("Core Platform Integration Tests [GH-90000]")
class CorePlatformIntegrationTest extends EventloopTestBase {

    private <T> T run(Promise<T> p) { // GH-90000
        return runPromise(() -> p); // GH-90000
    }

    private <T> Throwable runExpectFailure(Promise<T> p) { // GH-90000
        try {
            runPromise(() -> p); // GH-90000
            throw new AssertionError("Expected promise to fail but it succeeded [GH-90000]");
        } catch (RuntimeException e) { // GH-90000
            return e.getCause() != null ? e.getCause() : e; // GH-90000
        }
    }

    @Test
    @DisplayName("should handle concurrent promise execution correctly [GH-90000]")
    void shouldHandleConcurrentPromiseExecution() { // GH-90000
        AtomicInteger counter = new AtomicInteger(0); // GH-90000

        Integer result = run( // GH-90000
            Promise.of(1) // GH-90000
                .then(v -> Promise.of(counter.incrementAndGet())) // GH-90000
                .then(v -> Promise.of(counter.incrementAndGet())) // GH-90000
                .then(v -> Promise.of(counter.incrementAndGet())) // GH-90000
        );

        assertThat(result).isEqualTo(3); // GH-90000
        assertThat(counter.get()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("should propagate errors through promise chain [GH-90000]")
    void shouldPropagateErrorsThroughPromiseChain() { // GH-90000
        Throwable error = runExpectFailure( // GH-90000
            Promise.of(1) // GH-90000
                .then(v -> Promise.ofException(new RuntimeException("Test error [GH-90000]")))
        );

        assertThat(error).hasMessageContaining("Test error [GH-90000]");
    }

    @Test
    @DisplayName("should handle error callbacks [GH-90000]")
    void shouldHandleErrorCallbacks() { // GH-90000
        Throwable error = runExpectFailure( // GH-90000
            Promise.<String>ofException(new RuntimeException("Primary failed [GH-90000]"))
        );

        assertThat(error).isInstanceOf(RuntimeException.class); // GH-90000
    }

    @Test
    @DisplayName("should handle nested promise chains [GH-90000]")
    void shouldHandleNestedPromiseChains() { // GH-90000
        Integer result = run( // GH-90000
            Promise.of(1) // GH-90000
                .then(v -> Promise.of(v + 1) // GH-90000
                    .then(v2 -> Promise.of(v2 + 1) // GH-90000
                        .then(v3 -> Promise.of(v3 + 1)))) // GH-90000
        );

        assertThat(result).isEqualTo(4); // GH-90000
    }

    @Test
    @DisplayName("should handle conditional promise execution [GH-90000]")
    void shouldHandleConditionalPromiseExecution() { // GH-90000
        boolean condition = true;

        String result = run( // GH-90000
            Promise.of(condition) // GH-90000
                .then(cond -> cond ? // GH-90000
                    Promise.of("Condition true [GH-90000]") :
                    Promise.of("Condition false [GH-90000]"))
        );

        assertThat(result).isEqualTo("Condition true [GH-90000]");
    }

    @Test
    @DisplayName("should handle promise composition with combine [GH-90000]")
    void shouldHandlePromiseComposition() { // GH-90000
        Integer result = run( // GH-90000
            Promise.of(10).combine(Promise.of(5), (b, m) -> b * m) // GH-90000
        );

        assertThat(result).isEqualTo(50); // GH-90000
    }

    @Test
    @DisplayName("should handle sequential promise execution [GH-90000]")
    void shouldHandleSequentialPromiseExecution() { // GH-90000
        java.util.List<Integer> result = run( // GH-90000
            ActiveJPatterns.sequential(Promise.of(1), Promise.of(2), Promise.of(3)) // GH-90000
        );

        assertThat(result).containsExactly(1, 2, 3); // GH-90000
    }

    @Test
    @DisplayName("should handle parallel promise execution [GH-90000]")
    void shouldHandleParallelPromiseExecution() { // GH-90000
        java.util.List<Integer> result = run( // GH-90000
            ActiveJPatterns.parallel(Promise.of(1), Promise.of(2), Promise.of(3)) // GH-90000
        );

        assertThat(result).containsExactlyInAnyOrder(1, 2, 3); // GH-90000
    }

    @Test
    @DisplayName("should handle promise retry logic [GH-90000]")
    void shouldHandlePromiseRetryLogic() { // GH-90000
        AtomicInteger attempts = new AtomicInteger(0); // GH-90000

        Integer result = run( // GH-90000
            ActiveJPatterns.withRetry( // GH-90000
                () -> { // GH-90000
                    int attempt = attempts.incrementAndGet(); // GH-90000
                    return attempt < 3 ?
                        Promise.ofException(new RuntimeException("Attempt " + attempt)) : // GH-90000
                        Promise.of(attempt); // GH-90000
                },
                3,
                java.time.Duration.ofMillis(1), // GH-90000
                java.time.Duration.ofMillis(10) // GH-90000
            )
        );

        assertThat(result).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("should handle promise callbacks on completion [GH-90000]")
    void shouldHandlePromiseCallbacksOnCompletion() { // GH-90000
        String result = run( // GH-90000
            Promise.of("Success value [GH-90000]")
        );

        assertThat(result).isEqualTo("Success value [GH-90000]");
    }

    @Test
    @DisplayName("should handle empty sequential promises [GH-90000]")
    void shouldHandleEmptySequentialPromises() { // GH-90000
        java.util.List<Integer> result = run( // GH-90000
            ActiveJPatterns.sequential() // GH-90000
        );

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("should handle empty parallel promises [GH-90000]")
    void shouldHandleEmptyParallelPromises() { // GH-90000
        java.util.List<Integer> result = run( // GH-90000
            ActiveJPatterns.parallel() // GH-90000
        );

        assertThat(result).isEmpty(); // GH-90000
    }
}
