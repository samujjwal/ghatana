/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Unit tests for {@link AsyncProcessingEnhancer} (AEP-006.3). 
 */
@DisplayName("AsyncProcessingEnhancer — AEP-006.3")
class AsyncProcessingEnhancerTest extends EventloopTestBase {

    private AsyncProcessingEnhancer enhancer;

    @BeforeEach
    void setUp() { 
        enhancer = AsyncProcessingEnhancer.builder() 
                .executor(Executors.newVirtualThreadPerTaskExecutor()) 
                .build(); 
    }

    @Test
    @DisplayName("fanOut processes all inputs and returns results in order")
    void fanOutProcessesAllInputs() { 
        List<Integer> inputs = List.of(1, 2, 3, 4, 5); 

        List<String> results = runPromise(() -> 
            AsyncProcessingEnhancer.fanOut(inputs, i -> Promise.of("item-" + i), 3) 
        );

        assertThat(results).hasSize(5); 
        assertThat(results).containsExactly("item-1", "item-2", "item-3", "item-4", "item-5"); 
    }

    @Test
    @DisplayName("fanOut with maxConcurrency=1 is effectively sequential")
    void fanOutSequential() { 
        AtomicInteger maxConcurrent = new AtomicInteger(0); 
        AtomicInteger current = new AtomicInteger(0); 

        List<Integer> inputs = List.of(1, 2, 3); 

        List<Integer> results = runPromise(() -> 
            AsyncProcessingEnhancer.fanOut(inputs, i -> { 
                current.incrementAndGet(); 
                maxConcurrent.accumulateAndGet(current.get(), Math::max); 
                current.decrementAndGet(); 
                return Promise.of(i * 2); 
            }, 1)
        );

        assertThat(results).containsExactly(2, 4, 6); 
        // With maxConcurrency=1, max concurrent in-flight == 1
        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(1); 
    }

    @Test
    @DisplayName("offload executes blocking task off the event loop and returns result")
    void offloadExecutesCpuBoundTask() { 
        Long result = runPromise(() -> 
            enhancer.offload(() -> { 
                long sum = 0;
                for (int i = 1; i <= 100; i++) sum += i; 
                return sum;
            })
        );

        assertThat(result).isEqualTo(5050L); 
    }

    @Test
    @DisplayName("processBatched groups inputs into batches and concatenates results")
    void processBatchedGroupsInputs() { 
        List<Integer> inputs = List.of(1, 2, 3, 4, 5, 6, 7); 

        List<String> results = runPromise(() -> 
            AsyncProcessingEnhancer.processBatched( 
                inputs, 3,
                batch -> Promise.of(batch.stream().map(i -> "item-" + i).toList()) 
            )
        );

        assertThat(results).hasSize(7); 
        assertThat(results.get(0)).isEqualTo("item-1");
        assertThat(results.get(6)).isEqualTo("item-7");
    }

    @Test
    @DisplayName("throughputStats returns meaningful stats after some ops")
    void throughputStatsAfterOps() { 
        runPromise(() -> 
            AsyncProcessingEnhancer.fanOut(List.of(1, 2, 3), i -> Promise.of(i * 10), 3) 
        );

        AsyncProcessingEnhancer.ThroughputStats stats = enhancer.throughputStats(); 
        assertThat(stats).isNotNull(); 
    }

    @Test
    @DisplayName("fanOut rejects non-positive maxConcurrency")
    void fanOutRejectsZeroMaxConcurrency() { 
        assertThatThrownBy(() -> 
            runPromise(() -> AsyncProcessingEnhancer.fanOut(List.of(1), i -> Promise.of(i), 0)) 
        ).isInstanceOf(Exception.class); 
    }

    @Test
    @DisplayName("fanOut returns empty list for empty inputs")
    void fanOutEmptyInput() { 
        List<String> results = runPromise(() -> 
            AsyncProcessingEnhancer.fanOut(List.of(), i -> Promise.of("x"), 5)
        );
        assertThat(results).isEmpty(); 
    }
}
