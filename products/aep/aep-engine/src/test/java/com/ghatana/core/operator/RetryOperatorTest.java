package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for retry timing and predicate behavior.
 *
 * @doc.type class
 * @doc.purpose Verify RetryOperator executes real delayed retries without blocking the event loop
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RetryOperator")
class RetryOperatorTest extends EventloopTestBase {

    @Test
    @DisplayName("process() delays before retrying failed results")
    void shouldDelayRetriesForFailedResults() {
        UnifiedOperator delegate = mock(UnifiedOperator.class);
        Event event = mock(Event.class);
        AtomicInteger attempts = new AtomicInteger();

        when(delegate.process(event)).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                return Promise.of(OperatorResult.failed("transient"));
            }
            return Promise.of(OperatorResult.empty());
        });

        RetryOperator retryOperator = RetryOperator.builder()
            .operator(delegate)
            .maxRetries(1)
            .initialDelay(Duration.ofMillis(40))
            .maxDelay(Duration.ofMillis(40))
            .jitterFactor(0.0)
            .build();

        long start = System.nanoTime();
        OperatorResult result = runPromise(() -> retryOperator.process(event));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(result.isSuccess()).isTrue();
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(35L);
    }

    @Test
    @DisplayName("process() respects retry predicate for thrown exceptions")
    void shouldRespectRetryPredicate() {
        UnifiedOperator delegate = mock(UnifiedOperator.class);
        Event event = mock(Event.class);
        AtomicInteger attempts = new AtomicInteger();

        when(delegate.process(event)).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return Promise.ofException(new IllegalStateException("fatal"));
        });

        RetryOperator retryOperator = RetryOperator.builder()
            .operator(delegate)
            .maxRetries(3)
            .retryOn(ex -> false)
            .build();

        OperatorResult result = runPromise(() -> retryOperator.process(event));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("fatal");
        assertThat(attempts.get()).isEqualTo(1);
    }
}
