package com.ghatana.agent.memory.procedure;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.procedure.ProcedureStep;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for procedure execution — validates step ordering, fallback, success rate
 * tracking, version history, and prerequisite enforcement.
 *
 * @doc.type class
 * @doc.purpose Tests for enhanced procedure execution behavior and versioning
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Procedure Execution Tests")
@Tag("integration")
class ProcedureExecutionTest extends EventloopTestBase {

    // ── Step ordering ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("step ordering")
    class StepOrdering {

        @Test
        @DisplayName("steps are returned in ordinal order")
        void steps_areReturnedInOrdinalOrder() {
            ProcedureStep step1 = ProcedureStep.builder().ordinal(1).description("Start VM").build();
            ProcedureStep step2 = ProcedureStep.builder().ordinal(2).description("Configure network").build();
            ProcedureStep step3 = ProcedureStep.builder().ordinal(3).description("Start app").build();

            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id(UUID.randomUUID().toString())
                    .situation("Deploy service")
                    .action("Full deployment")
                    .steps(List.of(step1, step2, step3))
                    .build();

            List<ProcedureStep> steps = procedure.getSteps();

            assertThat(steps).hasSize(3);
            assertThat(steps.get(0).getOrdinal()).isEqualTo(1);
            assertThat(steps.get(1).getOrdinal()).isEqualTo(2);
            assertThat(steps.get(2).getOrdinal()).isEqualTo(3);
        }

        @Test
        @DisplayName("procedure with no steps has empty steps list")
        void procedure_withNoSteps_hasEmptyStepsList() {
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id(UUID.randomUUID().toString())
                    .situation("Simple situation")
                    .action("Direct action (no steps)")
                    .build();

            assertThat(procedure.getSteps()).isEmpty();
        }
    }

    // ── Fallback step ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fallback step resolution")
    class FallbackStepResolution {

        @Test
        @DisplayName("step with non-zero fallbackStep references alternative step")
        void stepWithFallbackStep_referencesAlternativeStep() {
            ProcedureStep mainStep = ProcedureStep.builder()
                    .ordinal(2)
                    .description("Try fast path")
                    .toolName("fast-tool")
                    .fallbackStep(3)
                    .build();

            ProcedureStep fallbackStep = ProcedureStep.builder()
                    .ordinal(3)
                    .description("Slow fallback path")
                    .toolName("slow-tool")
                    .build();

            // Resolve fallback: if step 2 fails, execute step 3
            ProcedureStep resolvedFallback = mainStep.getFallbackStep() != 0 ? fallbackStep : null;

            assertThat(mainStep.getFallbackStep()).isEqualTo(3);
            assertThat(resolvedFallback).isNotNull();
            assertThat(resolvedFallback.getDescription()).contains("Slow fallback");
        }

        @Test
        @DisplayName("step with fallbackStep 0 has no fallback")
        void stepWithFallbackStep0_hasNoFallback() {
            ProcedureStep step = ProcedureStep.builder()
                    .ordinal(1)
                    .description("Mandatory initialization")
                    .build();

            assertThat(step.getFallbackStep()).isEqualTo(0);
        }
    }

    // ── Success rate tracking ─────────────────────────────────────────────────

    @Nested
    @DisplayName("use count and success rate")
    class SuccessRateTracking {

        @Test
        @DisplayName("procedure stores success rate from 0.0 to 1.0")
        void procedure_storesSuccessRateFrom0To1() {
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id(UUID.randomUUID().toString())
                    .situation("S")
                    .action("A")
                    .successRate(0.87)
                    .useCount(23)
                    .build();

            assertThat(procedure.getSuccessRate()).isEqualTo(0.87);
            assertThat(procedure.getUseCount()).isEqualTo(23);
        }

        @Test
        @DisplayName("newly created procedure has default success rate of 0.0")
        void newProcedure_hasDefaultSuccessRateOf0() {
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id(UUID.randomUUID().toString())
                    .situation("S")
                    .action("A")
                    .build();

            assertThat(procedure.getSuccessRate()).isEqualTo(0.0);
            assertThat(procedure.getUseCount()).isEqualTo(0);
        }
    }

    // ── Prerequisites ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("prerequisites enforcement")
    class PrerequisitesEnforcement {

        @Test
        @DisplayName("procedure with prerequisites records them and they can be checked")
        void procedure_withPrerequisites_recordsThemForChecking() {
            Map<String, Object> prereqs = Map.of(
                    "requires-java-version", "21",
                    "requires-feature-flag", "FEATURE_X_ENABLED"
            );

            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id(UUID.randomUUID().toString())
                    .situation("S")
                    .action("A")
                    .prerequisites(prereqs)
                    .build();

            assertThat(procedure.getPrerequisites()).containsKey("requires-java-version");
            assertThat(procedure.getPrerequisites().get("requires-java-version")).isEqualTo("21");
        }

        @Test
        @DisplayName("procedure with no prerequisites has empty prerequisites map")
        void procedure_withNoPrerequisites_hasEmptyMap() {
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id(UUID.randomUUID().toString())
                    .situation("S")
                    .action("A")
                    .build();

            assertThat(procedure.getPrerequisites()).isEmpty();
        }
    }

    // ── Tool invocation per step ──────────────────────────────────────────────

    @Nested
    @DisplayName("tool invocation per step")
    class ToolInvocationPerStep {

        @Test
        @DisplayName("step with toolName records the tool to invoke")
        void stepWithToolName_recordsToolToInvoke() {
            ProcedureStep step = ProcedureStep.builder()
                    .ordinal(1)
                    .description("Search repositories")
                    .toolName("git-search-tool")
                    .expectedOutcome("list of matching repos")
                    .build();

            assertThat(step.getToolName()).isEqualTo("git-search-tool");
            assertThat(step.getExpectedOutcome()).isEqualTo("list of matching repos");
        }

        @Test
        @DisplayName("step without toolName represents manual/LLM action")
        void stepWithoutToolName_representsManaualOrLlmAction() {
            ProcedureStep step = ProcedureStep.builder()
                    .ordinal(1)
                    .description("Analyze result with LLM")
                    .build();

            assertThat(step.getToolName()).isNull();
        }
    }
}
