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
    void shouldResolvePromiseOnTimedFlush() {
        UnifiedOperator delegate = mock(UnifiedOperator.class);
        Event event = mock(Event.class);
        when(delegate.process(event)).thenReturn(Promise.of(OperatorResult.empty()));

        BatchingOperator batchingOperator = BatchingOperator.builder()
            .operator(delegate)
            .batchSize(10)
            .maxWaitTime(Duration.ofMillis(50))
            .build();

        Promise<OperatorResult> promise = batchingOperator.process(event);
        assertThat(promise.isComplete()).isFalse();

        OperatorResult result = runPromise(() -> promise);

        assertThat(result.isSuccess()).isTrue();
        assertThat(promise.isComplete()).isTrue();
        verify(delegate).process(event);
    }

    @Test
    @DisplayName("process() resolves all queued promises when batch size threshold is reached")
    void shouldResolveQueuedPromisesOnSizeFlush() {
        UnifiedOperator delegate = mock(UnifiedOperator.class);
        Event first = mock(Event.class);
        Event second = mock(Event.class);
        when(delegate.process(any(Event.class))).thenReturn(Promise.of(OperatorResult.empty()));

        BatchingOperator batchingOperator = BatchingOperator.builder()
            .operator(delegate)
            .batchSize(2)
            .maxWaitTime(Duration.ofSeconds(1))
            .build();

        Promise<OperatorResult> firstPromise = batchingOperator.process(first);
        assertThat(firstPromise.isComplete()).isFalse();

        Promise<OperatorResult> secondPromise = batchingOperator.process(second);

        OperatorResult secondResult = runPromise(() -> secondPromise);
        OperatorResult firstResult = runPromise(() -> firstPromise);

        assertThat(firstResult.isSuccess()).isTrue();
        assertThat(secondResult.isSuccess()).isTrue();
        assertThat(firstPromise.isComplete()).isTrue();
        verify(delegate, times(2)).process(any(Event.class));
    }
}
