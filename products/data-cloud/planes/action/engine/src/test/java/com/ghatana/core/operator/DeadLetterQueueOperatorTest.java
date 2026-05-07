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
 * @doc.purpose Verify DLQ routing and metrics tracking (AEP-003 remediation) 
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DeadLetterQueueOperator")
class DeadLetterQueueOperatorTest extends EventloopTestBase {

    private static UnifiedOperator delegateThatSucceeds() { 
        UnifiedOperator delegate = mock(UnifiedOperator.class); 
        when(delegate.process(org.mockito.ArgumentMatchers.any(Event.class))) 
            .thenReturn(Promise.of(OperatorResult.empty())); 
        return delegate;
    }

    private static UnifiedOperator delegateThatFails(String message) { 
        UnifiedOperator delegate = mock(UnifiedOperator.class); 
        when(delegate.process(org.mockito.ArgumentMatchers.any(Event.class))) 
            .thenReturn(Promise.ofException(new RuntimeException(message))); 
        return delegate;
    }

    private static DeadLetterQueue buildDlq() { 
        return DeadLetterQueue.builder() 
            .maxSize(1_000) 
            .ttl(Duration.ofHours(1)) 
            .enableReplay(true) 
            .build(); 
    }

    @Nested
    @DisplayName("Success path")
    class SuccessPath {

        @Test
        @DisplayName("passes through result when delegate succeeds")
        void shouldPassThroughOnSuccess() { 
            DeadLetterQueue dlq = buildDlq(); 
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() 
                .operator(delegateThatSucceeds()) 
                .deadLetterQueue(dlq) 
                .build(); 

            OperatorResult result = runPromise(() -> op.process(mock(Event.class))); 

            assertThat(result.isSuccess()).isTrue(); 
            assertThat(dlq.size()).isZero(); 
        }
    }

    @Nested
    @DisplayName("Failure routing")
    class FailurePath {

        @Test
        @DisplayName("stores event in DLQ when delegate throws")
        void shouldStoreToDlqOnDelegateFailure() { 
            DeadLetterQueue dlq = buildDlq(); 
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() 
                .operator(delegateThatFails("downstream-error"))
                .deadLetterQueue(dlq) 
                .build(); 

            OperatorResult result = runPromise(() -> op.process(mock(Event.class))); 

            // Operator should return empty (not propagate the error) 
            assertThat(result.isSuccess()).isTrue(); 
            // But the event is stored in the DLQ
            assertThat(dlq.size()).isEqualTo(1); 
            assertThat(dlq.getAll().get(0).getErrorMessage()).contains("downstream-error");
        }

        @Test
        @DisplayName("multiple failures each create a DLQ entry")
        void shouldStoreMultipleFailures() { 
            DeadLetterQueue dlq = buildDlq(); 
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() 
                .operator(delegateThatFails("retry-exhausted"))
                .deadLetterQueue(dlq) 
                .build(); 

            runPromise(() -> op.process(mock(Event.class))); 
            runPromise(() -> op.process(mock(Event.class))); 
            runPromise(() -> op.process(mock(Event.class))); 

            assertThat(dlq.size()).isEqualTo(3); 
        }
    }

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("increments aep.dlq.events.stored counter on each DLQ store")
        void shouldIncrementMetricOnDlqStore() { 
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); 
            DeadLetterQueue dlq = buildDlq(); 
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() 
                .operator(delegateThatFails("enrichment-timeout"))
                .deadLetterQueue(dlq) 
                .meterRegistry(registry) 
                .build(); 

            runPromise(() -> op.process(mock(Event.class))); 
            runPromise(() -> op.process(mock(Event.class))); 

            double count = registry.counter(DeadLetterQueueOperator.METRIC_DLQ_STORED).count(); 
            assertThat(count).isEqualTo(2.0); 
        }

        @Test
        @DisplayName("aep.dlq.events.pending gauge reflects current DLQ size")
        void shouldExposePendingGauge() { 
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); 
            DeadLetterQueue dlq = buildDlq(); 
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() 
                .operator(delegateThatFails("timeout"))
                .deadLetterQueue(dlq) 
                .meterRegistry(registry) 
                .build(); 

            runPromise(() -> op.process(mock(Event.class))); 
            runPromise(() -> op.process(mock(Event.class))); 
            runPromise(() -> op.process(mock(Event.class))); 

            double pendingGauge = registry.get(DeadLetterQueueOperator.METRIC_DLQ_PENDING) 
                .gauge().value(); 
            assertThat(pendingGauge).isEqualTo(3.0); 
        }

        @Test
        @DisplayName("does not increment metric when delegate succeeds")
        void shouldNotIncrementMetricOnSuccess() { 
            SimpleMeterRegistry registry = new SimpleMeterRegistry(); 
            DeadLetterQueue dlq = buildDlq(); 
            DeadLetterQueueOperator op = DeadLetterQueueOperator.builder() 
                .operator(delegateThatSucceeds()) 
                .deadLetterQueue(dlq) 
                .meterRegistry(registry) 
                .build(); 

            runPromise(() -> op.process(mock(Event.class))); 

            // Counter is registered lazily on first failure; if no failure occurred it
            // should not have been incremented.
            double count = registry.find(DeadLetterQueueOperator.METRIC_DLQ_STORED) 
                .counter() != null 
                ? registry.counter(DeadLetterQueueOperator.METRIC_DLQ_STORED).count() 
                : 0.0;
            assertThat(count).isZero(); 
        }
    }
}
