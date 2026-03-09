package com.ghatana.core.activej.promise;

import com.ghatana.core.activej.testing.EventloopTestExtension;
import com.ghatana.core.activej.testing.EventloopTestRunner;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for PromiseUtils.
 */
@ExtendWith(EventloopTestExtension.class)
class PromiseUtilsTest {
    
    @Test
    void testWithTimeout_completesBeforeTimeout(EventloopTestRunner runner) {
        Promise<String> promise = Promise.of("success");
        
        String result = runner.runPromise(() -> 
            PromiseUtils.withTimeout(promise, Duration.ofSeconds(1))
        );
        
        assertThat(result).isEqualTo("success");
    }
    
    @Test
    void testWithTimeout_failsOnTimeout(EventloopTestRunner runner) {
        Promise<String> slowPromise = Promise.ofCallback(cb -> {
            // Never completes
        });
        
        assertThatThrownBy(() -> runner.runPromise(() -> 
            PromiseUtils.withTimeout(slowPromise, Duration.ofMillis(100))
        )).hasCauseInstanceOf(TimeoutException.class);
    }
    
    @Test
    void testWithRetry_succeedsOnFirstAttempt(EventloopTestRunner runner) {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = runner.runPromise(() -> 
            PromiseUtils.withRetry(
                () -> {
                    attempts.incrementAndGet();
                    return Promise.of("success");
                },
                3,
                Duration.ofMillis(10)
            )
        );
        
        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(1);
    }
    
    @Test
    void testWithRetry_retriesOnFailure(EventloopTestRunner runner) {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = runner.runPromise(() -> 
            PromiseUtils.withRetry(
                () -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 3) {
                        return Promise.ofException(new RuntimeException("Attempt " + attempt));
                    }
                    return Promise.of("success");
                },
                3,
                Duration.ofMillis(10)
            )
        );
        
        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(3);
    }
    
    @Test
    void testWithRetry_failsAfterMaxAttempts(EventloopTestRunner runner) {
        AtomicInteger attempts = new AtomicInteger(0);
        
        assertThatThrownBy(() -> runner.runPromise(() -> 
            PromiseUtils.withRetry(
                () -> {
                    attempts.incrementAndGet();
                    return Promise.ofException(new RuntimeException("Always fails"));
                },
                3,
                Duration.ofMillis(10)
            )
        )).hasMessageContaining("Always fails");
        
        assertThat(attempts.get()).isEqualTo(3);
    }
    
    @Test
    void testWithRetry_requiresPositiveAttempts(EventloopTestRunner runner) {
        assertThatThrownBy(() -> runner.runPromise(() -> 
            PromiseUtils.withRetry(
                () -> Promise.of("test"),
                0,
                Duration.ofMillis(10)
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void testToCompletableFuture_success(EventloopTestRunner runner) {
        Promise<String> promise = Promise.of("test");
        
        CompletableFuture<String> future = runner.runBlocking(() -> 
            PromiseUtils.toCompletableFuture(promise)
        );
        
        assertThat(future).isCompletedWithValue("test");
    }
    
    @Test
    void testToCompletableFuture_failure(EventloopTestRunner runner) {
        Promise<String> promise = Promise.ofException(new RuntimeException("error"));
        
        CompletableFuture<String> future = runner.runBlocking(() -> 
            PromiseUtils.toCompletableFuture(promise)
        );
        
        assertThat(future).isCompletedExceptionally();
    }
    
    @Test
    void testFromCompletableFuture_success(EventloopTestRunner runner) {
        CompletableFuture<String> future = CompletableFuture.completedFuture("test");
        
        String result = runner.runPromise(() -> 
            PromiseUtils.fromCompletableFuture(future)
        );
        
        assertThat(result).isEqualTo("test");
    }
    
    @Test
    void testFromCompletableFuture_failure(EventloopTestRunner runner) {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("error"));
        
        assertThatThrownBy(() -> runner.runPromise(() -> 
            PromiseUtils.fromCompletableFuture(future)
        )).hasMessageContaining("error");
    }
    
    @Test
    void testAll_combinesSuccessfully(EventloopTestRunner runner) {
        List<Promise<Integer>> promises = List.of(
            Promise.of(1),
            Promise.of(2),
            Promise.of(3)
        );
        
        List<Integer> results = runner.runPromise(() -> 
            PromiseUtils.all(promises)
        );
        
        assertThat(results).containsExactly(1, 2, 3);
    }
    
    @Test
    void testAll_failsIfAnyFails(EventloopTestRunner runner) {
        List<Promise<Integer>> promises = List.of(
            Promise.of(1),
            Promise.ofException(new RuntimeException("error")),
            Promise.of(3)
        );
        
        assertThatThrownBy(() -> runner.runPromise(() -> 
            PromiseUtils.all(promises)
        )).hasMessageContaining("error");
    }
    
    @Test
    void testAny_returnsFirstSuccess(EventloopTestRunner runner) {
        List<Promise<String>> promises = List.of(
            Promise.ofException(new RuntimeException("error1")),
            Promise.of("success"),
            Promise.ofException(new RuntimeException("error2"))
        );
        
        String result = runner.runPromise(() -> 
            PromiseUtils.any(promises)
        );
        
        assertThat(result).isEqualTo("success");
    }
    
    @Test
    void testMap_transformsValue(EventloopTestRunner runner) {
        Promise<Integer> promise = Promise.of(10);
        
        String result = runner.runPromise(() -> 
            PromiseUtils.map(promise, n -> "Number: " + n)
        );
        
        assertThat(result).isEqualTo("Number: 10");
    }
    
    @Test
    void testFlatMap_chainsPromises(EventloopTestRunner runner) {
        Promise<Integer> promise = Promise.of(10);
        
        Integer result = runner.runPromise(() -> 
            PromiseUtils.flatMap(promise, n -> Promise.of(n * 2))
        );
        
        assertThat(result).isEqualTo(20);
    }
    
    @Test
    void testWithFallback_usesFallbackOnError(EventloopTestRunner runner) {
        Promise<String> failingPromise = Promise.ofException(new RuntimeException("error"));
        
        String result = runner.runPromise(() -> 
            PromiseUtils.withFallback(failingPromise, "fallback")
        );
        
        assertThat(result).isEqualTo("fallback");
    }
    
    @Test
    void testWithFallback_usesOriginalOnSuccess(EventloopTestRunner runner) {
        Promise<String> promise = Promise.of("original");
        
        String result = runner.runPromise(() -> 
            PromiseUtils.withFallback(promise, "fallback")
        );
        
        assertThat(result).isEqualTo("original");
    }
    
    @Test
    void testWithFallbackPromise_usesFallbackOnError(EventloopTestRunner runner) {
        Promise<String> failingPromise = Promise.ofException(new RuntimeException("error"));
        
        String result = runner.runPromise(() -> 
            PromiseUtils.withFallbackPromise(
                failingPromise, 
                () -> Promise.of("fallback")
            )
        );
        
        assertThat(result).isEqualTo("fallback");
    }
    
    @Test
    void testSequence_executesInOrder(EventloopTestRunner runner) {
        AtomicInteger counter = new AtomicInteger(0);
        
        List<Integer> results = runner.runPromise(() -> 
            PromiseUtils.sequence(List.of(
                () -> Promise.of(counter.incrementAndGet()),
                () -> Promise.of(counter.incrementAndGet()),
                () -> Promise.of(counter.incrementAndGet())
            ))
        );
        
        assertThat(results).containsExactly(1, 2, 3);
    }
    
    @Test
    void testDelay_delaysExecution(EventloopTestRunner runner) {
        long start = System.currentTimeMillis();
        
        String result = runner.runPromise(() -> 
            PromiseUtils.delay(
                Duration.ofMillis(100),
                () -> Promise.of("delayed")
            )
        );
        
        long elapsed = System.currentTimeMillis() - start;
        
        assertThat(result).isEqualTo("delayed");
        assertThat(elapsed).isGreaterThanOrEqualTo(100);
    }
    
    @Test
    void testOf_createsCompletedPromise(EventloopTestRunner runner) {
        String result = runner.runPromise(() -> 
            PromiseUtils.of("test")
        );
        
        assertThat(result).isEqualTo("test");
    }
    
    @Test
    void testOfException_createsFailedPromise(EventloopTestRunner runner) {
        assertThatThrownBy(() -> runner.runPromise(() -> 
            PromiseUtils.ofException(new RuntimeException("error"))
        )).hasMessageContaining("error");
    }
    
    @Test
    void testDoFinally_executesOnSuccess(EventloopTestRunner runner) {
        AtomicInteger finallyCount = new AtomicInteger(0);
        
        String result = runner.runPromise(() -> 
            PromiseUtils.doFinally(
                Promise.of("success"),
                finallyCount::incrementAndGet
            )
        );
        
        assertThat(result).isEqualTo("success");
        assertThat(finallyCount.get()).isEqualTo(1);
    }
    
    @Test
    void testDoFinally_executesOnFailure(EventloopTestRunner runner) {
        AtomicInteger finallyCount = new AtomicInteger(0);
        
        assertThatThrownBy(() -> runner.runPromise(() -> 
            PromiseUtils.doFinally(
                Promise.ofException(new RuntimeException("error")),
                finallyCount::incrementAndGet
            )
        )).hasMessageContaining("error");
        
        assertThat(finallyCount.get()).isEqualTo(1);
    }
}
