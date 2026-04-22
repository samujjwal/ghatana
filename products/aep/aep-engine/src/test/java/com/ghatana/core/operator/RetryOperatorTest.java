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
@DisplayName("RetryOperator [GH-90000]")
class RetryOperatorTest extends EventloopTestBase {

    @Test
    @DisplayName("process() delays before retrying failed results [GH-90000]")
    void shouldDelayRetriesForFailedResults() { // GH-90000
        UnifiedOperator delegate = mock(UnifiedOperator.class); // GH-90000
        Event event = mock(Event.class); // GH-90000
        AtomicInteger attempts = new AtomicInteger(); // GH-90000

        when(delegate.process(event)).thenAnswer(invocation -> { // GH-90000
            if (attempts.getAndIncrement() == 0) { // GH-90000
                return Promise.of(OperatorResult.failed("transient [GH-90000]"));
            }
            return Promise.of(OperatorResult.empty()); // GH-90000
        });

        RetryOperator retryOperator = RetryOperator.builder() // GH-90000
            .operator(delegate) // GH-90000
            .maxRetries(1) // GH-90000
            .initialDelay(Duration.ofMillis(40)) // GH-90000
            .maxDelay(Duration.ofMillis(40)) // GH-90000
            .jitterFactor(0.0) // GH-90000
            .build(); // GH-90000

        long start = System.nanoTime(); // GH-90000
        OperatorResult result = runPromise(() -> retryOperator.process(event)); // GH-90000
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis(); // GH-90000

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(attempts.get()).isEqualTo(2); // GH-90000
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(35L); // GH-90000
    }

    @Test
    @DisplayName("process() respects retry predicate for thrown exceptions [GH-90000]")
    void shouldRespectRetryPredicate() { // GH-90000
        UnifiedOperator delegate = mock(UnifiedOperator.class); // GH-90000
        Event event = mock(Event.class); // GH-90000
        AtomicInteger attempts = new AtomicInteger(); // GH-90000

        when(delegate.process(event)).thenAnswer(invocation -> { // GH-90000
            attempts.incrementAndGet(); // GH-90000
            return Promise.ofException(new IllegalStateException("fatal [GH-90000]"));
        });

        RetryOperator retryOperator = RetryOperator.builder() // GH-90000
            .operator(delegate) // GH-90000
            .maxRetries(3) // GH-90000
            .retryOn(ex -> false) // GH-90000
            .build(); // GH-90000

        OperatorResult result = runPromise(() -> retryOperator.process(event)); // GH-90000

        assertThat(result.isSuccess()).isFalse(); // GH-90000
        assertThat(result.getErrorMessage()).contains("fatal [GH-90000]");
        assertThat(attempts.get()).isEqualTo(1); // GH-90000
    }
}
