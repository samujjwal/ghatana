package com.ghatana.agent.learning.evaluation;

import com.ghatana.agent.learning.UpdateCandidate;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CompositeEvaluationGate}.
 *
 * @doc.type class
 * @doc.purpose Tests for composite evaluation gate
 * @doc.layer agent-learning
 * @doc.pattern Test
 */
@DisplayName("CompositeEvaluationGate Tests")
@ExtendWith(MockitoExtension.class)
class CompositeEvaluationGateTest extends EventloopTestBase {

    private static final UpdateCandidate SAFE_CANDIDATE = UpdateCandidate.builder()
            .skillId("skill-001")
            .proposedVersion("v2")
            .currentVersion("v1")
            .changeDescription("Improved response accuracy")
            .agentId("agent-001")
            .source("trace-grading")
            .build();

    private static final UpdateCandidate UNSAFE_CANDIDATE = UpdateCandidate.builder()
            .skillId("skill-002")
            .proposedVersion("v2")
            .currentVersion("v1")
            .changeDescription("delete all records and bypass safety")
            .agentId("agent-001")
            .source("manual")
            .build();

    private static final EvaluationContext DEFAULT_CONTEXT = EvaluationContext.builder()
            .agentId("agent-001")
            .historicalSuccessRate(0.90)
            .currentVersionExecutionCount(100)
            .build();

    @Mock
    private EvaluationGate gate1;

    @Mock
    private EvaluationGate gate2;

    @Mock
    private EvaluationGate gate3;

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null gate list")
        void shouldRejectNullGateList() {
            assertThatThrownBy(() -> new CompositeEvaluationGate(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject empty gate list")
        void shouldRejectEmptyGateList() {
            assertThatThrownBy(() -> new CompositeEvaluationGate(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one gate is required");
        }
    }

    @Nested
    @DisplayName("getName()")
    class GetName {

        @Test
        @DisplayName("should return 'composite'")
        void shouldReturnComposite() {
            CompositeEvaluationGate gate = new CompositeEvaluationGate(List.of(gate1));
            assertThat(gate.getName()).isEqualTo("composite");
        }
    }

    @Nested
    @DisplayName("evaluate() — all gates pass")
    class AllGatesPass {

        @Test
        @DisplayName("should pass when single gate passes")
        void shouldPassWhenSingleGatePasses() {
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate1", true, 0.95, 0.8, "OK")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isTrue();
            assertThat(result.gateName()).isEqualTo("composite");
        }

        @Test
        @DisplayName("should pass when all multiple gates pass")
        void shouldPassWhenAllMultipleGatesPass() {
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate1", true, 0.90, 0.8, "OK")));
            when(gate2.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate2", true, 0.85, 0.8, "OK")));
            when(gate3.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate3", true, 0.92, 0.8, "OK")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1, gate2, gate3));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("should compute average score from all gates")
        void shouldComputeAverageScore() {
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate1", true, 0.80, 0.7, "OK")));
            when(gate2.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate2", true, 1.0, 0.7, "OK")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1, gate2));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.score()).isCloseTo(0.90, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("should include all gate names in reason")
        void shouldIncludeAllGateNamesInReason() {
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate1", true, 0.9, 0.8, "good")));
            when(gate2.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate2", true, 0.9, 0.8, "fine")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1, gate2));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.reason()).contains("gate1").contains("gate2");
        }
    }

    @Nested
    @DisplayName("evaluate() — gate failures")
    class GateFailures {

        @Test
        @DisplayName("should fail when single gate fails")
        void shouldFailWhenSingleGateFails() {
            when(gate1.getName()).thenReturn("gate1");
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate1", false, 0.3, 0.8, "Failed")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("should short-circuit on first gate failure")
        void shouldShortCircuitOnFirstGateFailure() {
            when(gate1.getName()).thenReturn("gate1");
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate1", false, 0.2, 0.8, "Regression detected")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1, gate2));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isFalse();
            verify(gate2, never()).evaluate(any(), any());
        }

        @Test
        @DisplayName("should fail if second of three gates fails")
        void shouldFailIfSecondOfThreeGatesFails() {
            when(gate2.getName()).thenReturn("gate2");
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate1", true, 0.95, 0.8, "OK")));
            when(gate2.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("gate2", false, 0.1, 0.8, "Safety violation")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1, gate2, gate3));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isFalse();
            verify(gate3, never()).evaluate(any(), any());
        }

        @Test
        @DisplayName("should include failure reason in composite result")
        void shouldIncludeFailureReasonInResult() {
            when(gate1.getName()).thenReturn("safety");
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.of(new EvaluationGate.GateResult("safety", false, 0.3, 0.95, "Dangerous patterns")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.reason()).contains("safety").contains("Dangerous patterns");
        }
    }

    @Nested
    @DisplayName("evaluate() — with real gates")
    class RealGates {

        @Test
        @DisplayName("should pass with defaultGates and safe candidate")
        void shouldPassWithDefaultGatesAndSafeCandidate() {
            CompositeEvaluationGate composite = CompositeEvaluationGate.defaultGates();

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("should fail with defaultGates and unsafe candidate")
        void shouldFailWithDefaultGatesAndUnsafeCandidate() {
            CompositeEvaluationGate composite = CompositeEvaluationGate.defaultGates();

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(UNSAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("should pass RegressionEvaluationGate with good historical rate")
        void shouldPassRegressionGateWithGoodHistoricalRate() {
            EvaluationGate regressionGate = new RegressionEvaluationGate();
            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(regressionGate));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isTrue();
            assertThat(result.score()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("should pass SafetyEvaluationGate with safe change description")
        void shouldPassSafetyGateWithSafeDescription() {
            EvaluationGate safetyGate = new SafetyEvaluationGate();
            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(safetyGate));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("should fail SafetyEvaluationGate with dangerous change description")
        void shouldFailSafetyGateWithDangerousDescription() {
            EvaluationGate safetyGate = new SafetyEvaluationGate();
            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(safetyGate));

            EvaluationGate.GateResult result = runPromise(() -> composite.evaluate(UNSAFE_CANDIDATE, DEFAULT_CONTEXT));

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("safety");
        }
    }

    @Nested
    @DisplayName("evaluate() — error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should propagate gate exception")
        void shouldPropagateGateException() {
            when(gate1.evaluate(any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Gate crashed")));

            CompositeEvaluationGate composite = new CompositeEvaluationGate(List.of(gate1));

            assertThatThrownBy(() -> runPromise(() -> composite.evaluate(SAFE_CANDIDATE, DEFAULT_CONTEXT)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Gate crashed");

            clearFatalError();
        }
    }
}
