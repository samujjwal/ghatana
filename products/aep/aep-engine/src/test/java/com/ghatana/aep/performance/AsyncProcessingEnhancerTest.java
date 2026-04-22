/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.performance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AsyncProcessingEnhancer} (AEP-006.3). // GH-90000
 */
@DisplayName("AsyncProcessingEnhancer — AEP-006.3 [GH-90000]")
class AsyncProcessingEnhancerTest extends EventloopTestBase {

    private AsyncProcessingEnhancer enhancer;

    @BeforeEach
    void setUp() { // GH-90000
        enhancer = AsyncProcessingEnhancer.builder() // GH-90000
                .executor(Executors.newVirtualThreadPerTaskExecutor()) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("fanOut processes all inputs and returns results in order [GH-90000]")
    void fanOutProcessesAllInputs() { // GH-90000
        List<Integer> inputs = List.of(1, 2, 3, 4, 5); // GH-90000

        List<String> results = runPromise(() -> // GH-90000
            AsyncProcessingEnhancer.fanOut(inputs, i -> Promise.of("item-" + i), 3) // GH-90000
        );

        assertThat(results).hasSize(5); // GH-90000
        assertThat(results).containsExactly("item-1", "item-2", "item-3", "item-4", "item-5"); // GH-90000
    }

    @Test
    @DisplayName("fanOut with maxConcurrency=1 is effectively sequential [GH-90000]")
    void fanOutSequential() { // GH-90000
        AtomicInteger maxConcurrent = new AtomicInteger(0); // GH-90000
        AtomicInteger current = new AtomicInteger(0); // GH-90000

        List<Integer> inputs = List.of(1, 2, 3); // GH-90000

        List<Integer> results = runPromise(() -> // GH-90000
            AsyncProcessingEnhancer.fanOut(inputs, i -> { // GH-90000
                current.incrementAndGet(); // GH-90000
                maxConcurrent.accumulateAndGet(current.get(), Math::max); // GH-90000
                current.decrementAndGet(); // GH-90000
                return Promise.of(i * 2); // GH-90000
            }, 1)
        );

        assertThat(results).containsExactly(2, 4, 6); // GH-90000
        // With maxConcurrency=1, max concurrent in-flight == 1
        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("offload executes blocking task off the event loop and returns result [GH-90000]")
    void offloadExecutesCpuBoundTask() { // GH-90000
        Long result = runPromise(() -> // GH-90000
            enhancer.offload(() -> { // GH-90000
                long sum = 0;
                for (int i = 1; i <= 100; i++) sum += i; // GH-90000
                return sum;
            })
        );

        assertThat(result).isEqualTo(5050L); // GH-90000
    }

    @Test
    @DisplayName("processBatched groups inputs into batches and concatenates results [GH-90000]")
    void processBatchedGroupsInputs() { // GH-90000
        List<Integer> inputs = List.of(1, 2, 3, 4, 5, 6, 7); // GH-90000

        List<String> results = runPromise(() -> // GH-90000
            AsyncProcessingEnhancer.processBatched( // GH-90000
                inputs, 3,
                batch -> Promise.of(batch.stream().map(i -> "item-" + i).toList()) // GH-90000
            )
        );

        assertThat(results).hasSize(7); // GH-90000
        assertThat(results.get(0)).isEqualTo("item-1 [GH-90000]");
        assertThat(results.get(6)).isEqualTo("item-7 [GH-90000]");
    }

    @Test
    @DisplayName("throughputStats returns meaningful stats after some ops [GH-90000]")
    void throughputStatsAfterOps() { // GH-90000
        runPromise(() -> // GH-90000
            AsyncProcessingEnhancer.fanOut(List.of(1, 2, 3), i -> Promise.of(i * 10), 3) // GH-90000
        );

        AsyncProcessingEnhancer.ThroughputStats stats = enhancer.throughputStats(); // GH-90000
        assertThat(stats).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("fanOut rejects non-positive maxConcurrency [GH-90000]")
    void fanOutRejectsZeroMaxConcurrency() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> AsyncProcessingEnhancer.fanOut(List.of(1), i -> Promise.of(i), 0)) // GH-90000
        ).isInstanceOf(Exception.class); // GH-90000
    }

    @Test
    @DisplayName("fanOut returns empty list for empty inputs [GH-90000]")
    void fanOutEmptyInput() { // GH-90000
        List<String> results = runPromise(() -> // GH-90000
            AsyncProcessingEnhancer.fanOut(List.of(), i -> Promise.of("x [GH-90000]"), 5)
        );
        assertThat(results).isEmpty(); // GH-90000
    }
}
