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
@DisplayName("OperatorComposer")
class OperatorComposerTest extends EventloopTestBase {

    private final OperatorComposer composer = new OperatorComposer();

    // ─── sequential ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sequential()")
    class Sequential {

        @Test
        @DisplayName("chains two operators in order — second receives first's output")
        void twoOperators_processedInOrder() {
            UnifiedOperator first = mock(UnifiedOperator.class);
            UnifiedOperator second = mock(UnifiedOperator.class);
            Event inputEvent = mock(Event.class);
            Event midEvent = mock(Event.class);

            when(first.process(inputEvent)).thenReturn(Promise.of(OperatorResult.of(midEvent)));
            when(second.process(midEvent)).thenReturn(Promise.of(OperatorResult.of(midEvent)));

            UnifiedOperator chain = composer.sequential(first, second);
            OperatorResult result = runPromise(() -> chain.process(inputEvent));

            assertThat(result.isSuccess()).isTrue();
            verify(first).process(inputEvent);
            verify(second).process(midEvent);
        }

        @Test
        @DisplayName("single operator vararg — behaves as passthrough chain")
        void singleOperator_returnsDirectResult() {
            UnifiedOperator op = mock(UnifiedOperator.class);
            Event event = mock(Event.class);
            when(op.process(event)).thenReturn(Promise.of(OperatorResult.of(event)));

            UnifiedOperator chain = composer.sequential(op);
            OperatorResult result = runPromise(() -> chain.process(event));

            assertThat(result.isSuccess()).isTrue();
            verify(op).process(event);
        }

        @Test
        @DisplayName("list overload behaves identically to vararg overload")
        void listOverload_sameAsVararg() {
            UnifiedOperator first = mock(UnifiedOperator.class);
            UnifiedOperator second = mock(UnifiedOperator.class);
            Event event = mock(Event.class);
            Event mid = mock(Event.class);

            when(first.process(event)).thenReturn(Promise.of(OperatorResult.of(mid)));
            when(second.process(mid)).thenReturn(Promise.of(OperatorResult.of(mid)));

            UnifiedOperator chain = composer.sequential(List.of(first, second));
            OperatorResult result = runPromise(() -> chain.process(event));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("null vararg array throws NullPointerException")
        void nullVararg_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> composer.sequential((UnifiedOperator[]) null));
        }

        @Test
        @DisplayName("null list throws NullPointerException")
        void nullList_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> composer.sequential((List<UnifiedOperator>) null));
        }
    }

    // ─── parallel ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("parallel()")
    class Parallel {

        @Test
        @DisplayName("all operators receive the same event")
        void allOperators_receiveIdenticalEvent() {
            UnifiedOperator op1 = mock(UnifiedOperator.class);
            UnifiedOperator op2 = mock(UnifiedOperator.class);
            UnifiedOperator op3 = mock(UnifiedOperator.class);
            Event event = mock(Event.class);

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.empty()));
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.empty()));
            when(op3.process(event)).thenReturn(Promise.of(OperatorResult.empty()));

            UnifiedOperator parallel = composer.parallel(op1, op2, op3);
            OperatorResult result = runPromise(() -> parallel.process(event));

            assertThat(result.isSuccess()).isTrue();
            verify(op1).process(event);
            verify(op2).process(event);
            verify(op3).process(event);
        }

        @Test
        @DisplayName("outputs from all operators are collected in result")
        void outputs_collectedFromAll() {
            UnifiedOperator op1 = mock(UnifiedOperator.class);
            UnifiedOperator op2 = mock(UnifiedOperator.class);
            Event event = mock(Event.class);
            Event out1 = mock(Event.class);
            Event out2 = mock(Event.class);

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.of(out1)));
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.of(out2)));

            UnifiedOperator parallel = composer.parallel(op1, op2);
            OperatorResult result = runPromise(() -> parallel.process(event));

            assertThat(result.getOutputEvents()).containsExactlyInAnyOrder(out1, out2);
        }

        @Test
        @DisplayName("list overload works identically to vararg")
        void listOverload_works() {
            UnifiedOperator op1 = mock(UnifiedOperator.class);
            UnifiedOperator op2 = mock(UnifiedOperator.class);
            Event event = mock(Event.class);

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.empty()));
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.empty()));

            UnifiedOperator parallel = composer.parallel(List.of(op1, op2));
            OperatorResult result = runPromise(() -> parallel.process(event));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("null vararg throws NullPointerException")
        void nullVararg_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> composer.parallel((UnifiedOperator[]) null));
        }
    }

    // ─── conditional ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("conditional()")
    class Conditional {

        @Test
        @DisplayName("routes to ifTrue operator when predicate is true")
        void predicateTrue_routesToIfTrue() {
            UnifiedOperator ifTrue = mock(UnifiedOperator.class);
            UnifiedOperator ifFalse = mock(UnifiedOperator.class);
            Event event = mock(Event.class);

            when(ifTrue.process(event)).thenReturn(Promise.of(OperatorResult.of(event)));

            UnifiedOperator conditional = composer.conditional(e -> true, ifTrue, ifFalse);
            OperatorResult result = runPromise(() -> conditional.process(event));

            assertThat(result.isSuccess()).isTrue();
            verify(ifTrue).process(event);
            verify(ifFalse, times(0)).process(any());
        }

        @Test
        @DisplayName("routes to ifFalse operator when predicate is false")
        void predicateFalse_routesToIfFalse() {
            UnifiedOperator ifTrue = mock(UnifiedOperator.class);
            UnifiedOperator ifFalse = mock(UnifiedOperator.class);
            Event event = mock(Event.class);

            when(ifFalse.process(event)).thenReturn(Promise.of(OperatorResult.empty()));

            UnifiedOperator conditional = composer.conditional(e -> false, ifTrue, ifFalse);
            OperatorResult result = runPromise(() -> conditional.process(event));

            assertThat(result.isSuccess()).isTrue();
            verify(ifFalse).process(event);
            verify(ifTrue, times(0)).process(any());
        }

        @Test
        @DisplayName("null condition throws NullPointerException")
        void nullCondition_throwsNPE() {
            UnifiedOperator op = mock(UnifiedOperator.class);
            assertThatNullPointerException()
                    .isThrownBy(() -> composer.conditional(null, op, op));
        }

        @Test
        @DisplayName("null ifTrue throws NullPointerException")
        void nullIfTrue_throwsNPE() {
            UnifiedOperator op = mock(UnifiedOperator.class);
            assertThatNullPointerException()
                    .isThrownBy(() -> composer.conditional(e -> true, null, op));
        }

        @Test
        @DisplayName("null ifFalse throws NullPointerException")
        void nullIfFalse_throwsNPE() {
            UnifiedOperator op = mock(UnifiedOperator.class);
            assertThatNullPointerException()
                    .isThrownBy(() -> composer.conditional(e -> false, op, null));
        }
    }

    // ─── fanOut ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fanOut()")
    class FanOut {

        @Test
        @DisplayName("broadcasts event to all operators")
        void allOperators_receiveSameEvent() {
            UnifiedOperator op1 = mock(UnifiedOperator.class);
            UnifiedOperator op2 = mock(UnifiedOperator.class);
            Event event = mock(Event.class);

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.empty()));
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.empty()));

            UnifiedOperator fanOut = composer.fanOut(op1, op2);
            OperatorResult result = runPromise(() -> fanOut.process(event));

            assertThat(result.isSuccess()).isTrue();
            verify(op1).process(event);
            verify(op2).process(event);
        }

        @Test
        @DisplayName("collected outputs include results from all operators")
        void collectedOutputs_containAllResults() {
            UnifiedOperator op1 = mock(UnifiedOperator.class);
            UnifiedOperator op2 = mock(UnifiedOperator.class);
            Event event = mock(Event.class);
            Event out1 = mock(Event.class);
            Event out2 = mock(Event.class);

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.of(out1)));
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.of(out2)));

            UnifiedOperator fanOut = composer.fanOut(op1, op2);
            OperatorResult result = runPromise(() -> fanOut.process(event));

            assertThat(result.getOutputEvents()).containsExactlyInAnyOrder(out1, out2);
        }

        @Test
        @DisplayName("list overload works identically")
        void listOverload_works() {
            UnifiedOperator op1 = mock(UnifiedOperator.class);
            UnifiedOperator op2 = mock(UnifiedOperator.class);
            Event event = mock(Event.class);

            when(op1.process(event)).thenReturn(Promise.of(OperatorResult.empty()));
            when(op2.process(event)).thenReturn(Promise.of(OperatorResult.empty()));

            UnifiedOperator fanOut = composer.fanOut(List.of(op1, op2));
            OperatorResult result = runPromise(() -> fanOut.process(event));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("null vararg throws NullPointerException")
        void nullVararg_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> composer.fanOut((UnifiedOperator[]) null));
        }

        @Test
        @DisplayName("null list throws NullPointerException")
        void nullList_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> composer.fanOut((List<UnifiedOperator>) null));
        }
    }
}

