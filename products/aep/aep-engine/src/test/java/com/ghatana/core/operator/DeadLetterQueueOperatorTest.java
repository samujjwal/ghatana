package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.resilience.DeadLetterQueue;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeadLetterQueueOperator}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Successful event pass-through when delegate succeeds</li>
 *   <li>Failed events are stored in DLQ and empty result returned</li>
 *   <li>Micrometer metrics are incremented on DLQ store</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Verify DLQ routing and metrics tracking (AEP-003 remediation) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DeadLetterQueueOperator")
class DeadLetterQueueOperatorTest extends EventloopTestBase {

    private static UnifiedOperator delegateThatSucceeds() { // GH-90000
        UnifiedOperator delegate = mock(UnifiedOperator.class); // GH-90000
        when(delegate.process(org.mockito.ArgumentMatchers.any(Event.class))) // GH-90000
            .thenReturn(Promise.of(OperatorResult.empty())); // GH-90000
        return delegate;
    }

    private static UnifiedOperator delegateThatFails(String message) { // GH-90000
        UnifiedOperator delegate = mock(UnifiedOperator.class); // GH-90000
        when(delegate.process(org.mockito.ArgumentMatchers.any(Event.class))) // GH-90000
            .thenReturn(Promise.ofException(new RuntimeException(message))); // GH-90000
        return delegate;
    }

    private static DeadLetterQueue buildDlq() { // GH-90000
        return DeadLetterQueue.builder() // GH-90000
            .maxSize(1_000) // GH-90000
            .ttl(Duration.ofHours(1)) // GH-90000
            .enableReplay(true) // GH-90000
            .build(); // GH-90000
    }

    @Nested
    @DisplayName("Success path")
    class SuccessPath {

        @Test
        @DisplayName("passes through result when delegate succeeds")
        void shouldPassThroughOnSuccess() { // GH-90000
            DeadLetterQueue dlq = buildDlq(); // GH-90000
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() // GH-90000
                .operator(delegateThatSucceeds()) // GH-90000
                .deadLetterQueue(dlq) // GH-90000
                .build(); // GH-90000

            OperatorResult result = runPromise(() -> op.process(mock(Event.class))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(dlq.size()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Failure routing")
    class FailurePath {

        @Test
        @DisplayName("stores event in DLQ when delegate throws")
        void shouldStoreToDlqOnDelegateFailure() { // GH-90000
            DeadLetterQueue dlq = buildDlq(); // GH-90000
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() // GH-90000
                .operator(delegateThatFails("downstream-error"))
                .deadLetterQueue(dlq) // GH-90000
                .build(); // GH-90000

            OperatorResult result = runPromise(() -> op.process(mock(Event.class))); // GH-90000

            // Operator should return empty (not propagate the error) // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // But the event is stored in the DLQ
            assertThat(dlq.size()).isEqualTo(1); // GH-90000
            assertThat(dlq.getAll().get(0).getErrorMessage()).contains("downstream-error");
        }

        @Test
        @DisplayName("multiple failures each create a DLQ entry")
        void shouldStoreMultipleFailures() { // GH-90000
            DeadLetterQueue dlq = buildDlq(); // GH-90000
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() // GH-90000
                .operator(delegateThatFails("retry-exhausted"))
                .deadLetterQueue(dlq) // GH-90000
                .build(); // GH-90000

            runPromise(() -> op.process(mock(Event.class))); // GH-90000
            runPromise(() -> op.process(mock(Event.class))); // GH-90000
            runPromise(() -> op.process(mock(Event.class))); // GH-90000

            assertThat(dlq.size()).isEqualTo(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("increments aep.dlq.events.stored counter on each DLQ store")
        void shouldIncrementMetricOnDlqStore() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            DeadLetterQueue dlq = buildDlq(); // GH-90000
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() // GH-90000
                .operator(delegateThatFails("enrichment-timeout"))
                .deadLetterQueue(dlq) // GH-90000
                .meterRegistry(registry) // GH-90000
                .build(); // GH-90000

            runPromise(() -> op.process(mock(Event.class))); // GH-90000
            runPromise(() -> op.process(mock(Event.class))); // GH-90000

            double count = registry.counter(DeadLetterQueueOperator.METRIC_DLQ_STORED).count(); // GH-90000
            assertThat(count).isEqualTo(2.0); // GH-90000
        }

        @Test
        @DisplayName("aep.dlq.events.pending gauge reflects current DLQ size")
        void shouldExposePendingGauge() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            DeadLetterQueue dlq = buildDlq(); // GH-90000
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() // GH-90000
                .operator(delegateThatFails("timeout"))
                .deadLetterQueue(dlq) // GH-90000
                .meterRegistry(registry) // GH-90000
                .build(); // GH-90000

            runPromise(() -> op.process(mock(Event.class))); // GH-90000
            runPromise(() -> op.process(mock(Event.class))); // GH-90000
            runPromise(() -> op.process(mock(Event.class))); // GH-90000

            double pendingGauge = registry.get(DeadLetterQueueOperator.METRIC_DLQ_PENDING) // GH-90000
                .gauge().value(); // GH-90000
            assertThat(pendingGauge).isEqualTo(3.0); // GH-90000
        }

        @Test
        @DisplayName("does not increment metric when delegate succeeds")
        void shouldNotIncrementMetricOnSuccess() { // GH-90000
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
            DeadLetterQueue dlq = buildDlq(); // GH-90000
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() // GH-90000
                .operator(delegateThatSucceeds()) // GH-90000
                .deadLetterQueue(dlq) // GH-90000
                .meterRegistry(registry) // GH-90000
                .build(); // GH-90000

            runPromise(() -> op.process(mock(Event.class))); // GH-90000

            // Counter is registered lazily on first failure; if no failure occurred it
            // should not have been incremented.
            double count = registry.find(DeadLetterQueueOperator.METRIC_DLQ_STORED) // GH-90000
                .counter() != null // GH-90000
                ? registry.counter(DeadLetterQueueOperator.METRIC_DLQ_STORED).count() // GH-90000
                : 0.0;
            assertThat(count).isZero(); // GH-90000
        }
    }
}
