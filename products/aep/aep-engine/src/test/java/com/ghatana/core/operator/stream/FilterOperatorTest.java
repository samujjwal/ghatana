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
@DisplayName("FilterOperator [GH-90000]")
class FilterOperatorTest extends EventloopTestBase {

    private static final OperatorId ID = OperatorId.of("test", "stream", "filter", "1.0"); // GH-90000
    private MetricsCollector metrics;
    private Event event;

    @BeforeEach
    void setUp() { // GH-90000
        metrics = mock(MetricsCollector.class); // GH-90000
        event = mock(Event.class); // GH-90000
    }

    private FilterOperator alwaysPass() { // GH-90000
        return FilterOperator.builder() // GH-90000
                .id(ID) // GH-90000
                .name("pass-all [GH-90000]")
                .description("passes every event [GH-90000]")
                .eventTypes(List.of("* [GH-90000]"))
                .predicate(e -> true) // GH-90000
                .metricsCollector(metrics) // GH-90000
                .build(); // GH-90000
    }

    private FilterOperator alwaysBlock() { // GH-90000
        return FilterOperator.builder() // GH-90000
                .id(ID) // GH-90000
                .name("block-all [GH-90000]")
                .description("blocks every event [GH-90000]")
                .eventTypes(List.of("* [GH-90000]"))
                .predicate(e -> false) // GH-90000
                .metricsCollector(metrics) // GH-90000
                .build(); // GH-90000
    }

    @Nested
    @DisplayName("process() — passing predicate [GH-90000]")
    class PassTests {

        @Test
        @DisplayName("returns non-empty OperatorResult when predicate returns true [GH-90000]")
        void shouldReturnResultWhenPredicatePasses() { // GH-90000
            FilterOperator op = alwaysPass(); // GH-90000

            OperatorResult result = runPromise(() -> op.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("increments passThroughCount on each passing event [GH-90000]")
        void shouldIncrementPassThroughCount() { // GH-90000
            FilterOperator op = alwaysPass(); // GH-90000

            runPromise(() -> op.process(event)); // GH-90000
            runPromise(() -> op.process(event)); // GH-90000

            assertThat(op.getPassThroughCount()).isEqualTo(2); // GH-90000
            assertThat(op.getFilterCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("returned event is the original event [GH-90000]")
        void shouldReturnOriginalEvent() { // GH-90000
            FilterOperator op = alwaysPass(); // GH-90000

            OperatorResult result = runPromise(() -> op.process(event)); // GH-90000

            assertThat(result.getOutputEvents()).hasSize(1).contains(event); // GH-90000
        }
    }

    @Nested
    @DisplayName("process() — blocking predicate [GH-90000]")
    class BlockTests {

        @Test
        @DisplayName("returns empty OperatorResult when predicate returns false [GH-90000]")
        void shouldReturnEmptyWhenPredicateBlocks() { // GH-90000
            FilterOperator op = alwaysBlock(); // GH-90000

            OperatorResult result = runPromise(() -> op.process(event)); // GH-90000

            assertThat(result.getOutputEvents()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("increments filterCount on each blocked event [GH-90000]")
        void shouldIncrementFilterCount() { // GH-90000
            FilterOperator op = alwaysBlock(); // GH-90000

            runPromise(() -> op.process(event)); // GH-90000
            runPromise(() -> op.process(event)); // GH-90000
            runPromise(() -> op.process(event)); // GH-90000

            assertThat(op.getFilterCount()).isEqualTo(3); // GH-90000
            assertThat(op.getPassThroughCount()).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("process() — predicate throws exception [GH-90000]")
    class ExceptionTests {

        @Test
        @DisplayName("exception in predicate counts as filtered and returns empty result [GH-90000]")
        void shouldCountAsFiltedWhenPredicateThrows() { // GH-90000
            FilterOperator op = FilterOperator.builder() // GH-90000
                    .id(ID) // GH-90000
                    .name("throws [GH-90000]")
                    .description("always throws [GH-90000]")
                    .eventTypes(List.of("* [GH-90000]"))
                    .predicate(e -> { throw new RuntimeException("boom [GH-90000]"); })
                    .metricsCollector(metrics) // GH-90000
                    .build(); // GH-90000

            OperatorResult result = runPromise(() -> op.process(event)); // GH-90000

            assertThat(result.getOutputEvents()).isEmpty(); // GH-90000
            assertThat(op.getFilterCount()).isEqualTo(1); // GH-90000
            assertThat(op.getPassThroughCount()).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("null guard [GH-90000]")
    class NullGuards {

        @Test
        @DisplayName("null predicate throws NullPointerException during construction [GH-90000]")
        void shouldRejectNullPredicate() { // GH-90000
            assertThatThrownBy(() -> FilterOperator.builder() // GH-90000
                    .id(ID).name("x [GH-90000]").description("y [GH-90000]").eventTypes(List.of())
                    .predicate(null) // GH-90000
                    .metricsCollector(metrics) // GH-90000
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("Predicate [GH-90000]");
        }

        @Test
        @DisplayName("null event throws NullPointerException in process() [GH-90000]")
        void shouldRejectNullEvent() { // GH-90000
            FilterOperator op = alwaysPass(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> op.process(null))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("toString() [GH-90000]")
    class ToStringTests {

        @Test
        @DisplayName("includes operator name and counters [GH-90000]")
        void shouldIncludeNameAndCounters() { // GH-90000
            FilterOperator op = alwaysPass(); // GH-90000
            runPromise(() -> op.process(event)); // GH-90000

            String s = op.toString(); // GH-90000

            assertThat(s).contains("pass-all [GH-90000]");
            assertThat(s).contains("pass=1 [GH-90000]");
            assertThat(s).contains("filtered=0 [GH-90000]");
        }
    }
}
