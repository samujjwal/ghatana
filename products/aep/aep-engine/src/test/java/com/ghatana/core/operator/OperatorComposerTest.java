package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OperatorComposer} — AEP-007.3.
 *
 * <p>Covers all composition patterns: sequential, parallel, conditional, and fan-out.
 *
 * @doc.type class
 * @doc.purpose Unit tests for operator composition helpers
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("OperatorComposer [GH-90000]")
class OperatorComposerTest extends EventloopTestBase {

    private final OperatorComposer composer = new OperatorComposer(); // GH-90000

    // ─── sequential ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sequential() [GH-90000]")
    class Sequential {

        @Test
        @DisplayName("chains two operators in order — second receives first's output [GH-90000]")
        void twoOperators_processedInOrder() { // GH-90000
            UnifiedOperator first = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator second = mock(UnifiedOperator.class); // GH-90000
            Event inputEvent = mock(Event.class); // GH-90000
            Event midEvent = mock(Event.class); // GH-90000

            when(first.process(inputEvent)).thenReturn(Promise.of(OperatorResult.of(midEvent))); // GH-90000
            when(second.process(midEvent)).thenReturn(Promise.of(OperatorResult.of(midEvent))); // GH-90000

            UnifiedOperator chain = composer.sequential(first, second); // GH-90000
            OperatorResult result = runPromise(() -> chain.process(inputEvent)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(first).process(inputEvent); // GH-90000
            verify(second).process(midEvent); // GH-90000
        }

        @Test
        @DisplayName("single operator vararg — behaves as passthrough chain [GH-90000]")
        void singleOperator_returnsDirectResult() { // GH-90000
            UnifiedOperator op = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000
            when(op.process(event)).thenReturn(Promise.of(OperatorResult.of(event))); // GH-90000

            UnifiedOperator chain = composer.sequential(op); // GH-90000
            OperatorResult result = runPromise(() -> chain.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(op).process(event); // GH-90000
        }

        @Test
        @DisplayName("list overload behaves identically to vararg overload [GH-90000]")
        void listOverload_sameAsVararg() { // GH-90000
            UnifiedOperator first = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator second = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000
            Event mid = mock(Event.class); // GH-90000

            when(first.process(event)).thenReturn(Promise.of(OperatorResult.of(mid))); // GH-90000
            when(second.process(mid)).thenReturn(Promise.of(OperatorResult.of(mid))); // GH-90000

            UnifiedOperator chain = composer.sequential(List.of(first, second)); // GH-90000
            OperatorResult result = runPromise(() -> chain.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("null vararg array throws NullPointerException [GH-90000]")
        void nullVararg_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> composer.sequential((UnifiedOperator[]) null)); // GH-90000
        }

        @Test
        @DisplayName("null list throws NullPointerException [GH-90000]")
        void nullList_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> composer.sequential((List<UnifiedOperator>) null)); // GH-90000
        }
    }

    // ─── parallel ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("parallel() [GH-90000]")
    class Parallel {

        @Test
        @DisplayName("all operators receive the same event [GH-90000]")
        void allOperators_receiveIdenticalEvent() { // GH-90000
            UnifiedOperator op1 = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator op2 = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator op3 = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000
            when(op3.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

            UnifiedOperator parallel = composer.parallel(op1, op2, op3); // GH-90000
            OperatorResult result = runPromise(() -> parallel.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(op1).process(event); // GH-90000
            verify(op2).process(event); // GH-90000
            verify(op3).process(event); // GH-90000
        }

        @Test
        @DisplayName("outputs from all operators are collected in result [GH-90000]")
        void outputs_collectedFromAll() { // GH-90000
            UnifiedOperator op1 = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator op2 = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000
            Event out1 = mock(Event.class); // GH-90000
            Event out2 = mock(Event.class); // GH-90000

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.of(out1))); // GH-90000
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.of(out2))); // GH-90000

            UnifiedOperator parallel = composer.parallel(op1, op2); // GH-90000
            OperatorResult result = runPromise(() -> parallel.process(event)); // GH-90000

            assertThat(result.getOutputEvents()).containsExactlyInAnyOrder(out1, out2); // GH-90000
        }

        @Test
        @DisplayName("list overload works identically to vararg [GH-90000]")
        void listOverload_works() { // GH-90000
            UnifiedOperator op1 = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator op2 = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

            UnifiedOperator parallel = composer.parallel(List.of(op1, op2)); // GH-90000
            OperatorResult result = runPromise(() -> parallel.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("null vararg throws NullPointerException [GH-90000]")
        void nullVararg_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> composer.parallel((UnifiedOperator[]) null)); // GH-90000
        }
    }

    // ─── conditional ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("conditional() [GH-90000]")
    class Conditional {

        @Test
        @DisplayName("routes to ifTrue operator when predicate is true [GH-90000]")
        void predicateTrue_routesToIfTrue() { // GH-90000
            UnifiedOperator ifTrue = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator ifFalse = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000

            when(ifTrue.process(event)).thenReturn(Promise.of(OperatorResult.of(event))); // GH-90000

            UnifiedOperator conditional = composer.conditional(e -> true, ifTrue, ifFalse); // GH-90000
            OperatorResult result = runPromise(() -> conditional.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(ifTrue).process(event); // GH-90000
            verify(ifFalse, times(0)).process(any()); // GH-90000
        }

        @Test
        @DisplayName("routes to ifFalse operator when predicate is false [GH-90000]")
        void predicateFalse_routesToIfFalse() { // GH-90000
            UnifiedOperator ifTrue = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator ifFalse = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000

            when(ifFalse.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

            UnifiedOperator conditional = composer.conditional(e -> false, ifTrue, ifFalse); // GH-90000
            OperatorResult result = runPromise(() -> conditional.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(ifFalse).process(event); // GH-90000
            verify(ifTrue, times(0)).process(any()); // GH-90000
        }

        @Test
        @DisplayName("null condition throws NullPointerException [GH-90000]")
        void nullCondition_throwsNPE() { // GH-90000
            UnifiedOperator op = mock(UnifiedOperator.class); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> composer.conditional(null, op, op)); // GH-90000
        }

        @Test
        @DisplayName("null ifTrue throws NullPointerException [GH-90000]")
        void nullIfTrue_throwsNPE() { // GH-90000
            UnifiedOperator op = mock(UnifiedOperator.class); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> composer.conditional(e -> true, null, op)); // GH-90000
        }

        @Test
        @DisplayName("null ifFalse throws NullPointerException [GH-90000]")
        void nullIfFalse_throwsNPE() { // GH-90000
            UnifiedOperator op = mock(UnifiedOperator.class); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> composer.conditional(e -> false, op, null)); // GH-90000
        }
    }

    // ─── fanOut ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fanOut() [GH-90000]")
    class FanOut {

        @Test
        @DisplayName("broadcasts event to all operators [GH-90000]")
        void allOperators_receiveSameEvent() { // GH-90000
            UnifiedOperator op1 = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator op2 = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

            UnifiedOperator fanOut = composer.fanOut(op1, op2); // GH-90000
            OperatorResult result = runPromise(() -> fanOut.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(op1).process(event); // GH-90000
            verify(op2).process(event); // GH-90000
        }

        @Test
        @DisplayName("collected outputs include results from all operators [GH-90000]")
        void collectedOutputs_containAllResults() { // GH-90000
            UnifiedOperator op1 = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator op2 = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000
            Event out1 = mock(Event.class); // GH-90000
            Event out2 = mock(Event.class); // GH-90000

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.of(out1))); // GH-90000
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.of(out2))); // GH-90000

            UnifiedOperator fanOut = composer.fanOut(op1, op2); // GH-90000
            OperatorResult result = runPromise(() -> fanOut.process(event)); // GH-90000

            assertThat(result.getOutputEvents()).containsExactlyInAnyOrder(out1, out2); // GH-90000
        }

        @Test
        @DisplayName("list overload works identically [GH-90000]")
        void listOverload_works() { // GH-90000
            UnifiedOperator op1 = mock(UnifiedOperator.class); // GH-90000
            UnifiedOperator op2 = mock(UnifiedOperator.class); // GH-90000
            Event event = mock(Event.class); // GH-90000

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

            UnifiedOperator fanOut = composer.fanOut(List.of(op1, op2)); // GH-90000
            OperatorResult result = runPromise(() -> fanOut.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("null vararg throws NullPointerException [GH-90000]")
        void nullVararg_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> composer.fanOut((UnifiedOperator[]) null)); // GH-90000
        }

        @Test
        @DisplayName("null list throws NullPointerException [GH-90000]")
        void nullList_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> composer.fanOut((List<UnifiedOperator>) null)); // GH-90000
        }
    }
}
