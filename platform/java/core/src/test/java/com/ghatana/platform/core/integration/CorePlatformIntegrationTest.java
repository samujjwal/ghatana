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
@DisplayName("Core Platform Integration Tests")
class CorePlatformIntegrationTest extends EventloopTestBase {

    private <T> T run(Promise<T> p) { 
        return runPromise(() -> p); 
    }

    private <T> Throwable runExpectFailure(Promise<T> p) { 
        try {
            runPromise(() -> p); 
            throw new AssertionError("Expected promise to fail but it succeeded");
        } catch (RuntimeException e) { 
            return e.getCause() != null ? e.getCause() : e; 
        }
    }

    @Test
    @DisplayName("should handle concurrent promise execution correctly")
    void shouldHandleConcurrentPromiseExecution() { 
        AtomicInteger counter = new AtomicInteger(0); 

        Integer result = run( 
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
        Throwable error = runExpectFailure( 
            Promise.of(1) 
                .then(v -> Promise.ofException(new RuntimeException("Test error")))
        );

        assertThat(error).hasMessageContaining("Test error");
    }

    @Test
    @DisplayName("should handle error callbacks")
    void shouldHandleErrorCallbacks() { 
        Throwable error = runExpectFailure( 
            Promise.<String>ofException(new RuntimeException("Primary failed"))
        );

        assertThat(error).isInstanceOf(RuntimeException.class); 
    }

    @Test
    @DisplayName("should handle nested promise chains")
    void shouldHandleNestedPromiseChains() { 
        Integer result = run( 
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

        String result = run( 
            Promise.of(condition) 
                .then(cond -> cond ? 
                    Promise.of("Condition true") :
                    Promise.of("Condition false"))
        );

        assertThat(result).isEqualTo("Condition true");
    }

    @Test
    @DisplayName("should handle promise composition with combine")
    void shouldHandlePromiseComposition() { 
        Integer result = run( 
            Promise.of(10).combine(Promise.of(5), (b, m) -> b * m) 
        );

        assertThat(result).isEqualTo(50); 
    }

    @Test
    @DisplayName("should handle sequential promise execution")
    void shouldHandleSequentialPromiseExecution() { 
        java.util.List<Integer> result = run( 
            ActiveJPatterns.sequential(Promise.of(1), Promise.of(2), Promise.of(3)) 
        );

        assertThat(result).containsExactly(1, 2, 3); 
    }

    @Test
    @DisplayName("should handle parallel promise execution")
    void shouldHandleParallelPromiseExecution() { 
        java.util.List<Integer> result = run( 
            ActiveJPatterns.parallel(Promise.of(1), Promise.of(2), Promise.of(3)) 
        );

        assertThat(result).containsExactlyInAnyOrder(1, 2, 3); 
    }

    @Test
    @DisplayName("should handle promise retry logic")
    void shouldHandlePromiseRetryLogic() { 
        AtomicInteger attempts = new AtomicInteger(0); 

        Integer result = run( 
            ActiveJPatterns.withRetry( 
                () -> { 
                    int attempt = attempts.incrementAndGet(); 
                    return attempt < 3 ?
                        Promise.ofException(new RuntimeException("Attempt " + attempt)) : 
                        Promise.of(attempt); 
                },
                3,
                java.time.Duration.ofMillis(1), 
                java.time.Duration.ofMillis(10) 
            )
        );

        assertThat(result).isEqualTo(3); 
    }

    @Test
    @DisplayName("should handle promise callbacks on completion")
    void shouldHandlePromiseCallbacksOnCompletion() { 
        String result = run( 
            Promise.of("Success value")
        );

        assertThat(result).isEqualTo("Success value");
    }

    @Test
    @DisplayName("should handle empty sequential promises")
    void shouldHandleEmptySequentialPromises() { 
        java.util.List<Integer> result = run( 
            ActiveJPatterns.sequential() 
        );

        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("should handle empty parallel promises")
    void shouldHandleEmptyParallelPromises() { 
        java.util.List<Integer> result = run( 
            ActiveJPatterns.parallel() 
        );

        assertThat(result).isEmpty(); 
    }
}
