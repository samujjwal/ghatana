package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for batching completion semantics.
 *
 * @doc.type class
 * @doc.purpose Verify BatchingOperator resolves event promises on actual flush completion
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BatchingOperator")
class BatchingOperatorTest extends EventloopTestBase {

    @Test
    @DisplayName("process() keeps promise pending until scheduled flush executes")
    void shouldResolvePromiseOnTimedFlush() { // GH-90000
        UnifiedOperator delegate = mock(UnifiedOperator.class); // GH-90000
        Event event = mock(Event.class); // GH-90000
        when(delegate.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

        BatchingOperator batchingOperator = BatchingOperator.builder() // GH-90000
            .operator(delegate) // GH-90000
            .batchSize(10) // GH-90000
            .maxWaitTime(Duration.ofMillis(50)) // GH-90000
            .build(); // GH-90000

        Promise<OperatorResult> promise = batchingOperator.process(event); // GH-90000
        assertThat(promise.isComplete()).isFalse(); // GH-90000

        OperatorResult result = runPromise(() -> promise); // GH-90000

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(promise.isComplete()).isTrue(); // GH-90000
        verify(delegate).process(event); // GH-90000
    }

    @Test
    @DisplayName("process() resolves all queued promises when batch size threshold is reached")
    void shouldResolveQueuedPromisesOnSizeFlush() { // GH-90000
        UnifiedOperator delegate = mock(UnifiedOperator.class); // GH-90000
        Event first = mock(Event.class); // GH-90000
        Event second = mock(Event.class); // GH-90000
        when(delegate.process(any(Event.class))).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

        BatchingOperator batchingOperator = BatchingOperator.builder() // GH-90000
            .operator(delegate) // GH-90000
            .batchSize(2) // GH-90000
            .maxWaitTime(Duration.ofSeconds(1)) // GH-90000
            .build(); // GH-90000

        Promise<OperatorResult> firstPromise = batchingOperator.process(first); // GH-90000
        assertThat(firstPromise.isComplete()).isFalse(); // GH-90000

        Promise<OperatorResult> secondPromise = batchingOperator.process(second); // GH-90000

        OperatorResult secondResult = runPromise(() -> secondPromise); // GH-90000
        OperatorResult firstResult = runPromise(() -> firstPromise); // GH-90000

        assertThat(firstResult.isSuccess()).isTrue(); // GH-90000
        assertThat(secondResult.isSuccess()).isTrue(); // GH-90000
        assertThat(firstPromise.isComplete()).isTrue(); // GH-90000
        verify(delegate, times(2)).process(any(Event.class)); // GH-90000
    }
}
