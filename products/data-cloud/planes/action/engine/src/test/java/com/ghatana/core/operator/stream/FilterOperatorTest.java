package com.ghatana.core.operator.stream;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @doc.type class
 * @doc.purpose Verify FilterOperator predicate evaluation and counter tracking
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FilterOperator")
class FilterOperatorTest extends EventloopTestBase {

    private static final OperatorId ID = OperatorId.of("test", "stream", "filter", "1.0"); 
    private MetricsCollector metrics;
    private Event event;

    @BeforeEach
    void setUp() { 
        metrics = mock(MetricsCollector.class); 
        event = mock(Event.class); 
    }

    private FilterOperator alwaysPass() { 
        return FilterOperator.builder() 
                .id(ID) 
                .name("pass-all")
                .description("passes every event")
                .eventTypes(List.of("*"))
                .predicate(e -> true) 
                .metricsCollector(metrics) 
                .build(); 
    }

    private FilterOperator alwaysBlock() { 
        return FilterOperator.builder() 
                .id(ID) 
                .name("block-all")
                .description("blocks every event")
                .eventTypes(List.of("*"))
                .predicate(e -> false) 
                .metricsCollector(metrics) 
                .build(); 
    }

    @Nested
    @DisplayName("process() — passing predicate")
    class PassTests {

        @Test
        @DisplayName("returns non-empty OperatorResult when predicate returns true")
        void shouldReturnResultWhenPredicatePasses() { 
            FilterOperator op = alwaysPass(); 

            OperatorResult result = runPromise(() -> op.process(event)); 

            assertThat(result.isSuccess()).isTrue(); 
            assertThat(result.getOutputEvents()).isNotEmpty(); 
        }

        @Test
        @DisplayName("increments passThroughCount on each passing event")
        void shouldIncrementPassThroughCount() { 
            FilterOperator op = alwaysPass(); 

            runPromise(() -> op.process(event)); 
            runPromise(() -> op.process(event)); 

            assertThat(op.getPassThroughCount()).isEqualTo(2); 
            assertThat(op.getFilterCount()).isEqualTo(0); 
        }

        @Test
        @DisplayName("returned event is the original event")
        void shouldReturnOriginalEvent() { 
            FilterOperator op = alwaysPass(); 

            OperatorResult result = runPromise(() -> op.process(event)); 

            assertThat(result.getOutputEvents()).hasSize(1).contains(event); 
        }
    }

    @Nested
    @DisplayName("process() — blocking predicate")
    class BlockTests {

        @Test
        @DisplayName("returns empty OperatorResult when predicate returns false")
        void shouldReturnEmptyWhenPredicateBlocks() { 
            FilterOperator op = alwaysBlock(); 

            OperatorResult result = runPromise(() -> op.process(event)); 

            assertThat(result.getOutputEvents()).isEmpty(); 
        }

        @Test
        @DisplayName("increments filterCount on each blocked event")
        void shouldIncrementFilterCount() { 
            FilterOperator op = alwaysBlock(); 

            runPromise(() -> op.process(event)); 
            runPromise(() -> op.process(event)); 
            runPromise(() -> op.process(event)); 

            assertThat(op.getFilterCount()).isEqualTo(3); 
            assertThat(op.getPassThroughCount()).isEqualTo(0); 
        }
    }

    @Nested
    @DisplayName("process() — predicate throws exception")
    class ExceptionTests {

        @Test
        @DisplayName("exception in predicate counts as filtered and returns empty result")
        void shouldCountAsFiltedWhenPredicateThrows() { 
            FilterOperator op = FilterOperator.builder() 
                    .id(ID) 
                    .name("throws")
                    .description("always throws")
                    .eventTypes(List.of("*"))
                    .predicate(e -> { throw new RuntimeException("boom"); })
                    .metricsCollector(metrics) 
                    .build(); 

            OperatorResult result = runPromise(() -> op.process(event)); 

            assertThat(result.getOutputEvents()).isEmpty(); 
            assertThat(op.getFilterCount()).isEqualTo(1); 
            assertThat(op.getPassThroughCount()).isEqualTo(0); 
        }
    }

    @Nested
    @DisplayName("null guard")
    class NullGuards {

        @Test
        @DisplayName("null predicate throws NullPointerException during construction")
        void shouldRejectNullPredicate() { 
            assertThatThrownBy(() -> FilterOperator.builder() 
                    .id(ID).name("x").description("y").eventTypes(List.of())
                    .predicate(null) 
                    .metricsCollector(metrics) 
                    .build()) 
                    .isInstanceOf(NullPointerException.class) 
                    .hasMessageContaining("Predicate");
        }

        @Test
        @DisplayName("null event throws NullPointerException in process()")
        void shouldRejectNullEvent() { 
            FilterOperator op = alwaysPass(); 

            assertThatThrownBy(() -> runPromise(() -> op.process(null))) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes operator name and counters")
        void shouldIncludeNameAndCounters() { 
            FilterOperator op = alwaysPass(); 
            runPromise(() -> op.process(event)); 

            String s = op.toString(); 

            assertThat(s).contains("pass-all");
            assertThat(s).contains("pass=1");
            assertThat(s).contains("filtered=0");
        }
    }
}
